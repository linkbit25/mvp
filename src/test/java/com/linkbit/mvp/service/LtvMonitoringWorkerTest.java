package com.linkbit.mvp.service;

import com.linkbit.mvp.domain.*;
import com.linkbit.mvp.repository.LoanLtvHistoryRepository;
import com.linkbit.mvp.repository.LoanOfferRepository;
import com.linkbit.mvp.repository.LoanRepository;
import com.linkbit.mvp.repository.UserRepository;
import com.linkbit.mvp.repository.LoanMarginCallRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
public class LtvMonitoringWorkerTest {

    @Autowired
    private LtvMonitoringWorker worker;

    @Autowired
    private LoanRepository loanRepository;

    @Autowired
    private LoanOfferRepository loanOfferRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private LoanLtvHistoryRepository historyRepository;

    @Autowired
    private LoanMarginCallRepository marginCallRepository;

    @MockBean
    private WazirXService WazirXService;

    private Loan activeLoan;

    @BeforeEach
    void setUp() {
        marginCallRepository.deleteAll();
        historyRepository.deleteAll();
        loanRepository.deleteAll();
        loanOfferRepository.deleteAll();
        userRepository.deleteAll();

        User lender = userRepository.save(User.builder().email("lender_ltv@example.com").password("pass").phoneNumber("1").pseudonym("L1").kycStatus(KycStatus.VERIFIED).build());
        User borrower = userRepository.save(User.builder().email("borrower_ltv@example.com").password("pass").phoneNumber("2").pseudonym("B1").kycStatus(KycStatus.VERIFIED).build());

        LoanOffer offer = loanOfferRepository.save(LoanOffer.builder()
                .lender(lender)
                .loanAmountInr(new BigDecimal("100000"))
                .interestRate(new BigDecimal("10"))
                .tenureDays(30)
                .expectedLtvPercent(100)
                .status(LoanOfferStatus.OPEN)
                .build());

        activeLoan = loanRepository.save(Loan.builder()
                .offer(offer)
                .lender(lender)
                .borrower(borrower)
                .principalAmount(offer.getLoanAmountInr())
                .interestRate(offer.getInterestRate())
                .tenureDays(30)
                .totalOutstanding(new BigDecimal("100000"))
                .collateralBtcAmount(new BigDecimal("0.02"))
                .marginCallLtvPercent(80)
                .liquidationLtvPercent(90)
                .status(LoanStatus.ACTIVE)
                .build());
    }

    @Test
    void shouldMaintainActiveIfLtvBelowMargin() {
        // BTC = 6,000,000 -> 0.02 BTC = 120,000 INR
        // 100,000 / 120,000 = ~83.33% LTV (above 80 margin... wait, let's bump the price to make it safe)
        when(WazirXService.getCurrentBtcPrice()).thenReturn(new BigDecimal("7000000.00"));
        // BTC = 7,000,000 -> 0.02 BTC = 140,000 INR -> 100,000/140,000 = ~71.4% LTV (Safe!)

        worker.monitorLtvLevels();

        Loan l = loanRepository.findById(activeLoan.getId()).orElseThrow();
        assertThat(l.getStatus()).isEqualTo(LoanStatus.ACTIVE);
        assertThat(l.getCurrentLtvPercent().doubleValue()).isCloseTo(71.4286, org.assertj.core.data.Offset.offset(0.1));
        
        List<LoanLtvHistory> hist = historyRepository.findAll();
        assertThat(hist).isEmpty(); // No state change implies no record inserted in this simple iteration 
    }

    @Test
    void shouldTransitionToMarginCall() {
        // 100,000 / (0.02 * 6,000,000) = 83.33% LTV. Margin is 80%.
        when(WazirXService.getCurrentBtcPrice()).thenReturn(new BigDecimal("6000000.00"));

        worker.monitorLtvLevels();

        Loan l = loanRepository.findById(activeLoan.getId()).orElseThrow();
        assertThat(l.getStatus()).isEqualTo(LoanStatus.MARGIN_CALL);
        assertThat(l.getCurrentLtvPercent().doubleValue()).isCloseTo(83.3333, org.assertj.core.data.Offset.offset(0.1));

        List<LoanLtvHistory> hist = historyRepository.findAll();
        assertThat(hist).hasSize(1);
        assertThat(hist.get(0).getLtvPercent().doubleValue()).isCloseTo(83.3333, org.assertj.core.data.Offset.offset(0.1));
    }

    @Test
    void shouldTransitionToLiquidationEligible() {
        // 100,000 / (0.02 * 5,000,000) = 100% LTV. Liq is 90%.
        when(WazirXService.getCurrentBtcPrice()).thenReturn(new BigDecimal("5000000.00"));

        worker.monitorLtvLevels();

        Loan l = loanRepository.findById(activeLoan.getId()).orElseThrow();
        assertThat(l.getStatus()).isEqualTo(LoanStatus.LIQUIDATION_ELIGIBLE);
        assertThat(l.getCurrentLtvPercent().doubleValue()).isCloseTo(100.0, org.assertj.core.data.Offset.offset(0.1));

        List<LoanLtvHistory> hist = historyRepository.findAll();
        assertThat(hist).hasSize(1);
    }

    @Test
    void shouldRecoverFromMarginCallToActiveIfPriceRises() {
        // Setup initial Margin Call
        activeLoan.setStatus(LoanStatus.MARGIN_CALL);
        loanRepository.save(activeLoan);

        // Price goes to moon 
        when(WazirXService.getCurrentBtcPrice()).thenReturn(new BigDecimal("8000000.00")); // 0.02 = 160,000 -> 62.5% LTV

        worker.monitorLtvLevels();

        Loan l = loanRepository.findById(activeLoan.getId()).orElseThrow();
        assertThat(l.getStatus()).isEqualTo(LoanStatus.ACTIVE); // Recovered
    }
}
