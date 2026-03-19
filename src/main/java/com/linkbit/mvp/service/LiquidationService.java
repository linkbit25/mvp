package com.linkbit.mvp.service;

import com.linkbit.mvp.domain.ActorType;
import com.linkbit.mvp.domain.LedgerEntryType;
import com.linkbit.mvp.domain.Loan;
import com.linkbit.mvp.domain.LoanAction;
import com.linkbit.mvp.domain.LoanLedger;
import com.linkbit.mvp.domain.LoanLiquidation;
import com.linkbit.mvp.domain.LoanStatus;
import com.linkbit.mvp.repository.*;
import lombok.RequiredArgsConstructor;
import com.linkbit.mvp.service.BtcPriceService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class LiquidationService {

    private final LoanRepository loanRepository;
    private final LoanLiquidationRepository loanLiquidationRepository;
    private final LoanLedgerRepository loanLedgerRepository;
    private final BtcPriceService btcPriceService;
    private final StateMachineService stateMachineService;

    @Transactional
    public void executeLiquidation(UUID loanId) {
        Loan loan = loanRepository.findById(loanId)
                .orElseThrow(() -> new IllegalArgumentException("Loan not found: " + loanId));

        if (loan.getStatus() != LoanStatus.LIQUIDATION_ELIGIBLE) {
            if (loan.getStatus() == LoanStatus.LIQUIDATED) return;
            throw new IllegalStateException("Loan is not eligible for liquidation. Current status: " + loan.getStatus());
        }

        // 1. Fetch fresh BTC price
        BigDecimal btcPrice = btcPriceService.getCurrentBtcPrice();
        if (btcPrice == null || btcPrice.compareTo(BigDecimal.ZERO) <= 0) {
            throw new RuntimeException("Failed to fetch valid BTC price for liquidation.");
        }

        // Re-check LTV
        BigDecimal collateralValueInr = loan.getCollateralBtcAmount().multiply(btcPrice);
        BigDecimal outstanding = loan.getTotalOutstanding();
        if (outstanding == null) outstanding = loan.getTotalRepaymentAmount();
        if (outstanding == null) outstanding = loan.getPrincipalAmount();

        BigDecimal ltvPercent = outstanding
                .divide(collateralValueInr, 4, RoundingMode.HALF_UP)
                .multiply(new BigDecimal("100"));
        
        // If user topped up and LTV is now safe (below liquidation threshold), revert to ACTIVE
        int liquidationThreshold = loan.getLiquidationLtvPercent() != null ? loan.getLiquidationLtvPercent() : 95;
        if (ltvPercent.compareTo(new BigDecimal(liquidationThreshold)) < 0) {
            stateMachineService.transition(loan, LoanAction.LTV_RECOVERED, ActorType.SYSTEM);
            loanRepository.save(loan);
            log.info("Liquidation aborted for loan {} as LTV {} is below threshold {}", loanId, ltvPercent, liquidationThreshold);
            return;
        }

        // 2. Calculate collateral value
        // Already calculated as collateralValueInr

        // 3. Calculate liquidation penalty (5% of loan outstanding)
        BigDecimal penalty = outstanding.multiply(new BigDecimal("0.05"));

        // 4. Repay lender
        // Lender gets outstanding amount (principal + interest)
        BigDecimal lenderRepaymentAmount = outstanding;
        
        // Distribution Logic
        // 1. Lender Repayment
        // 2. Liquidation Penalty
        // 3. Borrower Remainder

        BigDecimal remainingCollateralValue = collateralValueInr;
        BigDecimal actualLenderRepayment = BigDecimal.ZERO;
        BigDecimal actualPenalty = BigDecimal.ZERO;
        BigDecimal borrowerReturn = BigDecimal.ZERO;

        if (remainingCollateralValue.compareTo(lenderRepaymentAmount) >= 0) {
            actualLenderRepayment = lenderRepaymentAmount;
            remainingCollateralValue = remainingCollateralValue.subtract(lenderRepaymentAmount);
        } else {
            actualLenderRepayment = remainingCollateralValue;
            remainingCollateralValue = BigDecimal.ZERO;
        }

        if (remainingCollateralValue.compareTo(penalty) >= 0) {
            actualPenalty = penalty;
            remainingCollateralValue = remainingCollateralValue.subtract(penalty);
        } else {
            actualPenalty = remainingCollateralValue;
            remainingCollateralValue = BigDecimal.ZERO;
        }

        borrowerReturn = remainingCollateralValue;

        // Update Loan
        stateMachineService.transition(loan, LoanAction.EXECUTE_LIQUIDATION, ActorType.SYSTEM);
        loan.setLiquidationExecutedAt(LocalDateTime.now());
        loan.setLiquidationPriceInr(btcPrice);
        loan.setLenderRepaymentAmount(actualLenderRepayment);
        loan.setLiquidationPenaltyAmount(actualPenalty);
        loan.setBorrowerReturnAmount(borrowerReturn);
        loanRepository.save(loan);

        // Record Liquidation
        LoanLiquidation liquidation = LoanLiquidation.builder()
                .loan(loan)
                .btcPriceInr(btcPrice)
                .collateralValueInr(collateralValueInr)
                .lenderRepaid(actualLenderRepayment)
                .liquidationPenalty(actualPenalty)
                .borrowerReturned(borrowerReturn)
                .build();
        loanLiquidationRepository.save(liquidation);

        // Record Ledger Entries
        createLedgerEntry(loan, LedgerEntryType.LIQUIDATION_EXECUTED, collateralValueInr, "Liquidation executed at BTC Price: " + btcPrice);
        createLedgerEntry(loan, LedgerEntryType.LENDER_REPAID, actualLenderRepayment, "Repayment to lender from liquidation");
        createLedgerEntry(loan, LedgerEntryType.LIQUIDATION_PENALTY, actualPenalty, "Liquidation penalty fee");
        createLedgerEntry(loan, LedgerEntryType.BORROWER_COLLATERAL_RETURN, borrowerReturn, "Remaining collateral value returned to borrower");

        log.info("Loan {} liquidated. Lender: {}, Penalty: {}, Borrower: {}", loanId, actualLenderRepayment, actualPenalty, borrowerReturn);
    }

    private void createLedgerEntry(Loan loan, LedgerEntryType type, BigDecimal amount, String description) {
        if (amount.compareTo(BigDecimal.ZERO) > 0 || type == LedgerEntryType.LIQUIDATION_EXECUTED) {
             LoanLedger ledger = LoanLedger.builder()
                .loan(loan)
                .entryType(type)
                .amountInr(amount)
                .notes(description)
                .build();
            loanLedgerRepository.save(ledger);
        }
    }
}
