package com.linkbit.mvp.service;

import com.linkbit.mvp.domain.*;
import com.linkbit.mvp.repository.LoanAuditLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class StateMachineService {

    private final LoanAuditLogRepository auditLogRepository;
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
        ADMIN_ALLOWED_TRANSITIONS.put(LoanStatus.ACTIVE, EnumSet.of(LoanStatus.MARGIN_CALL, LoanStatus.LIQUIDATION_ELIGIBLE, LoanStatus.CLOSED));
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
        
        addTransition(LoanStatus.AWAITING_COLLATERAL, LoanAction.DEPOSIT_COLLATERAL, LoanStatus.COLLATERAL_LOCKED);
        
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
        
        // Collateral Release can happen from ACTIVE or REPAID based on current logic
        addTransition(LoanStatus.ACTIVE, LoanAction.RELEASE_COLLATERAL, LoanStatus.CLOSED);
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
                throw new IllegalStateException("Invariant violated: Collateral must exist for active loan states");
            }
        }

        log.info("Loan {}: {} -> {} via {} by {}", loan.getId(), current, next, action, actor);
        loan.setStatus(next);
        saveAuditLog(loan, current, next, action, actor);
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
                throw new IllegalStateException("Invariant violated: Collateral must exist for active loan states");
            }
        }

        log.info("Loan {}: {} -> {} via ADMIN_OVERRIDE", loan.getId(), current, targetStatus);
        loan.setStatus(targetStatus);
        saveAuditLog(loan, current, targetStatus, LoanAction.ADMIN_OVERRIDE, ActorType.ADMIN);
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
