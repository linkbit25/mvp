package com.linkbit.mvp.security;

import com.linkbit.mvp.domain.*;
import com.linkbit.mvp.dto.*;
import com.linkbit.mvp.repository.*;
import com.linkbit.mvp.service.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.annotation.DirtiesContext;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.BDDMockito.given;

@SpringBootTest
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_EACH_TEST_METHOD)
public class LoanLifecycleIntegrationTest {

    @Autowired private AuthService authService;
    @Autowired private LoanMarketplaceService marketplaceService;
    @Autowired private NegotiationService negotiationService;
    @Autowired private PaymentService paymentService;
    @Autowired private EscrowService escrowService;
    @Autowired private DisbursementService disbursementService;
    @Autowired private RepaymentService repaymentService;
    @Autowired private CollateralReleaseService collateralReleaseService;
    @Autowired private LiquidationService liquidationService;
    @Autowired private LtvMonitoringWorker ltvMonitoringWorker;
    @Autowired private StateMachineService stateMachineService;
    
    @MockBean private BtcPriceService btcPriceService;
    @MockBean private org.springframework.messaging.simp.SimpMessagingTemplate messagingTemplate;

    @Autowired private LoanRepository loanRepository;
    @Autowired private LoanOfferRepository loanOfferRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private LoanAuditLogRepository auditLogRepository;
    @Autowired private LoanRepaymentRepository repaymentRepository;

    private String borrowerEmail = "borrower@test.com";
    private String lenderEmail = "lender@test.com";

    @BeforeEach
    void setUp() {
        loanRepository.deleteAll();
        loanOfferRepository.deleteAll();
        userRepository.deleteAll();

        // Default price for setup phases
        given(btcPriceService.getCurrentBtcPrice()).willReturn(new BigDecimal("60000"));

        // Register borrower
        RegisterRequest br = new RegisterRequest();
        br.setName("Borrower Name");
        br.setEmail(borrowerEmail);
        br.setPassword("pass");
        br.setDob("1990-01-01");
        br.setPseudonym("Borrower");
        br.setPhoneNumber("1234567890");
        br.setBankAccountNumber("123456789");
        br.setIfsc("BKID0001");
        br.setUpiId("borrower@upi");
        authService.register(br);

        // Register lender
        RegisterRequest lr = new RegisterRequest();
        lr.setName("Lender Name");
        lr.setEmail(lenderEmail);
        lr.setPassword("pass");
        lr.setDob("1990-01-01");
        lr.setPseudonym("Lender");
        lr.setPhoneNumber("0987654321");
        lr.setBankAccountNumber("987654321");
        lr.setIfsc("BKID0002");
        lr.setUpiId("lender@upi");
        authService.register(lr);

        User borrower = userRepository.findByEmail(borrowerEmail).get();
        borrower.setKycStatus(KycStatus.VERIFIED);
        userRepository.save(borrower);

        User lender = userRepository.findByEmail(lenderEmail).get();
        lender.setKycStatus(KycStatus.VERIFIED);
        userRepository.save(lender);

        userRepository.save(com.linkbit.mvp.domain.User.builder()
                .email("system@linkbit.internal")
                .password("sys")
                .role(ActorType.SYSTEM)
                .kycStatus(KycStatus.VERIFIED)
                .build());
        userRepository.save(com.linkbit.mvp.domain.User.builder()
                .email("admin@linkbit.com")
                .password("admin")
                .role(ActorType.ADMIN)
                .kycStatus(KycStatus.VERIFIED)
                .build());
    }

