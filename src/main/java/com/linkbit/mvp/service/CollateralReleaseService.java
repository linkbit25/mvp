package com.linkbit.mvp.service;

import com.linkbit.mvp.domain.CollateralRelease;
import com.linkbit.mvp.domain.EscrowAccount;
import com.linkbit.mvp.domain.LedgerEntryType;
import com.linkbit.mvp.domain.Loan;
import com.linkbit.mvp.domain.LoanLedger;
import com.linkbit.mvp.domain.LoanStatus;
import com.linkbit.mvp.domain.ActorType;
import com.linkbit.mvp.domain.LoanAction;
import com.linkbit.mvp.domain.User;
import com.linkbit.mvp.repository.CollateralReleaseRepository;
import com.linkbit.mvp.repository.EscrowAccountRepository;
import com.linkbit.mvp.repository.LoanLedgerRepository;
import com.linkbit.mvp.repository.LoanRepository;
import com.linkbit.mvp.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
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
    private final UserRepository userRepository;
    private final StateMachineService stateMachineService;

    @Transactional
    public void releaseCollateral(UUID loanId, String adminEmail) {
        User admin = userRepository.findByEmail(adminEmail)
                .orElseThrow(() -> new UsernameNotFoundException("User not found"));
        Loan loan = loanRepository.findById(loanId)
                .orElseThrow(() -> new IllegalArgumentException("Loan not found: " + loanId));

        if (loan.getStatus() != LoanStatus.REPAID) {
            if (loan.getStatus() == LoanStatus.CLOSED) return;
            throw new IllegalStateException("Collateral release denied. Loan status must be REPAID, but is " + loan.getStatus());
        }

        EscrowAccount escrow = escrowAccountRepository.findByLoanId(loanId)
                .orElseThrow(() -> new RuntimeException("Escrow account not found for loan: " + loanId));
        
        if (escrow.getCurrentBalanceSats() == null || escrow.getCurrentBalanceSats() == 0) {
            log.info("Collateral for loan {} already released or never deposited. Skipping.", loanId);
            return;
        }

        BigDecimal releaseAmount = toBtc(escrow.getCurrentBalanceSats());

        collateralReleaseRepository.save(CollateralRelease.builder()
                .loan(loan)
                .releasedBtc(releaseAmount)
                .executedBy(admin.getId())
                .build());

        stateMachineService.transition(loan, LoanAction.RELEASE_COLLATERAL, ActorType.SYSTEM);
        loan.setCollateralReleasedAt(LocalDateTime.now());
        loan.setCollateralReleasedBtc(releaseAmount);

        escrow.setCurrentBalanceSats(0L);
        escrowAccountRepository.save(escrow);

        createLedgerEntry(loan, LedgerEntryType.COLLATERAL_RELEASED, BigDecimal.ZERO, "Collateral Released: " + releaseAmount + " BTC");
        createLedgerEntry(loan, LedgerEntryType.ESCROW_CLOSED, BigDecimal.ZERO, "Escrow Account Closed");
        loanRepository.save(loan);

        log.info("Collateral released for loan {}. Amount: {} BTC", loanId, releaseAmount);
    }

    @Transactional(readOnly = true)
    public BigDecimal getCollateralBalance(UUID loanId, String email) {
        Loan loan = loanRepository.findById(loanId)
                .orElseThrow(() -> new RuntimeException("Loan not found"));
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new UsernameNotFoundException("User not found"));

        if (!loan.getBorrower().getId().equals(user.getId()) && !loan.getLender().getId().equals(user.getId())) {
            throw new org.springframework.security.access.AccessDeniedException("Only participants can view collateral balance");
        }

        return escrowAccountRepository.findByLoanId(loanId)
                .map(escrow -> toBtc(escrow.getCurrentBalanceSats()))
                .orElse(BigDecimal.ZERO.setScale(8, RoundingMode.HALF_UP));
    }

    private BigDecimal toBtc(long sats) {
        return new BigDecimal(sats).divide(new BigDecimal("100000000"), 8, RoundingMode.HALF_UP);
    }

    private void createLedgerEntry(Loan loan, LedgerEntryType type, BigDecimal amount, String description) {
        loanLedgerRepository.save(LoanLedger.builder()
                .loan(loan)
                .entryType(type)
                .amountInr(amount)
                .notes(description)
                .build());
    }
}
