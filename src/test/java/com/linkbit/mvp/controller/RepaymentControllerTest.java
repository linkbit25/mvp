package com.linkbit.mvp.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.linkbit.mvp.domain.*;
import com.linkbit.mvp.dto.RepaymentRequest;
import com.linkbit.mvp.repository.*;
import com.linkbit.mvp.service.DisbursementService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
public class RepaymentControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private LoanOfferRepository loanOfferRepository;

    @Autowired
    private LoanRepository loanRepository;

    @Autowired
    private EscrowAccountRepository escrowAccountRepository;

    @Autowired
    private DisbursementService disbursementService;

    @Autowired
    private LoanEmiRepository emiRepository;

    @Autowired
    private LoanRepaymentRepository repaymentRepository;

    @Autowired
    private LoanLedgerRepository ledgerRepository;

    private User lender;
    private User borrower;
    private Loan activeLoan;

    @BeforeEach
    void setUp() {
        lender = User.builder()
                .email("lender_repay@example.com")
                .password("password")
                .phoneNumber("111")
                .pseudonym("RepayLender")
                .kycStatus(KycStatus.VERIFIED)
                .build();
        userRepository.save(lender);

        borrower = User.builder()
                .email("borrower_repay@example.com")
                .password("password")
                .phoneNumber("222")
                .pseudonym("RepayBorrower")
                .kycStatus(KycStatus.VERIFIED)
                .build();
        userRepository.save(borrower);

        LoanOffer offer = loanOfferRepository.save(LoanOffer.builder()
                .lender(lender)
                .loanAmountInr(new BigDecimal("1000"))
                .interestRate(new BigDecimal("10"))
                .tenureDays(60) // 2 EMIs
                .expectedLtvPercent(150)
                .status(LoanOfferStatus.OPEN)
                .build());

        activeLoan = loanRepository.save(Loan.builder()
                .offer(offer)
                .lender(lender)
                .borrower(borrower)
                .principalAmount(offer.getLoanAmountInr())
                .interestRate(offer.getInterestRate())
                .repaymentType(RepaymentType.EMI)
                .emiCount(2)
                .emiAmount(new BigDecimal("550.00"))
                .totalRepaymentAmount(new BigDecimal("1100")) // 10% on 1000 (simplistic flat sum logic)
                .tenureDays(offer.getTenureDays())
                .collateralBtcAmount(new BigDecimal("1.5"))
                .status(LoanStatus.DISPUTE_OPEN) // Jumpstarting directly to activation call
                .build());

        // Kickoff initialization rules (this populates EMI + FIAT_DISBURSEMENT tracking logic)
        disbursementService.activateLoanAdmin(activeLoan.getId());
    }

    @Test
    @WithMockUser(username = "lender_repay@example.com")
    void shouldInitializeLedgerAndEmiSchedulesProperlyOnActivation() {
        Loan loan = loanRepository.findById(activeLoan.getId()).orElseThrow();
        assert loan.getStatus() == LoanStatus.ACTIVE;
        assert loan.getPrincipalOutstanding().compareTo(new BigDecimal("1000")) == 0;
        assert loan.getInterestOutstanding().compareTo(new BigDecimal("100")) == 0;
        assert loan.getTotalOutstanding().compareTo(new BigDecimal("1100")) == 0;

        List<LoanEmi> emis = emiRepository.findByLoanIdOrderByEmiNumberAsc(loan.getId());
        assert emis.size() == 2;
        assert emis.get(0).getEmiAmount().compareTo(new BigDecimal("550.00")) == 0;
        assert emis.get(1).getEmiAmount().compareTo(new BigDecimal("550.00")) == 0;

        List<LoanLedger> ledger = ledgerRepository.findByLoanIdOrderByCreatedAtAsc(loan.getId());
        assert ledger.size() == 1; // FIAT_DISBURSEMENT trigger
        assert ledger.get(0).getEntryType() == LedgerEntryType.FIAT_DISBURSEMENT;
        assert ledger.get(0).getAmountInr().compareTo(new BigDecimal("1000")) == 0;
    }

    @Test
    @WithMockUser(username = "borrower_repay@example.com")
    void borrowerShouldSubmitRepaymentSuccessfully() throws Exception {
        RepaymentRequest req = new RepaymentRequest(new BigDecimal("500"), "UPI-500", "http://img.proof");

        mockMvc.perform(post("/loans/" + activeLoan.getId() + "/repay")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isAccepted());

        List<LoanRepayment> repayments = repaymentRepository.findByLoanIdOrderByCreatedAtDesc(activeLoan.getId());
        assert repayments.size() == 1;
        assert repayments.get(0).getStatus() == RepaymentStatus.PENDING;
    }

    @Test
    @WithMockUser(username = "admin@example.com")
    void adminShouldVerifyAndLinearlySettleEmis() throws Exception {
        // Pre-create pending repayment for 600 INR
        LoanRepayment rep = loanRepaymentRepository().save(LoanRepayment.builder()
                .loan(activeLoan)
                .amountInr(new BigDecimal("600"))
                .transactionReference("REF1")
                .proofUrl("url")
                .status(RepaymentStatus.PENDING)
                .build());

        mockMvc.perform(post("/admin/repayments/" + rep.getId() + "/verify")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isAccepted());

        // Validate state changes
        Loan loan = loanRepository.findById(activeLoan.getId()).orElseThrow();
        assert loan.getTotalOutstanding().compareTo(new BigDecimal("500")) == 0; // 1100 - 600
        assert loan.getInterestOutstanding().compareTo(BigDecimal.ZERO) == 0; // Sent against interest (100) first
        assert loan.getPrincipalOutstanding().compareTo(new BigDecimal("500")) == 0; // Rest against principal (1000 - 500)

        List<LoanEmi> emis = emiRepository.findByLoanIdOrderByEmiNumberAsc(loan.getId());
        assert emis.get(0).getStatus() == EmiStatus.PAID; // First EMI (550) fully paid
        assert emis.get(0).getAmountPaid().compareTo(new BigDecimal("550.00")) == 0;
        
        assert emis.get(1).getStatus() == EmiStatus.PARTIAL; // Remaining 50 goes to second EMI
        assert emis.get(1).getAmountPaid().compareTo(new BigDecimal("50.00")) == 0;

        List<LoanLedger> ledger = ledgerRepository.findByLoanIdOrderByCreatedAtAsc(loan.getId());
        assert ledger.size() == 2;
        assert ledger.get(1).getEntryType() == LedgerEntryType.BORROWER_REPAYMENT;
    }

    @Test
    @WithMockUser(username = "admin@example.com")
    void shouldMoveToRepaidWhenFullySettled() throws Exception {
        LoanRepayment rep = loanRepaymentRepository().save(LoanRepayment.builder()
                .loan(activeLoan)
                .amountInr(new BigDecimal("1100"))
                .transactionReference("REF2")
                .proofUrl("url")
                .status(RepaymentStatus.PENDING)
                .build());

        mockMvc.perform(post("/admin/repayments/" + rep.getId() + "/verify"))
                .andExpect(status().isAccepted());

        Loan loan = loanRepository.findById(activeLoan.getId()).orElseThrow();
        assert loan.getStatus() == LoanStatus.REPAID;
        assert loan.getTotalOutstanding().compareTo(BigDecimal.ZERO) == 0;
    }

    @Test
    @WithMockUser(username = "lender_repay@example.com")
    void shouldReturnLedgerCorrectly() throws Exception {
        mockMvc.perform(get("/loans/" + activeLoan.getId() + "/ledger")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$[0].type").value("FIAT_DISBURSEMENT"));
    }

    private LoanRepaymentRepository loanRepaymentRepository() {
        return repaymentRepository;
    }
}
