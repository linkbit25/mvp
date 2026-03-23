package com.linkbit.mvp.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.linkbit.mvp.domain.*;
import com.linkbit.mvp.dto.DepositRequest;
import com.linkbit.mvp.dto.TopupRequest;
import com.linkbit.mvp.repository.BitcoinTransactionRepository;
import com.linkbit.mvp.repository.EscrowAccountRepository;
import com.linkbit.mvp.repository.LoanMarginCallRepository;
import com.linkbit.mvp.repository.LoanOfferRepository;
import com.linkbit.mvp.repository.LoanRepository;
import com.linkbit.mvp.repository.UserRepository;
import com.linkbit.mvp.service.BtcPriceService;
import com.linkbit.mvp.service.JwtService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
public class EscrowControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private LoanOfferRepository loanOfferRepository;

    @Autowired
    private LoanRepository loanRepository;

    @Autowired
    private EscrowAccountRepository escrowAccountRepository;

    @Autowired
    private BitcoinTransactionRepository bitcoinTransactionRepository;

    @Autowired
    private LoanMarginCallRepository marginCallRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtService jwtService;

    @MockBean
    private BtcPriceService btcPriceService;

    private User borrower;
    private User lender;
    private User admin;
    private Loan loan;
    private String borrowerToken;
    private String adminToken;

    @BeforeEach
    void setUp() {
        marginCallRepository.deleteAll();
        bitcoinTransactionRepository.deleteAll();
        escrowAccountRepository.deleteAll();
        loanRepository.deleteAll();
        loanOfferRepository.deleteAll();
        userRepository.deleteAll();

        lender = User.builder()
                .email("lender@example.com")
                .password(passwordEncoder.encode("password"))
                .phoneNumber("1111111111")
                .pseudonym("Lender1")
                .kycStatus(KycStatus.VERIFIED)
                .build();
        userRepository.save(lender);

        borrower = User.builder()
                .email("borrower@example.com")
                .password(passwordEncoder.encode("password"))
                .phoneNumber("3333333333")
                .pseudonym("Borrower1")
                .kycStatus(KycStatus.VERIFIED)
                .build();
        userRepository.save(borrower);
        borrowerToken = "Bearer " + jwtService.generateToken(borrower);

        admin = User.builder()
                .email("admin@example.com")
                .password(passwordEncoder.encode("password"))
                .phoneNumber("4444444444")
                .pseudonym("Admin")
                .role(ActorType.ADMIN)
                .kycStatus(KycStatus.VERIFIED)
                .build();
        userRepository.save(admin);
        adminToken = "Bearer " + jwtService.generateToken(admin);

        LoanOffer offer = LoanOffer.builder()
                .lender(lender)
                .loanAmountInr(new BigDecimal("100000"))
                .interestRate(new BigDecimal("12.0"))
                .expectedLtvPercent(60) // 60% LTV
                .tenureDays(90)
                .status(LoanOfferStatus.OPEN)
                .build();
        loanOfferRepository.save(offer);

        loan = Loan.builder()
                .offer(offer)
                .lender(lender)
                .borrower(borrower)
                .principalAmount(new BigDecimal("100000"))
                .interestRate(new BigDecimal("12.0"))
                .expectedLtvPercent(60)
                .tenureDays(90)
                .status(LoanStatus.AWAITING_COLLATERAL)
                .build();
        loanRepository.save(loan);
    }

    @Test
    void shouldGenerateMockEscrowAddress() throws Exception {
        mockMvc.perform(post("/loans/" + loan.getId() + "/escrow/generate")
                .header("Authorization", borrowerToken))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.escrow_address").exists())
                .andExpect(jsonPath("$.current_balance_sats").value(0));
    }

    @Test
    void shouldSubmitDepositSuccessfully() throws Exception {
        // First generate Escrow
        mockMvc.perform(post("/loans/" + loan.getId() + "/escrow/generate")
                .header("Authorization", borrowerToken));

        DepositRequest request = new DepositRequest();
        request.setAmountBtc(new BigDecimal("0.015")); // 1,500,000 sats

        mockMvc.perform(post("/loans/" + loan.getId() + "/deposit")
                .header("Authorization", borrowerToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isAccepted());

        List<BitcoinTransaction> transactions = bitcoinTransactionRepository.findByLoanId(loan.getId());
        assert transactions.size() == 1;
        assert transactions.get(0).getAmountSats() == 1_500_000L;
        assert transactions.get(0).getConfirmations() == 0;
    }

    @Test
    void shouldVerifyCollateralLtvAndLock() throws Exception {
        // Setup mock price: 1 BTC = ₹5,500,000
        Mockito.when(btcPriceService.getCurrentBtcPrice()).thenReturn(new BigDecimal("5500000.00"));

        // Generate Escrow
        mockMvc.perform(post("/loans/" + loan.getId() + "/escrow/generate")
                .header("Authorization", borrowerToken));

        // Note: Principal = 100,000 / 0.6 = 166,666.66 INR needed.
        // 166,666.66 / 5,500,000 = ~0.0303 BTC needed at least.
        DepositRequest request = new DepositRequest();
        request.setAmountBtc(new BigDecimal("0.05"));

        // Deposit
        mockMvc.perform(post("/loans/" + loan.getId() + "/deposit")
                .header("Authorization", borrowerToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)));

        // Verify
        mockMvc.perform(post("/admin/collateral/" + loan.getId() + "/verify")
                .header("Authorization", adminToken))
                .andExpect(status().isOk());

        // Check transition
        Loan updatedLoan = loanRepository.findById(loan.getId()).orElseThrow();
        assert updatedLoan.getStatus() == LoanStatus.COLLATERAL_LOCKED;
        
        EscrowAccount account = escrowAccountRepository.findById(loan.getId()).orElseThrow();
        assert account.getCurrentBalanceSats() == 5_000_000L;
    }

    @Test
    void shouldRefuseLockingWhenUndercollateralized() throws Exception {
        Mockito.when(btcPriceService.getCurrentBtcPrice()).thenReturn(new BigDecimal("5500000.00"));

        mockMvc.perform(post("/loans/" + loan.getId() + "/escrow/generate")
                .header("Authorization", borrowerToken));

        // Provide only 0.01 BTC instead of the required ~0.03 BTC
        DepositRequest request = new DepositRequest();
        request.setAmountBtc(new BigDecimal("0.01"));

        mockMvc.perform(post("/loans/" + loan.getId() + "/deposit")
                .header("Authorization", borrowerToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)));

        mockMvc.perform(post("/admin/collateral/" + loan.getId() + "/verify")
                .header("Authorization", adminToken))
                .andExpect(status().isOk());

        // Should NOT be locked
        Loan updatedLoan = loanRepository.findById(loan.getId()).orElseThrow();
        assert updatedLoan.getStatus() == LoanStatus.AWAITING_COLLATERAL;
    }

    @Test
    void shouldSubmitTopupSuccessfully() throws Exception {
        // First generate Escrow while loan is AWAITING_COLLATERAL
        mockMvc.perform(post("/loans/" + loan.getId() + "/escrow/generate")
                .header("Authorization", borrowerToken))
                .andExpect(status().isCreated());

        // Prepare loan in Margin Call
        loan.setStatus(LoanStatus.MARGIN_CALL);
        loanRepository.save(loan);

        TopupRequest request = new TopupRequest();
        request.setAmountBtc(new BigDecimal("0.015")); // 1,500,000 sats

        mockMvc.perform(post("/loans/" + loan.getId() + "/topup-collateral")
                .header("Authorization", borrowerToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andDo(org.springframework.test.web.servlet.result.MockMvcResultHandlers.print())
                .andExpect(status().isAccepted());

        List<BitcoinTransaction> transactions = bitcoinTransactionRepository.findByLoanId(loan.getId());
        assert transactions.size() == 1;
        assert transactions.get(0).getAmountSats() == 1_500_000L;
        assert transactions.get(0).getType() == BitcoinTransactionType.COLLATERAL_TOPUP;
        assert transactions.get(0).getConfirmations() == 0;
    }

    @Test
    void shouldVerifyTopupAndResolveMarginCall() throws Exception {
        // Setup mock price: 1 BTC = ₹5,500,000
        Mockito.when(btcPriceService.getCurrentBtcPrice()).thenReturn(new BigDecimal("5500000.00"));

        mockMvc.perform(post("/loans/" + loan.getId() + "/escrow/generate")
                .header("Authorization", borrowerToken))
                .andExpect(status().isCreated());

        loan.setStatus(LoanStatus.MARGIN_CALL);
        loan.setTotalOutstanding(new BigDecimal("100000")); // Principal outstanding
        loanRepository.save(loan);

        // Let's add an OPEN margin call 
        LoanMarginCall mc = LoanMarginCall.builder()
                .loan(loan)
                .status(MarginCallStatus.OPEN)
                .triggeredAt(java.time.LocalDateTime.now())
                .build();
        marginCallRepository.save(mc);

        // We deposit 0.05 BTC (worth 275,000 INR)
        // Outstanding is 100,000. LTV = 100,000 / 275,000 = ~36.36%
        // Margin call threshold default is 85%. So this resolves the margin call.
        TopupRequest request = new TopupRequest();
        request.setAmountBtc(new BigDecimal("0.05"));

        mockMvc.perform(post("/loans/" + loan.getId() + "/topup-collateral")
                .header("Authorization", borrowerToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)));

        // Verify Topup
        mockMvc.perform(post("/admin/collateral/" + loan.getId() + "/verify-topup")
                .header("Authorization", adminToken))
                .andDo(org.springframework.test.web.servlet.result.MockMvcResultHandlers.print())
                .andExpect(status().isOk());

        // Check verification states
        Loan updatedLoan = loanRepository.findById(loan.getId()).orElseThrow();
        assert updatedLoan.getStatus() == LoanStatus.ACTIVE;
        
        List<LoanMarginCall> resolvedCalls = marginCallRepository.findByLoanAndStatusIn(loan, List.of(MarginCallStatus.RESOLVED));
        assert resolvedCalls.size() == 1;
    }

    @Test
    void shouldRejectTopupIfNotInRiskState() throws Exception {
        loan.setStatus(LoanStatus.ACTIVE);
        loanRepository.save(loan);

        TopupRequest request = new TopupRequest();
        request.setAmountBtc(new BigDecimal("0.015"));

        mockMvc.perform(post("/loans/" + loan.getId() + "/topup-collateral")
                .header("Authorization", borrowerToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isInternalServerError());
    }
}
