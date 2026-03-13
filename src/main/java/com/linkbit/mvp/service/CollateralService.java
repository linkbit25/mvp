package com.linkbit.mvp.service;

import com.linkbit.mvp.domain.*;
import com.linkbit.mvp.dto.TopUpCollateralRequest;
import com.linkbit.mvp.dto.VerifyTopUpRequest;
import com.linkbit.mvp.repository.*;
import com.linkbit.mvp.service.BtcPriceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.UUID;
import java.util.Optional;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class CollateralService {

    private final LoanRepository loanRepository;
    private final BitcoinTransactionRepository bitcoinTransactionRepository;
    private final EscrowAccountRepository escrowAccountRepository;
    private final LoanLedgerRepository loanLedgerRepository;
    private final BtcPriceService btcPriceService;
    private final LoanMarginCallRepository marginCallRepository;

    @Transactional
    public void requestCollateralTopUp(UUID loanId, TopUpCollateralRequest request) {
        Loan loan = loanRepository.findById(loanId)
                .orElseThrow(() -> new RuntimeException("Loan not found"));

        if (loan.getStatus() != LoanStatus.ACTIVE && loan.getStatus() != LoanStatus.MARGIN_CALL && loan.getStatus() != LoanStatus.LIQUIDATION_ELIGIBLE) {
             throw new IllegalStateException("Collateral top-up is not allowed in current loan status: " + loan.getStatus());
        }

        BigDecimal amountBtc = request.getAmount_btc();
        if (amountBtc == null || amountBtc.compareTo(BigDecimal.ZERO) <= 0) {
             throw new IllegalArgumentException("Amount must be positive");
        }

        long amountSats = amountBtc.multiply(new BigDecimal("100000000")).longValue();

        BitcoinTransaction transaction = BitcoinTransaction.builder()
                .loan(loan)
                .type(BitcoinTransactionType.COLLATERAL_TOPUP)
                .amountSats(amountSats)
                .status("PENDING_VERIFICATION")
                .confirmations(0)
                .build();

        bitcoinTransactionRepository.save(transaction);
    }

    @Transactional
    public void verifyTopUp(UUID loanId, VerifyTopUpRequest request) {
        Loan loan = loanRepository.findById(loanId)
                .orElseThrow(() -> new RuntimeException("Loan not found"));

        Optional<BitcoinTransaction> pendingTx = bitcoinTransactionRepository.findByLoanAndTypeAndStatus(loan, BitcoinTransactionType.COLLATERAL_TOPUP, "PENDING_VERIFICATION")
                .stream().filter(tx -> tx.getAmountSats() == (long)(request.getAmountBtc() * 100000000)).findFirst();
        
        BitcoinTransaction transaction = pendingTx.orElse(BitcoinTransaction.builder()
                .loan(loan)
                .type(BitcoinTransactionType.COLLATERAL_TOPUP)
                .amountSats((long)(request.getAmountBtc() * 100000000))
                .build());
        
        transaction.setTxHash(request.getTxHash());
        transaction.setStatus("VERIFIED");
        transaction.setConfirmations(1);
        bitcoinTransactionRepository.save(transaction);

        EscrowAccount escrow = escrowAccountRepository.findByLoanId(loan.getId())
                .orElseThrow(() -> new RuntimeException("Escrow account not found"));
        
        BigDecimal addedBtc = BigDecimal.valueOf(request.getAmountBtc());
        long addedSats = addedBtc.multiply(new BigDecimal("100000000")).longValue();
        
        // EscrowAccount uses Sats
        escrow.setCurrentBalanceSats(escrow.getCurrentBalanceSats() + addedSats);
        escrowAccountRepository.save(escrow);

        // Loan uses BTC amount for collateral tracking?
        // Loan entity has collateralBtcAmount as BigDecimal
        BigDecimal newCollateralAmount = loan.getCollateralBtcAmount().add(addedBtc);
        loan.setCollateralBtcAmount(newCollateralAmount);

        BigDecimal currentBtcPrice = btcPriceService.getCurrentBtcPrice();
        if (currentBtcPrice != null && currentBtcPrice.compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal collateralValueInr = newCollateralAmount.multiply(currentBtcPrice);
            loan.setCollateralValueInr(collateralValueInr);
            
            BigDecimal outstanding = loan.getTotalOutstanding();
            if (outstanding == null) outstanding = loan.getTotalRepaymentAmount();
            if (outstanding == null) outstanding = loan.getPrincipalAmount();

            if (outstanding != null && outstanding.compareTo(BigDecimal.ZERO) > 0) {
                BigDecimal ltvPercent = outstanding
                        .divide(collateralValueInr, 4, RoundingMode.HALF_UP)
                        .multiply(new BigDecimal("100"));
                loan.setCurrentLtvPercent(ltvPercent);
                
                BigDecimal marginCallThreshold = new BigDecimal(loan.getMarginCallLtvPercent() != null ? loan.getMarginCallLtvPercent() : 85);
                if (ltvPercent.compareTo(marginCallThreshold) < 0) {
                    if (loan.getStatus() == LoanStatus.MARGIN_CALL || loan.getStatus() == LoanStatus.LIQUIDATION_ELIGIBLE) {
                        loan.setStatus(LoanStatus.ACTIVE);
                        
                        List<LoanMarginCall> activeCalls = marginCallRepository.findByLoanAndStatusIn(loan, List.of(MarginCallStatus.OPEN, MarginCallStatus.ESCALATED));
                        for (LoanMarginCall mc : activeCalls) {
                            mc.setStatus(MarginCallStatus.RESOLVED);
                            mc.setResolvedAt(LocalDateTime.now());
                            marginCallRepository.save(mc);
                        }
                    }
                }
            }
        }
        
        loanRepository.save(loan);

        LoanLedger ledgerEntry = LoanLedger.builder()
                .loan(loan)
                .entryType(LedgerEntryType.COLLATERAL_TOPUP)
                .amountInr(BigDecimal.ZERO)
                .notes("Collateral Top-up Verified: " + request.getTxHash() + " Amount: " + addedBtc + " BTC")
                .createdAt(LocalDateTime.now())
                .build();
        loanLedgerRepository.save(ledgerEntry);
    }
}
