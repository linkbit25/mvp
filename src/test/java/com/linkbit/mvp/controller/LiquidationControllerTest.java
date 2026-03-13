package com.linkbit.mvp.controller;

import com.linkbit.mvp.domain.*;
import com.linkbit.mvp.repository.*;
import com.linkbit.mvp.service.BtcPriceService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.security.test.context.support.WithMockUser;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
@WithMockUser(username = "admin", roles = {"ADMIN"})
class LiquidationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private LoanRepository loanRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private LoanOfferRepository loanOfferRepository;

    @Autowired
    private LoanLiquidationRepository loanLiquidationRepository;

    @Autowired
    private LoanLedgerRepository loanLedgerRepository;

    @MockBean
    private BtcPriceService btcPriceService;

    private User lender;
    private User borrower;
    private LoanOffer offer;
    private Loan loan;

    @BeforeEach
    void setUp() {
        loanLiquidationRepository.deleteAll();
        loanLedgerRepository.deleteAll();
        loanRepository.deleteAll();
        loanOfferRepository.deleteAll();
        userRepository.deleteAll();

        lender = User.builder()
                .email("lender@example.com")
                .password("password")
                .kycStatus(KycStatus.VERIFIED)
                .build();
        userRepository.save(lender);

        borrower = User.builder()
                .email("borrower@example.com")
                .password("password")
                .kycStatus(KycStatus.VERIFIED)
                .build();
        userRepository.save(borrower);

        offer = LoanOffer.builder()
                .lender(lender)
                .loanAmountInr(new BigDecimal("100000.00"))
                .interestRate(new BigDecimal("12.00"))
                .tenureDays(30)
                .expectedLtvPercent(70)
                .status(LoanOfferStatus.OPEN)
                .build();
        loanOfferRepository.save(offer);

        loan = Loan.builder()
                .offer(offer)
                .lender(lender)
                .borrower(borrower)
                .principalAmount(new BigDecimal("100000.00"))
                .interestRate(new BigDecimal("12.00"))
                .tenureDays(30)
                .status(LoanStatus.LIQUIDATION_ELIGIBLE)
                .collateralBtcAmount(new BigDecimal("0.02"))
                .totalOutstanding(new BigDecimal("100000.00"))
                .liquidationLtvPercent(90)
                .build();
        loanRepository.save(loan);
    }

    @Test
    void testSuccessfulLiquidation() throws Exception {
        // BTC Price = 5,000,000
        // Collateral = 0.02 * 5,000,000 = 100,000
        // Outstanding = 100,000
        // LTV = 100% (High enough for liquidation)
        when(btcPriceService.getCurrentBtcPrice()).thenReturn(new BigDecimal("5200000.00"));

        mockMvc.perform(post("/admin/loans/{loan_id}/execute-liquidation", loan.getId())
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());

        Loan updatedLoan = loanRepository.findById(loan.getId()).orElseThrow();
        assertEquals(LoanStatus.LIQUIDATED, updatedLoan.getStatus());
        assertNotNull(updatedLoan.getLiquidationExecutedAt());
        assertEquals(new BigDecimal("5000000.00"), updatedLoan.getLiquidationPriceInr());
        
        // 100,000 collateral, 100,000 outstanding
        // Lender gets 100,000
        // Penalty = 5% of 100,000 = 5,000. But only 0 left. So penalty = 0.
        // Borrower = 0 left.
        assertEquals(0, new BigDecimal("100000.00").compareTo(updatedLoan.getLenderRepaymentAmount()));
        assertEquals(0, BigDecimal.ZERO.compareTo(updatedLoan.getLiquidationPenaltyAmount()));
        assertEquals(0, BigDecimal.ZERO.compareTo(updatedLoan.getBorrowerReturnAmount()));

        List<LoanLiquidation> liquidations = loanLiquidationRepository.findAll();
        assertFalse(liquidations.isEmpty());
        
        List<LoanLedger> ledgerEntries = loanLedgerRepository.findAll();
        assertTrue(ledgerEntries.size() >= 2); // LIQUIDATION_EXECUTED and LENDER_REPAID
    }

    @Test
    void testLiquidationAbortedDueToTopUp() throws Exception {
        // BTC Price = 10,000,000
        // Collateral = 0.02 * 10,000,000 = 200,000
        // Outstanding = 100,000
        // LTV = 50% (Below liquidation threshold of 90%)
        when(btcPriceService.getCurrentBtcPrice()).thenReturn(new BigDecimal("10000000.00"));

        mockMvc.perform(post("/admin/loans/{loan_id}/execute-liquidation", loan.getId())
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());

        Loan updatedLoan = loanRepository.findById(loan.getId()).orElseThrow();
        assertEquals(LoanStatus.ACTIVE, updatedLoan.getStatus()); // Reverted to ACTIVE
        assertNull(updatedLoan.getLiquidationExecutedAt());
    }

    @Test
    void testLiquidationWithPenaltyAndBorrowerReturn() throws Exception {
        // BTC Price = 7,500,000
        // Collateral = 0.02 * 7,500,000 = 150,000
        // Outstanding = 100,000
        // LTV = 100,000 / 150,000 = 66.6% 
        // Wait, if LTV is 66.6%, it shouldn't be LIQUIDATION_ELIGIBLE normally, 
        // but the status is already LIQUIDATION_ELIGIBLE in setUp.
        // The service re-checks LTV. 66.6% < 90% (threshold), so it would abort.
        
        // Let's set BTC price so LTV is just above threshold
        // Outstanding = 100,000. Threshold = 90%.
        // Collateral Value needs to be < 100,000 / 0.9 = 111,111
        // BTC Price < 111,111 / 0.02 = 5,555,555
        
        when(btcPriceService.getCurrentBtcPrice()).thenReturn(new BigDecimal("5200000.00"));
        // Collateral Value = 0.02 * 5,200,000 = 104,000
        // Outstanding = 100,000
        // Penalty = 5% of 100,000 = 5,000
        
        // Distribution:
        // 1. Lender = 100,000
        // 2. Penalty = 104,000 - 100,000 = 4,000 (remaining collateral is less than penalty)
        // 3. Borrower = 0
        
        mockMvc.perform(post("/admin/loans/{loan_id}/execute-liquidation", loan.getId())
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());

        Loan updatedLoan = loanRepository.findById(loan.getId()).orElseThrow();
        assertEquals(LoanStatus.LIQUIDATED, updatedLoan.getStatus());
        assertEquals(0, new BigDecimal("100000.00").compareTo(updatedLoan.getLenderRepaymentAmount()));
        assertEquals(0, new BigDecimal("4000.00").compareTo(updatedLoan.getLiquidationPenaltyAmount()));
        assertEquals(0, BigDecimal.ZERO.compareTo(updatedLoan.getBorrowerReturnAmount()));
    }
}
