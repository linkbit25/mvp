package com.linkbit.mvp.service;

import com.linkbit.mvp.domain.Loan;
import com.linkbit.mvp.domain.LoanStatus;
import com.linkbit.mvp.domain.LoanAction;
import com.linkbit.mvp.domain.ActorType;
import com.linkbit.mvp.repository.LoanRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.math.BigDecimal;

@Slf4j
@Service
@RequiredArgsConstructor
public class LoanTimeoutWorker {

    private final LoanRepository loanRepository;
    private final StateMachineService stateMachineService;

    // Run every hour
    @Scheduled(fixedRate = 3600000)
    @Transactional
    public void handleStuckLoans() {
        log.info("Starting stuck loan timeout check...");

        // 1. AWAITING_FEE timeout (e.g., 24 hours)
        handleTimeout(LoanStatus.AWAITING_FEE, 24, LoanAction.TIMEOUT_CANCEL, "AWAITING_FEE");

        // 2. AWAITING_COLLATERAL timeout (e.g., 48 hours)
        handleTimeout(LoanStatus.AWAITING_COLLATERAL, 48, LoanAction.TIMEOUT_CANCEL, "AWAITING_COLLATERAL");

        // 3. DISPUTE_OPEN notification/escalation (e.g., 7 days)
        // For now, we just log it or we could transition to a specialized state if it existed.
        // handleTimeout(LoanStatus.DISPUTE_OPEN, 168, LoanAction.ESCALATE_DISPUTE, "DISPUTE_OPEN");
    }

    private void handleTimeout(LoanStatus status, int hours, LoanAction action, String stateName) {
        LocalDateTime threshold = LocalDateTime.now().minusHours(hours);
        List<Loan> stuckLoans = loanRepository.findByStatusAndUpdatedAtBefore(status, threshold);

        if (!stuckLoans.isEmpty()) {
            log.info("Processings {} loans stuck in {} for more than {} hours", stuckLoans.size(), stateName, hours);
            for (Loan loan : stuckLoans) {
                try {
                    UUID loanId = loan.getId();
                    // Business Logic Check: Were any funds already partially paid?
                    // For AWAITING_FEE/COLLATERAL, we check if the loan record or escrow has balance.
                    boolean hasPartialFunds = false;
                    if (loan.getCollateralBtcAmount() != null && loan.getCollateralBtcAmount().compareTo(BigDecimal.ZERO) > 0) {
                        hasPartialFunds = true;
                    }
                    
                    if (hasPartialFunds) {
                        log.warn("CRITICAL: Stuck loan {} has partial funds but has timed out. Transitioning to CANCELLED. MANUAL REFUND INVESTIGATION REQUIRED.", loanId);
                    }

                    stateMachineService.transition(loan, action, ActorType.SYSTEM);
                    log.info("Successfully timed out loan {} (status={}, action={}, borrower={})", 
                        loanId, stateName, action, loan.getBorrower().getId());

                } catch (Exception e) {
                    log.error("Failed to process timeout for loan {}: {}", loan.getId(), e.getMessage());
                }
            }
        }
    }
}
