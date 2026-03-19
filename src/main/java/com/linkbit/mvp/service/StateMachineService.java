package com.linkbit.mvp.service;

import com.linkbit.mvp.domain.*;
import com.linkbit.mvp.repository.LoanAuditLogRepository;
import com.linkbit.mvp.repository.LoanRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class StateMachineService {

    private final LoanAuditLogRepository auditLogRepository;
    private final LoanRepository loanRepository;
    private final BtcPriceService btcPriceService;
    private final Map<LoanStatus, Map<LoanAction, LoanStatus>> transitionMap = new EnumMap<>(LoanStatus.class);

    private static final Set<LoanStatus> TERMINAL_STATES = EnumSet.of(
            LoanStatus.CLOSED,
            LoanStatus.LIQUIDATED,
            LoanStatus.CANCELLED
    );

    private static final Map<LoanStatus, Set<LoanStatus>> ADMIN_ALLOWED_TRANSITIONS = new EnumMap<>(LoanStatus.class);

    static {
        ADMIN_ALLOWED_TRANSITIONS.put(LoanStatus.AWAITING_FEE, EnumSet.of(LoanStatus.CANCELLED));
        ADMIN_ALLOWED_TRANSITIONS.put(LoanStatus.AWAITING_COLLATERAL, EnumSet.of(LoanStatus.CANCELLED));
        ADMIN_ALLOWED_TRANSITIONS.put(LoanStatus.ACTIVE, EnumSet.of(LoanStatus.MARGIN_CALL, LoanStatus.LIQUIDATION_ELIGIBLE));
        ADMIN_ALLOWED_TRANSITIONS.put(LoanStatus.MARGIN_CALL, EnumSet.of(LoanStatus.ACTIVE, LoanStatus.LIQUIDATION_ELIGIBLE));
        ADMIN_ALLOWED_TRANSITIONS.put(LoanStatus.LIQUIDATION_ELIGIBLE, EnumSet.of(LoanStatus.ACTIVE));
    }

    @jakarta.annotation.PostConstruct
    public void init() {
        // Build valid transitions
        addTransition(LoanStatus.NEGOTIATING, LoanAction.FINALIZE_CONTRACT, LoanStatus.AWAITING_SIGNATURES);
        addTransition(LoanStatus.NEGOTIATING, LoanAction.CANCEL_NEGOTIATION, LoanStatus.CANCELLED);
        
        addTransition(LoanStatus.AWAITING_SIGNATURES, LoanAction.SIGN_CONTRACT, LoanStatus.AWAITING_FEE);
        
        addTransition(LoanStatus.AWAITING_FEE, LoanAction.PAY_FEE, LoanStatus.AWAITING_COLLATERAL);
        addTransition(LoanStatus.AWAITING_FEE, LoanAction.TIMEOUT_CANCEL, LoanStatus.CANCELLED);
        
        addTransition(LoanStatus.AWAITING_COLLATERAL, LoanAction.DEPOSIT_COLLATERAL, LoanStatus.COLLATERAL_LOCKED);
        addTransition(LoanStatus.AWAITING_COLLATERAL, LoanAction.TIMEOUT_CANCEL, LoanStatus.CANCELLED);
        
        addTransition(LoanStatus.COLLATERAL_LOCKED, LoanAction.DISBURSE_FIAT, LoanStatus.ACTIVE);
        addTransition(LoanStatus.COLLATERAL_LOCKED, LoanAction.MARK_DISPUTE, LoanStatus.DISPUTE_OPEN);
        // Dispute resolutions
        addTransition(LoanStatus.DISPUTE_OPEN, LoanAction.RESOLVE_DISPUTE, LoanStatus.ACTIVE);
        addTransition(LoanStatus.DISPUTE_OPEN, LoanAction.RELEASE_COLLATERAL, LoanStatus.CLOSED);
        
        addTransition(LoanStatus.ACTIVE, LoanAction.LTV_DROP_MARGIN_CALL, LoanStatus.MARGIN_CALL);
        addTransition(LoanStatus.ACTIVE, LoanAction.LTV_DROP_LIQUIDATION, LoanStatus.LIQUIDATION_ELIGIBLE);
        addTransition(LoanStatus.ACTIVE, LoanAction.REPAY_LOAN, LoanStatus.REPAID); // Repaid acts as Settlement Pending
        
        addTransition(LoanStatus.MARGIN_CALL, LoanAction.LTV_RECOVERED, LoanStatus.ACTIVE);
        addTransition(LoanStatus.MARGIN_CALL, LoanAction.LTV_DROP_LIQUIDATION, LoanStatus.LIQUIDATION_ELIGIBLE);
        
        addTransition(LoanStatus.LIQUIDATION_ELIGIBLE, LoanAction.LTV_RECOVERED, LoanStatus.ACTIVE);
        addTransition(LoanStatus.LIQUIDATION_ELIGIBLE, LoanAction.EXECUTE_LIQUIDATION, LoanStatus.LIQUIDATED);
        
        // Collateral Release can only happen from REPAID state
        addTransition(LoanStatus.REPAID, LoanAction.RELEASE_COLLATERAL, LoanStatus.CLOSED);
    }

    private void addTransition(LoanStatus from, LoanAction action, LoanStatus to) {
        transitionMap.computeIfAbsent(from, k -> new EnumMap<>(LoanAction.class)).put(action, to);
    }

    public void transition(Loan loan, LoanAction action, ActorType actor) {
        LoanStatus current = loan.getStatus();

        // Idempotency check: If the loan's current state is already the target of this action, skip.
        if (isAlreadyInTargetState(action, current)) {
            log.info("Idempotent retry detected for loan {} and action {}. Already in target state {}.", loan.getId(), action, current);
            return;
        }

        if (TERMINAL_STATES.contains(current)) {
            throw new IllegalStateException("No transitions allowed from terminal state: " + current);
        }

        Map<LoanAction, LoanStatus> possibleTransitions = transitionMap.get(current);
        
        if (possibleTransitions == null || !possibleTransitions.containsKey(action)) {
            throw new IllegalStateException(String.format("Invalid transition: Cannot perform %s from %s", action, current));
        }

        LoanStatus next = possibleTransitions.get(action);

        // Double check: if current == next despite not being caught by isAlreadyInTargetState (shouldn't happen with the logic below)
        if (current == next) {
            return;
        }

        if (next == LoanStatus.ACTIVE || next == LoanStatus.MARGIN_CALL || next == LoanStatus.LIQUIDATION_ELIGIBLE) {
            if (loan.getCollateralBtcAmount() == null || loan.getCollateralBtcAmount().compareTo(BigDecimal.ZERO) <= 0) {
                throw new IllegalStateException("Invariant violated: ACTIVE loans must have collateral balance > 0");
            }
        }

        // Invariant 1: No fiat disbursement before collateral locked
        if (action == LoanAction.DISBURSE_FIAT && current != LoanStatus.COLLATERAL_LOCKED && current != LoanStatus.DISPUTE_OPEN) {
            throw new IllegalStateException("Invariant violated: No fiat disbursement before collateral locked");
        }

        // Invariant 2: No loan closure without full repayment (principal + interest)
        if (next == LoanStatus.CLOSED) {
            BigDecimal outstanding = loan.getTotalOutstanding();
            if (outstanding == null || outstanding.compareTo(BigDecimal.ZERO) > 0) {
                throw new IllegalStateException("CRITICAL INVARIANT VIOLATION: Cannot CLOSE loan " + loan.getId() + " with outstanding balance: " + outstanding);
            }
        }
        
        // Invariant 4: Active loans must have collateral
        if (next == LoanStatus.ACTIVE || next == LoanStatus.MARGIN_CALL || next == LoanStatus.LIQUIDATION_ELIGIBLE) {
            if (loan.getCollateralBtcAmount() == null || loan.getCollateralBtcAmount().compareTo(BigDecimal.ZERO) <= 0) {
                throw new IllegalStateException("CRITICAL INVARIANT VIOLATION: Loan " + loan.getId() + " cannot be ACTIVE without collateral.");
            }
        }

        // Invariant 3: No liquidation without eligibility
        if (action == LoanAction.LTV_DROP_MARGIN_CALL || action == LoanAction.LTV_DROP_LIQUIDATION) {
            BigDecimal btcPrice = btcPriceService.getCurrentBtcPrice();
            if (btcPrice == null || btcPrice.compareTo(BigDecimal.ZERO) <= 0) {
                log.warn("Price fetch failed during state transition for loan {}", loan.getId());
            } else {
                BigDecimal collateralValueInr = loan.getCollateralBtcAmount().multiply(btcPrice);
                BigDecimal outstanding = loan.getTotalOutstanding();
                if (outstanding == null) outstanding = loan.getPrincipalAmount();

                BigDecimal ltvPercent = outstanding
                        .divide(collateralValueInr, 4, RoundingMode.HALF_UP)
                        .multiply(new BigDecimal("100"));

                int threshold = (action == LoanAction.LTV_DROP_MARGIN_CALL) ? 
                        (loan.getMarginCallLtvPercent() != null ? loan.getMarginCallLtvPercent() : 80) :
                        (loan.getLiquidationLtvPercent() != null ? loan.getLiquidationLtvPercent() : 95);

                if (ltvPercent.compareTo(new BigDecimal(threshold)) < 0) {
                    throw new IllegalStateException(String.format("Invariant violated: LTV %.2f%% is below threshold %d%% for action %s", 
                            ltvPercent, threshold, action));
                }
            }
        }

        log.info("Loan {}: {} -> {} via {} by {}", loan.getId(), current, next, action, actor);
        loan.setStatus(next);
        saveAuditLog(loan, current, next, action, actor);
        loanRepository.save(loan);
    }

    @Transactional
    public void adminTransition(Loan loan, LoanStatus targetStatus) {
        LoanStatus current = loan.getStatus();
        
        if (current == targetStatus) {
            log.info("Idempotent admin retry: Loan {} already in state {}.", loan.getId(), targetStatus);
            return;
        }

        if (TERMINAL_STATES.contains(current)) {
            throw new IllegalStateException("Cannot override terminal state: " + current);
        }

        Set<LoanStatus> allowedTargets = ADMIN_ALLOWED_TRANSITIONS.get(current);

        if (allowedTargets == null || !allowedTargets.contains(targetStatus)) {
            throw new IllegalStateException(
                "Invalid admin transition from " + current + " to " + targetStatus
            );
        }

        if (targetStatus == LoanStatus.ACTIVE || targetStatus == LoanStatus.MARGIN_CALL || targetStatus == LoanStatus.LIQUIDATION_ELIGIBLE) {
            if (loan.getCollateralBtcAmount() == null || loan.getCollateralBtcAmount().compareTo(BigDecimal.ZERO) <= 0) {
                throw new IllegalStateException("Invariant violated: ACTIVE loans must have collateral balance > 0");
            }
        }

        // Invariant 2: No loan closure without full repayment
        if (targetStatus == LoanStatus.CLOSED) {
            BigDecimal outstanding = loan.getTotalOutstanding();
            if (outstanding == null) {
                throw new IllegalStateException("Invariant violated: Total outstanding balance is missing for loan " + loan.getId());
            }
            if (outstanding.compareTo(BigDecimal.ZERO) > 0) {
                throw new IllegalStateException("Invariant violated: No loan closure while balance is outstanding: " + outstanding);
            }
        }

        log.info("Loan {}: {} -> {} via ADMIN_OVERRIDE", loan.getId(), current, targetStatus);
        loan.setStatus(targetStatus);
        saveAuditLog(loan, current, targetStatus, LoanAction.ADMIN_OVERRIDE, ActorType.ADMIN);
        loanRepository.save(loan);
    }

    private void saveAuditLog(Loan loan, LoanStatus previous, LoanStatus next, LoanAction action, ActorType actor) {
        auditLogRepository.save(LoanAuditLog.builder()
                .loanId(loan.getId())
                .previousState(previous)
                .newState(next)
                .action(action)
                .actor(actor)
                .build());
    }

    private boolean isAlreadyInTargetState(LoanAction action, LoanStatus currentStatus) {
        return transitionMap.values().stream()
                .anyMatch(m -> m.get(action) == currentStatus);
    }
}
