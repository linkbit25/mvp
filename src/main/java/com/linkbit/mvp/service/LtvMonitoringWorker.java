package com.linkbit.mvp.service;

import com.linkbit.mvp.domain.ActorType;
import com.linkbit.mvp.domain.Loan;
import com.linkbit.mvp.domain.LoanAction;
import com.linkbit.mvp.domain.LoanLtvHistory;
import com.linkbit.mvp.domain.LoanMarginCall;
import com.linkbit.mvp.domain.LoanStatus;
import com.linkbit.mvp.domain.MarginCallStatus;
import com.linkbit.mvp.repository.LoanLtvHistoryRepository;
import com.linkbit.mvp.repository.LoanMarginCallRepository;
import com.linkbit.mvp.repository.LoanRepository;
import com.linkbit.mvp.service.BtcPriceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class LtvMonitoringWorker {

    private final LoanRepository loanRepository;
    private final LoanLtvHistoryRepository ltvHistoryRepository;
    private final LoanMarginCallRepository marginCallRepository;
    private final BtcPriceService btcPriceService;
    private final ChatService chatService;
    private final StateMachineService stateMachineService;

    @Scheduled(fixedRate = 1000)
    @Transactional
    public void monitorLtvLevels() {
        BigDecimal currentBtcPrice;
        try {
            currentBtcPrice = btcPriceService.getCurrentBtcPrice();
        } catch (Exception e) {
            log.error("Failed to fetch BTC price", e);
            return;
        }

        if (currentBtcPrice == null || currentBtcPrice.compareTo(BigDecimal.ZERO) <= 0) {
            return;
        }

        List<Loan> monitoredLoans = loanRepository.findByStatusIn(List.of(LoanStatus.ACTIVE, LoanStatus.MARGIN_CALL, LoanStatus.LIQUIDATION_ELIGIBLE));

        for (Loan loan : monitoredLoans) {
            processLoanLtv(loan, currentBtcPrice);
        }
    }

    private void processLoanLtv(Loan loan, BigDecimal currentBtcPrice) {
        if (loan.getCollateralBtcAmount() == null || loan.getCollateralBtcAmount().compareTo(BigDecimal.ZERO) == 0) {
            return;
        }

        BigDecimal collateralValueInr = loan.getCollateralBtcAmount().multiply(currentBtcPrice);
        BigDecimal outstanding = loan.getTotalOutstanding();
        if (outstanding == null) {
            outstanding = loan.getTotalRepaymentAmount();
        }
        if (outstanding == null) {
            outstanding = loan.getPrincipalAmount();
        }
        if (outstanding == null || outstanding.compareTo(BigDecimal.ZERO) == 0) {
            return;
        }

        BigDecimal ltvPercent = outstanding
                .divide(collateralValueInr, 4, RoundingMode.HALF_UP)
                .multiply(new BigDecimal("100"));

        loan.setCollateralValueInr(collateralValueInr);
        loan.setCurrentLtvPercent(ltvPercent);
        loan.setLastPriceUpdate(LocalDateTime.now());

        LoanStatus previousStatus = loan.getStatus();
        
        // Define thresholds
        BigDecimal liquidationThreshold = new BigDecimal(loan.getLiquidationLtvPercent() != null ? loan.getLiquidationLtvPercent() : 90);
        BigDecimal marginCallThreshold = new BigDecimal(loan.getMarginCallLtvPercent() != null ? loan.getMarginCallLtvPercent() : 75);

        LoanStatus newStatus;
        if (ltvPercent.compareTo(liquidationThreshold) >= 0) {
            newStatus = LoanStatus.LIQUIDATION_ELIGIBLE;
        } else if (ltvPercent.compareTo(marginCallThreshold) >= 0) {
            newStatus = LoanStatus.MARGIN_CALL;
        } else {
            newStatus = LoanStatus.ACTIVE;
        }

        if (newStatus != previousStatus) {
            handleStatusTransition(loan, previousStatus, newStatus, ltvPercent);
            if (newStatus == LoanStatus.LIQUIDATION_ELIGIBLE) {
                stateMachineService.transition(loan, LoanAction.LTV_DROP_LIQUIDATION, ActorType.SYSTEM);
            } else if (newStatus == LoanStatus.MARGIN_CALL) {
                stateMachineService.transition(loan, LoanAction.LTV_DROP_MARGIN_CALL, ActorType.SYSTEM);
            } else if (newStatus == LoanStatus.ACTIVE) {
                stateMachineService.transition(loan, LoanAction.LTV_RECOVERED, ActorType.SYSTEM);
            }
            
             LoanLtvHistory history = LoanLtvHistory.builder()
                .loan(loan)
                .btcPriceInr(currentBtcPrice)
                .collateralValueInr(collateralValueInr)
                .ltvPercent(ltvPercent)
                .build();
             ltvHistoryRepository.save(history);
        }
        
        loanRepository.save(loan);
    }

    private void handleStatusTransition(Loan loan, LoanStatus oldStatus, LoanStatus newStatus, BigDecimal ltvPercent) {
        log.info("Loan {} transitioning from {} to {} at LTV {}%", loan.getId(), oldStatus, newStatus, ltvPercent);
        
        // Notify user via chat
        chatService.sendSystemMessage(loan.getId(), 
            String.format("SYSTEM: Loan status changed to %s. Current LTV: %.2f%%", newStatus, ltvPercent));

        if (newStatus == LoanStatus.MARGIN_CALL && oldStatus == LoanStatus.ACTIVE) {
            // Trigger new margin call
            LoanMarginCall marginCall = LoanMarginCall.builder()
                    .loan(loan)
                    .triggeredAt(LocalDateTime.now())
                    .status(MarginCallStatus.OPEN)
                    .build();
            marginCallRepository.save(marginCall);
        } else if (newStatus == LoanStatus.LIQUIDATION_ELIGIBLE) {
             // Escalate existing open margin calls
             List<LoanMarginCall> openCalls = marginCallRepository.findByLoanAndStatusIn(loan, List.of(MarginCallStatus.OPEN));
             for (LoanMarginCall mc : openCalls) {
                 mc.setStatus(MarginCallStatus.ESCALATED);
                 marginCallRepository.save(mc);
             }
        } else if (newStatus == LoanStatus.ACTIVE && (oldStatus == LoanStatus.MARGIN_CALL || oldStatus == LoanStatus.LIQUIDATION_ELIGIBLE)) {
            // Resolve margin calls
            List<LoanMarginCall> activeCalls = marginCallRepository.findByLoanAndStatusIn(loan, List.of(MarginCallStatus.OPEN, MarginCallStatus.ESCALATED));
            for (LoanMarginCall mc : activeCalls) {
                mc.setStatus(MarginCallStatus.RESOLVED);
                mc.setResolvedAt(LocalDateTime.now());
                marginCallRepository.save(mc);
            }
        }
    }
}