    @Test
    void testHappyPath_FullLifecycle() {
        // 1. Create Offer (Lender)
        CreateOfferRequest cor = new CreateOfferRequest();
        cor.setLoanAmountInr(new BigDecimal("10000"));
        cor.setInterestRate(new BigDecimal("12"));
        cor.setTenureDays(30);
        cor.setExpectedLtvPercent(50);
        marketplaceService.createOffer(lenderEmail, cor);
        
        LoanOffer offer = loanOfferRepository.findAll().get(0);

        // 2. Connect Offer (Borrower)
        UUID loanId = marketplaceService.connectOffer(borrowerEmail, offer.getId());
        
        // 3. Negotiate & Finalize (Lender)
        UpdateTermsRequest utr = new UpdateTermsRequest();
        utr.setPrincipalAmount(new BigDecimal("10000"));
        utr.setInterestRate(new BigDecimal("12"));
        utr.setTenureDays(30);
        utr.setRepaymentType(RepaymentType.BULLET);
        utr.setEmiCount(1);
        utr.setExpectedLtvPercent(50);
        utr.setMarginCallLtvPercent(80);
        utr.setLiquidationLtvPercent(95);
        
        negotiationService.updateTerms(lenderEmail, loanId, utr);
        negotiationService.finalizeContract(lenderEmail, loanId);
        assertEquals(LoanStatus.NEGOTIATING, getLoan(loanId).getStatus()); // Still negotiating until both agree
        
        negotiationService.finalizeContract(borrowerEmail, loanId);
        assertEquals(LoanStatus.AWAITING_SIGNATURES, getLoan(loanId).getStatus());

        // 4. Sign (Both)
        negotiationService.signContract(borrowerEmail, loanId, "SIG_BORROWER");
        negotiationService.signContract(lenderEmail, loanId, "SIG_LENDER");
        assertEquals(LoanStatus.AWAITING_FEE, getLoan(loanId).getStatus());

        // 5. Pay Fee (Dual)
        FeeResponse bFee = paymentService.initiateFeePayment(borrowerEmail, loanId);
        paymentService.verifyPayment(bFee.getFeeId());
        assertEquals(LoanStatus.AWAITING_FEE, getLoan(loanId).getStatus()); // Still awaiting lender

        FeeResponse lFee = paymentService.initiateFeePayment(lenderEmail, loanId);
        paymentService.verifyPayment(lFee.getFeeId());
        assertEquals(LoanStatus.AWAITING_COLLATERAL, getLoan(loanId).getStatus());

        // 6. Deposit Collateral (Borrower)
        escrowService.generateAddress(borrowerEmail, loanId);
        escrowService.deposit(borrowerEmail, loanId, new BigDecimal("0.5")); // plenty of collateral
        escrowService.verifyDeposit(loanId);
        assertEquals(LoanStatus.COLLATERAL_LOCKED, getLoan(loanId).getStatus());

        // 7. Disburse Fiat (Lender)
        disbursementService.markDisbursed(loanId, lenderEmail, DisbursementRequest.builder()
                .transactionReference("REF123")
                .proofImageUrl("http://proof.jpg")
                .build());
        
        // 8. Confirm Receipt (Borrower)
        disbursementService.confirmReceipt(loanId, borrowerEmail);
        assertEquals(LoanStatus.ACTIVE, getLoan(loanId).getStatus());

        // 9. Repay (Borrower)
        Loan loan = getLoan(loanId);
        BigDecimal totalDue = loan.getTotalOutstanding();
        repaymentService.submitRepayment(loanId, borrowerEmail, RepaymentRequest.builder()
                .amount(totalDue)
                .transactionReference("REPAY123")
                .proofImageUrl("http://repay.jpg")
                .build());
        
        // Verify (Admin/System)
        List<LoanRepayment> repayments = repaymentRepository.findByLoanIdOrderByCreatedAtDesc(loanId);
        assertFalse(repayments.isEmpty());
        UUID repaymentId = repayments.get(0).getId();
        repaymentService.verifyRepayment(repaymentId);
        
        // Due to automated collateral release, state goes immediately from REPAID -> CLOSED
        assertEquals(LoanStatus.CLOSED, getLoan(loanId).getStatus());

        // Final Verify: Audit logs should exist for all major transitions
        List<LoanAuditLog> logs = auditLogRepository.findByLoanId(loanId);
        assertTrue(logs.size() >= 5, "Expected at least 5 audit logs for the major transitions, found " + logs.size());
    }

