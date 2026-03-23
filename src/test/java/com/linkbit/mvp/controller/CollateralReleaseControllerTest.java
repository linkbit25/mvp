package com.linkbit.mvp.controller;

import com.linkbit.mvp.domain.*;
import com.linkbit.mvp.repository.*;
import com.linkbit.mvp.service.JwtService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class CollateralReleaseControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private LoanRepository loanRepository;

    @Autowired
    private LoanOfferRepository loanOfferRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private EscrowAccountRepository escrowAccountRepository;

    @Autowired
    private LoanLedgerRepository loanLedgerRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtService jwtService;

    private Loan loan;
    private User borrower;
    private User lender;
    private User admin;
    private String borrowerToken;
    private String adminToken;

    @BeforeEach
    void setUp() {
        loanLedgerRepository.deleteAll();
        escrowAccountRepository.deleteAll();
        loanRepository.deleteAll();
        loanOfferRepository.deleteAll();
        userRepository.deleteAll();

        borrower = User.builder()
                .email("borrower@example.com")
                .password(passwordEncoder.encode("password"))
                .kycStatus(KycStatus.VERIFIED)
                .pseudonym("BorrowerP")
                .build();
        userRepository.save(borrower);
        borrowerToken = "Bearer " + jwtService.generateToken(borrower);

        lender = User.builder()
                .email("lender@example.com")
                .password(passwordEncoder.encode("password"))
                .kycStatus(KycStatus.VERIFIED)
                .pseudonym("LenderP")
                .build();
        userRepository.save(lender);

        admin = User.builder()
                .email("admin@example.com")
                .password(passwordEncoder.encode("password"))
                .kycStatus(KycStatus.VERIFIED)
                .role(ActorType.ADMIN)
                .pseudonym("AdminP")
                .build();
        userRepository.save(admin);
        adminToken = "Bearer " + jwtService.generateToken(admin);

        LoanOffer offer = LoanOffer.builder()
                .lender(lender)
                .loanAmountInr(new BigDecimal("50000"))
                .interestRate(new BigDecimal("12.0"))
                .expectedLtvPercent(60)
                .tenureDays(90)
                .status(LoanOfferStatus.OPEN)
                .build();
        loanOfferRepository.save(offer);

        loan = Loan.builder()
                .offer(offer)
                .borrower(borrower)
                .lender(lender)
                .principalAmount(new BigDecimal("20000"))
                .interestRate(new BigDecimal("12.0"))
                .tenureDays(90)
                .status(LoanStatus.REPAID)
                .repaymentType(RepaymentType.BULLET)
                .emiCount(1)
                .emiAmount(new BigDecimal("22400"))
                .totalRepaymentAmount(new BigDecimal("22400"))
                .expectedLtvPercent(60)
                .marginCallLtvPercent(85)
                .liquidationLtvPercent(90)
                .collateralBtcAmount(new BigDecimal("0.025"))
                .collateralValueInr(new BigDecimal("110000"))
                .currentLtvPercent(new BigDecimal("18.18"))
                .lastPriceUpdate(LocalDateTime.now())
                .principalOutstanding(BigDecimal.ZERO)
                .interestOutstanding(BigDecimal.ZERO)
                .totalOutstanding(BigDecimal.ZERO)
                .build();
        loanRepository.save(loan);

        escrowAccountRepository.insertEscrowAccount(loan.getId(), "bc1qmockaddress", 2500000L);
    }

    @Test
    void shouldReleaseCollateral() throws Exception {
        mockMvc.perform(post("/admin/loans/" + loan.getId() + "/release-collateral")
                        .header("Authorization", adminToken)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());

        Loan updatedLoan = loanRepository.findById(loan.getId()).orElseThrow();
        assertThat(updatedLoan.getStatus()).isEqualTo(LoanStatus.CLOSED);
        assertThat(updatedLoan.getCollateralReleasedAt()).isNotNull();
        assertThat(updatedLoan.getCollateralReleasedBtc()).isEqualByComparingTo("0.025");

        EscrowAccount escrow = escrowAccountRepository.findById(loan.getId()).orElseThrow();
        assertThat(escrow.getCurrentBalanceSats()).isEqualTo(0L);

        boolean releaseLedgerExists = loanLedgerRepository.findAll().stream()
                .anyMatch(l -> l.getLoan().getId().equals(loan.getId()) && l.getEntryType() == LedgerEntryType.COLLATERAL_RELEASED);
        assertThat(releaseLedgerExists).isTrue();

        boolean escrowClosedLedgerExists = loanLedgerRepository.findAll().stream()
                .anyMatch(l -> l.getLoan().getId().equals(loan.getId()) && l.getEntryType() == LedgerEntryType.ESCROW_CLOSED);
        assertThat(escrowClosedLedgerExists).isTrue();
    }

    @Test
    void shouldFailReleaseIfStatusNotRepaid() throws Exception {
        loan.setStatus(LoanStatus.ACTIVE);
        loanRepository.save(loan);

        mockMvc.perform(post("/admin/loans/" + loan.getId() + "/release-collateral")
                        .header("Authorization", adminToken)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isConflict());
    }

    @Test
    void shouldGetCollateralBalance() throws Exception {
        mockMvc.perform(get("/loans/" + loan.getId() + "/collateral")
                        .header("Authorization", borrowerToken)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.loanId").value(loan.getId().toString()))
                .andExpect(jsonPath("$.collateralBtc").value(0.025))
                .andExpect(jsonPath("$.status").value("LOCKED"));

        // After release
        mockMvc.perform(post("/admin/loans/" + loan.getId() + "/release-collateral")
                        .header("Authorization", adminToken)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());

        mockMvc.perform(get("/loans/" + loan.getId() + "/collateral")
                        .header("Authorization", borrowerToken)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RELEASED"));
    }
}
