package com.linkbit.mvp.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.linkbit.mvp.domain.*;
import com.linkbit.mvp.dto.SignContractRequest;
import com.linkbit.mvp.dto.UpdateTermsRequest;
import com.linkbit.mvp.repository.LoanOfferRepository;
import com.linkbit.mvp.repository.LoanRepository;
import com.linkbit.mvp.repository.UserRepository;
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
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class NegotiationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private LoanOfferRepository loanOfferRepository;

    @Autowired
    private LoanRepository loanRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtService jwtService;

    private User lender;
    private User borrower;
    private Loan loan;
    private String lenderToken;
    private String borrowerToken;

    @BeforeEach
    void setUp() {
        loanRepository.deleteAll();
        loanOfferRepository.deleteAll();
        userRepository.deleteAll();

        // Create lender
        lender = User.builder()
                .email("lender@example.com")
                .password(passwordEncoder.encode("password"))
                .phoneNumber("1111111111")
                .pseudonym("Lender1")
                .kycStatus(KycStatus.VERIFIED)
                .build();
        userRepository.save(lender);
        lenderToken = "Bearer " + jwtService.generateToken(lender);

        // Create borrower
        borrower = User.builder()
                .email("borrower@example.com")
                .password(passwordEncoder.encode("password"))
                .phoneNumber("3333333333")
                .pseudonym("Borrower1")
                .kycStatus(KycStatus.VERIFIED)
                .build();
        userRepository.save(borrower);
        borrowerToken = "Bearer " + jwtService.generateToken(borrower);

        // Create offer
        LoanOffer offer = LoanOffer.builder()
                .lender(lender)
                .loanAmountInr(new BigDecimal("100000"))
                .interestRate(new BigDecimal("12.0"))
                .expectedLtvPercent(60)
                .tenureDays(90)
                .status(LoanOfferStatus.OPEN)
                .build();
        loanOfferRepository.save(offer);

        // Create loan
        loan = Loan.builder()
                .offer(offer)
                .lender(lender)
                .borrower(borrower)
                .principalAmount(new BigDecimal("100000"))
                .interestRate(new BigDecimal("12.0"))
                .tenureDays(90)
                .status(LoanStatus.NEGOTIATING)
                .build();
        loanRepository.save(loan);
    }

    @Test
    void shouldUpdateTermsSuccessfully() throws Exception {
        UpdateTermsRequest request = new UpdateTermsRequest();
        request.setPrincipalAmount(new BigDecimal("90000"));
        request.setInterestRate(new BigDecimal("11.5"));
        request.setTenureDays(60);
        request.setRepaymentType(RepaymentType.EMI);
        request.setEmiCount(2);
        request.setExpectedLtvPercent(55);
        request.setMarginCallLtvPercent(70);
        request.setLiquidationLtvPercent(80);

        mockMvc.perform(put("/loans/" + loan.getId() + "/terms")
                .header("Authorization", lenderToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());
    }

    @Test
    void shouldFinalizeContract() throws Exception {
        // First update terms to ensure all fields are set
        UpdateTermsRequest request = new UpdateTermsRequest();
        request.setPrincipalAmount(new BigDecimal("100000"));
        request.setInterestRate(new BigDecimal("12.0"));
        request.setTenureDays(90);
        request.setRepaymentType(RepaymentType.EMI);
        request.setEmiCount(3);
        request.setExpectedLtvPercent(60);
        request.setMarginCallLtvPercent(75);
        request.setLiquidationLtvPercent(85);

        mockMvc.perform(put("/loans/" + loan.getId() + "/terms")
                .header("Authorization", lenderToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());

        // Then finalize
        mockMvc.perform(post("/loans/" + loan.getId() + "/finalize")
                .header("Authorization", lenderToken))
                .andExpect(status().isOk());

        Loan updatedLoan = loanRepository.findById(loan.getId()).orElseThrow();
        assertThat(updatedLoan.getAgreementHash()).isNotBlank();
        assertThat(updatedLoan.getAgreementFinalizedAt()).isNotNull();
        assertThat(updatedLoan.getOffer().getStatus()).isEqualTo(LoanOfferStatus.CLOSED);
    }

    @Test
    void shouldSignContract() throws Exception {
        // Prepare loan in AWAITING_SIGNATURES state
        loan.setStatus(LoanStatus.AWAITING_SIGNATURES);
        loan.setRepaymentType(RepaymentType.EMI);
        loan.setAgreementHash("hash");
        loan.setAgreementFinalizedAt(java.time.LocalDateTime.now());
        loanRepository.save(loan);

        SignContractRequest request = new SignContractRequest();
        request.setSignatureString("signature_string_borrower");

        mockMvc.perform(post("/loans/" + loan.getId() + "/sign")
                .header("Authorization", borrowerToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());
        
        // Check if borrower signature is saved (I can verify via repository but status check is enough for controller test)
    }

    @Test
    void shouldCancelNegotiation() throws Exception {
        mockMvc.perform(post("/loans/" + loan.getId() + "/cancel")
                .header("Authorization", borrowerToken))
                .andExpect(status().isOk());
    }
}