    @Test
    void testLiquidationPath() {
        setupLoanToActive();
        Loan loan = loanRepository.findAll().get(0);
        UUID loanId = loan.getId();

        // Drop BTC Price to $20,000 (LTV will exceed 95%)
        given(btcPriceService.getCurrentBtcPrice()).willReturn(new BigDecimal("20000"));
        ltvMonitoringWorker.monitorLtvLevels();
        
        assertEquals(LoanStatus.LIQUIDATION_ELIGIBLE, getLoan(loanId).getStatus());
        
        liquidationService.executeLiquidation(loanId);
        assertEquals(LoanStatus.LIQUIDATED, getLoan(loanId).getStatus());
    }

    @Test
    void testIdempotency_FailSafes() {
        setupLoanToDisbursed(); // Loan is in COLLATERAL_LOCKED, markDisbursed has been called once
        Loan loan = loanRepository.findAll().get(0);
        UUID loanId = loan.getId();

        // 1. Duplicate Disbursement (should be idempotent / no error)
        int initialLogs = auditLogRepository.findByLoanId(loanId).size();
        disbursementService.markDisbursed(loanId, lenderEmail, DisbursementRequest.builder()
                .transactionReference("REF-DUP")
                .build());
        // No new audit log because no state transition happened, but service should not throw
        
        // Move to ACTIVE for next tests
        disbursementService.confirmReceipt(loanId, borrowerEmail);
        assertEquals(LoanStatus.ACTIVE, getLoan(loanId).getStatus());

        // 2. Duplicate Repayment Verification
        BigDecimal totalDue = getLoan(loanId).getTotalOutstanding();
        repaymentService.submitRepayment(loanId, borrowerEmail, RepaymentRequest.builder()
                .amount(totalDue)
                .transactionReference("REPAY-DUP")
                .proofImageUrl("http://repay.jpg")
                .build());
        LoanRepayment repayment = repaymentRepository.findByLoanIdOrderByCreatedAtDesc(loanId).get(0);
        
        repaymentService.verifyRepayment(repayment.getId());
        assertEquals(LoanStatus.CLOSED, getLoan(loanId).getStatus());
        
        // Retry verification
        assertThrows(RuntimeException.class, () -> repaymentService.verifyRepayment(repayment.getId()));

        // 3. Duplicate Liquidation
        // Setup another active loan for liquidation test
        Loan loan2 = createManualActiveLoan(new BigDecimal("10000"), new BigDecimal("0.5"));
        given(btcPriceService.getCurrentBtcPrice()).willReturn(new BigDecimal("20000"));
        ltvMonitoringWorker.monitorLtvLevels();
        assertEquals(LoanStatus.LIQUIDATION_ELIGIBLE, getLoan(loan2.getId()).getStatus());
        
        liquidationService.executeLiquidation(loan2.getId());
        assertEquals(LoanStatus.LIQUIDATED, getLoan(loan2.getId()).getStatus());
        
        // Retry liquidation (should be idempotent and silent)
        liquidationService.executeLiquidation(loan2.getId());
        assertEquals(LoanStatus.LIQUIDATED, getLoan(loan2.getId()).getStatus());

        // 4. Duplicate Collateral Release
        collateralReleaseService.releaseCollateral(loanId, lenderEmail);
        assertEquals(LoanStatus.CLOSED, getLoan(loanId).getStatus());
        
        // Retry release (should be idempotent and silent)
        collateralReleaseService.releaseCollateral(loanId, lenderEmail);
        assertEquals(LoanStatus.CLOSED, getLoan(loanId).getStatus());
    }

