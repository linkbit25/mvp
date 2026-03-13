package com.linkbit.mvp.service;

import com.linkbit.mvp.domain.*;
import com.linkbit.mvp.repository.*;
import lombok.RequiredArgsConstructor;
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
public class CollateralReleaseService {

    private final LoanRepository loanRepository;
    private final EscrowAccountRepository escrowAccountRepository;
    private final LoanLedgerRepository loanLedgerRepository;
    private final CollateralReleaseRepository collateralReleaseRepository;

    @Transactional
    public void releaseCollateral(UUID loanId, UUID adminId) {
        Loan loan = loanRepository.findById(loanId)
                .orElseThrow(() -> new IllegalArgumentException("Loan not found: " + loanId));

        if (loan.getStatus() != LoanStatus.REPAID) {
            throw new IllegalStateException("Collateral release denied. Loan status must be REPAID, but is " + loan.getStatus());
        }

        EscrowAccount escrow = escrowAccountRepository.findByLoanId(loanId)
                .orElseThrow(() -> new RuntimeException("Escrow account not found for loan: " + loanId));
        
        BigDecimal escrowBalanceBtc = new BigDecimal(escrow.getCurrentBalanceSats()).divide(new BigDecimal("100000000"), 8, RoundingMode.HALF_UP);
        
        BigDecimal finalReleaseAmount = escrowBalanceBtc; 
        
        // Create Release Record
        CollateralRelease release = CollateralRelease.builder()
                .loan(loan)
                .releasedBtc(finalReleaseAmount)
                .executedBy(adminId)
                .build();
        collateralReleaseRepository.save(release);

        // Update Loan
        loan.setStatus(LoanStatus.CLOSED);
        loan.setCollateralReleasedAt(LocalDateTime.now());
        loan.setCollateralReleasedBtc(finalReleaseAmount);
        
        // Update Escrow
        escrow.setCurrentBalanceSats(0L);
        escrowAccountRepository.save(escrow);

        // Ledger Entries
        createLedgerEntry(loan, LedgerEntryType.COLLATERAL_RELEASED, BigDecimal.ZERO, "Collateral Released: " + finalReleaseAmount + " BTC");
        createLedgerEntry(loan, LedgerEntryType.ESCROW_CLOSED, BigDecimal.ZERO, "Escrow Account Closed");
        
        // Update loan status finally
        loanRepository.save(loan);
        
        log.info("Collateral released for loan {}. Amount: {} BTC", loanId, finalReleaseAmount);
    }
    
    public BigDecimal getCollateralBalance(UUID loanId) {
         EscrowAccount escrow = escrowAccountRepository.findByLoanId(loanId)
                .orElseThrow(() -> new RuntimeException("Escrow account not found"));
         
         return new BigDecimal(escrow.getCurrentBalanceSats()).divide(new BigDecimal("100000000"), 8, RoundingMode.HALF_UP);
    }

    private void createLedgerEntry(Loan loan, LedgerEntryType type, BigDecimal amount, String description) {
         LoanLedger ledger = LoanLedger.builder()
            .loan(loan)
            .entryType(type)
            .amountInr(amount)
            .notes(description)
            .build();
        loanLedgerRepository.save(ledger);
    }
}
