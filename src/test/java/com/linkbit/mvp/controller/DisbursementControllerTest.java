package com.linkbit.mvp.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.linkbit.mvp.domain.*;
import com.linkbit.mvp.dto.DisbursementRequest;
import com.linkbit.mvp.repository.EscrowAccountRepository;
import com.linkbit.mvp.repository.LoanOfferRepository;
import com.linkbit.mvp.repository.LoanRepository;
import com.linkbit.mvp.repository.UserRepository;
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
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
public class DisbursementControllerTest {

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

    private User lender;
    private User borrower;
    private Loan lockedLoan;

    @BeforeEach
    void setUp() {
        lender = User.builder()
                .email("lender_disburse@example.com")
                .password("password")
                .phoneNumber("111222333")
                .pseudonym("DisbursingLender")
                .kycStatus(KycStatus.VERIFIED)
                .build();
        userRepository.save(lender);

        borrower = User.builder()
                .email("borrower_disburse@example.com")
                .password("password")
                .phoneNumber("444555666")
                .pseudonym("ReceivingBorrower")
                .kycStatus(KycStatus.VERIFIED)
                .build();

        UserKycDetails borrowerKyc = new UserKycDetails();
        borrowerKyc.setUser(borrower);
        borrowerKyc.setFullLegalName("Bob Borrower");
        borrowerKyc.setBankAccountNumber("ACCT12345");
        borrowerKyc.setIfsc("IFSC001");
        borrowerKyc.setUpiId("bob@upi");
        borrower.setKycDetails(borrowerKyc);
        userRepository.save(borrower);

        LoanOffer offer = loanOfferRepository.save(LoanOffer.builder()
                .lender(lender)
                .loanAmountInr(new BigDecimal("100000"))
                .interestRate(new BigDecimal("10"))
                .tenureDays(30)
                .expectedLtvPercent(150)
                .status(LoanOfferStatus.OPEN)
                .build());

        lockedLoan = loanRepository.save(Loan.builder()
                .offer(offer)
                .lender(lender)
                .borrower(borrower)
                .principalAmount(offer.getLoanAmountInr())
                .interestRate(offer.getInterestRate())
                .tenureDays(offer.getTenureDays())
                .expectedLtvPercent(offer.getExpectedLtvPercent())
                .collateralBtcAmount(new BigDecimal("1.5"))
                .status(LoanStatus.COLLATERAL_LOCKED)
                .build());
                
        escrowAccountRepository.insertEscrowAccount(lockedLoan.getId(), "bc1qtest", 10000000L);
    }

    @Test
    @WithMockUser(username = "lender_disburse@example.com")
    void shouldReturnPaymentDetailsForLender() throws Exception {
        mockMvc.perform(get("/loans/" + lockedLoan.getId() + "/payment-details")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.account_number").value("ACCT12345"))
                .andExpect(jsonPath("$.ifsc").value("IFSC001"))
                .andExpect(jsonPath("$.upi_id").value("bob@upi"));
    }

    @Test
    @WithMockUser(username = "borrower_disburse@example.com")
    void shouldDenyPaymentDetailsForBorrower() throws Exception {
        // Only lender can view these details
        mockMvc.perform(get("/loans/" + lockedLoan.getId() + "/payment-details")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isInternalServerError());
    }

    @Test
    @WithMockUser(username = "lender_disburse@example.com")
    void shouldMarkDisbursedSuccessfully() throws Exception {
        DisbursementRequest req = new DisbursementRequest("UPI-REF-123", "https://img.proof");

        mockMvc.perform(post("/loans/" + lockedLoan.getId() + "/disburse")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isAccepted());

        Loan updated = loanRepository.findById(lockedLoan.getId()).orElseThrow();
        assert updated.getFiatDisbursedAt() != null;
        assert "UPI-REF-123".equals(updated.getDisbursementReference());
        assert "https://img.proof".equals(updated.getDisbursementProofUrl());
    }

    @Test
    @WithMockUser(username = "borrower_disburse@example.com")
    void shouldConfirmReceiptSuccessfully() throws Exception {
        // Pre-disburse
        lockedLoan.setFiatDisbursedAt(java.time.LocalDateTime.now());
        loanRepository.save(lockedLoan);

        mockMvc.perform(post("/loans/" + lockedLoan.getId() + "/confirm-receipt")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isAccepted());

        Loan updated = loanRepository.findById(lockedLoan.getId()).orElseThrow();
        assert updated.getStatus() == LoanStatus.ACTIVE;
        assert updated.getFiatReceivedConfirmedAt() != null;
    }

    @Test
    @WithMockUser(username = "borrower_disburse@example.com")
    void shouldOpenDisputeSuccessfully() throws Exception {
        // Pre-disburse
        lockedLoan.setFiatDisbursedAt(java.time.LocalDateTime.now());
        loanRepository.save(lockedLoan);

        mockMvc.perform(post("/loans/" + lockedLoan.getId() + "/open-dispute")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isAccepted());

        Loan updated = loanRepository.findById(lockedLoan.getId()).orElseThrow();
        assert updated.getStatus() == LoanStatus.DISPUTE_OPEN;
    }

    @Test
    @WithMockUser(username = "admin@example.com")
    void shouldActivateLoanThroughAdminArbitration() throws Exception {
        lockedLoan.setStatus(LoanStatus.DISPUTE_OPEN);
        loanRepository.save(lockedLoan);

        mockMvc.perform(post("/admin/loans/" + lockedLoan.getId() + "/activate")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isAccepted());

        Loan updated = loanRepository.findById(lockedLoan.getId()).orElseThrow();
        assert updated.getStatus() == LoanStatus.ACTIVE;
    }

    @Test
    @WithMockUser(username = "admin@example.com")
    void shouldRefundCollateralThroughAdminArbitration() throws Exception {
        lockedLoan.setStatus(LoanStatus.DISPUTE_OPEN);
        loanRepository.save(lockedLoan);

        mockMvc.perform(post("/admin/loans/" + lockedLoan.getId() + "/refund-collateral")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isAccepted());

        Loan updated = loanRepository.findById(lockedLoan.getId()).orElseThrow();
        assert updated.getStatus() == LoanStatus.CLOSED;

        EscrowAccount escrow = escrowAccountRepository.findById(lockedLoan.getId()).orElseThrow();
        assert escrow.getCurrentBalanceSats() == 0L;
    }
}