    private Loan createManualActiveLoan(BigDecimal amount, BigDecimal collateral) {
        CreateOfferRequest cor = new CreateOfferRequest();
        cor.setLoanAmountInr(amount);
        cor.setInterestRate(new BigDecimal("12"));
        cor.setTenureDays(30);
        cor.setExpectedLtvPercent(50);
        marketplaceService.createOffer(lenderEmail, cor);
        LoanOffer offer = loanOfferRepository.findAll().get(0);

        Loan loan = Loan.builder()
                .offer(offer)
                .lender(userRepository.findByEmail(lenderEmail).get())
                .borrower(userRepository.findByEmail(borrowerEmail).get())
                .principalAmount(amount)
                .interestRate(new BigDecimal("12"))
                .tenureDays(30)
                .collateralBtcAmount(collateral)
                .status(LoanStatus.COLLATERAL_LOCKED)
                .marginCallLtvPercent(80)
                .liquidationLtvPercent(95)
                .principalOutstanding(BigDecimal.ZERO)
                .interestOutstanding(BigDecimal.ZERO)
                .totalOutstanding(BigDecimal.ZERO)
                .build();
        
        loan = loanRepository.save(loan);
        repaymentService.initializeLoanFinancials(loan);
        
        // Re-fetch to ensure we have the version updated by initializeLoanFinancials
        loan = loanRepository.findById(loan.getId()).get();
        loan = stateMachineService.transition(loan, LoanAction.DISBURSE_FIAT, ActorType.LENDER);
        
        return loan;
    }


    @Test
    void testDisputePath_ResolveByActivation() {
        setupLoanToDisbursed();
        Loan loan = loanRepository.findAll().get(0);
        UUID loanId = loan.getId();

        disbursementService.openDispute(loanId, borrowerEmail);
        assertEquals(LoanStatus.DISPUTE_OPEN, getLoan(loanId).getStatus());

        disbursementService.activateLoanAdmin(loanId);
        assertEquals(LoanStatus.ACTIVE, getLoan(loanId).getStatus());
    }

    @Test
    void testDisputePath_ResolveByRefund() {
        setupLoanToDisbursed();
        Loan loan = loanRepository.findAll().get(0);
        UUID loanId = loan.getId();

        disbursementService.openDispute(loanId, borrowerEmail);
        
        disbursementService.refundCollateralAdmin(loanId);
        assertEquals(LoanStatus.CLOSED, getLoan(loanId).getStatus());
    }

    private void setupLoanToActive() {
        setupLoanToDisbursed();
        Loan loan = loanRepository.findAll().get(0);
        disbursementService.confirmReceipt(loan.getId(), borrowerEmail);
    }

    private void setupLoanToDisbursed() {
        CreateOfferRequest cor = new CreateOfferRequest();
        cor.setLoanAmountInr(new BigDecimal("10000"));
        cor.setInterestRate(new BigDecimal("12"));
        cor.setTenureDays(30);
        cor.setExpectedLtvPercent(50);
        marketplaceService.createOffer(lenderEmail, cor);

        LoanOffer offer = loanOfferRepository.findAll().get(0);
        UUID loanId = marketplaceService.connectOffer(borrowerEmail, offer.getId());
        
        UpdateTermsRequest utr = new UpdateTermsRequest();
        utr.setPrincipalAmount(new BigDecimal("10000"));
        utr.setInterestRate(new BigDecimal("12"));
        utr.setTenureDays(30);
        utr.setRepaymentType(RepaymentType.BULLET);
        utr.setEmiCount(1);
        utr.setExpectedLtvPercent(50);
        utr.setMarginCallLtvPercent(80);
        utr.setLiquidationLtvPercent(95);

        negotiationService.updateTerms(lenderEmail, loanId, utr);
        negotiationService.finalizeContract(lenderEmail, loanId);
        negotiationService.finalizeContract(borrowerEmail, loanId);
        negotiationService.signContract(borrowerEmail, loanId, "SIG_B");
        negotiationService.signContract(lenderEmail, loanId, "SIG_L");
        FeeResponse bFee = paymentService.initiateFeePayment(borrowerEmail, loanId);
        paymentService.verifyPayment(bFee.getFeeId());
        FeeResponse lFee = paymentService.initiateFeePayment(lenderEmail, loanId);
        paymentService.verifyPayment(lFee.getFeeId());
        escrowService.generateAddress(borrowerEmail, loanId);
        escrowService.deposit(borrowerEmail, loanId, new BigDecimal("0.5"));
        escrowService.verifyDeposit(loanId);
        disbursementService.markDisbursed(loanId, lenderEmail, DisbursementRequest.builder()
                .transactionReference("REF")
                .proofImageUrl("http://proof.jpg")
                .build());
    }

    private Loan getLoan(UUID loanId) {
        return loanRepository.findById(loanId).get();
    }
}
