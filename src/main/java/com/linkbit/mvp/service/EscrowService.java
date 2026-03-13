package com.linkbit.mvp.service;

import com.linkbit.mvp.domain.*;
import com.linkbit.mvp.dto.EscrowResponse;
import com.linkbit.mvp.repository.BitcoinTransactionRepository;
import com.linkbit.mvp.repository.EscrowAccountRepository;
import com.linkbit.mvp.repository.LoanMarginCallRepository;
import com.linkbit.mvp.repository.LoanRepository;
import com.linkbit.mvp.repository.UserRepository;
import com.linkbit.mvp.service.BtcPriceService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class EscrowService {

    private final LoanRepository loanRepository;
    private final EscrowAccountRepository escrowAccountRepository;
    private final BitcoinTransactionRepository bitcoinTransactionRepository;
    private final LoanMarginCallRepository marginCallRepository;
    private final UserRepository userRepository;
    private final BtcPriceService btcPriceService;
    private final ChatService chatService;

    @Transactional
    public EscrowResponse generateAddress(String email, UUID loanId) {
        User borrower = userRepository.findByEmail(email)
                .orElseThrow(() -> new UsernameNotFoundException("User not found"));

        Loan loan = getLoan(loanId);

        validateBorrower(loan, borrower);

        if (loan.getStatus() != LoanStatus.AWAITING_COLLATERAL) {
            throw new RuntimeException("Loan is not awaiting collateral");
        }

        if (escrowAccountRepository.existsById(loanId)) {
            EscrowAccount existingEscrow = escrowAccountRepository.findById(loanId).orElseThrow();
            return mapToResponse(existingEscrow);
        }

        String mockAddress = "bc1qmockaddress" + UUID.randomUUID().toString().substring(0, 10);

        EscrowAccount escrowAccount = new EscrowAccount();
        escrowAccount.setLoanId(loan.getId());
        escrowAccount.setLoan(loan);
        escrowAccount.setEscrowAddress(mockAddress);
        escrowAccount.setCurrentBalanceSats(0L);

        escrowAccountRepository.insertEscrowAccount(loan.getId(), mockAddress, 0L);

        escrowAccount = escrowAccountRepository.findById(loan.getId()).orElseThrow(() -> new RuntimeException("Escrow account mapping failed across native insert"));

        chatService.sendSystemMessage(loanId, "SYSTEM: Mock Bitcoin Escrow address generated: " + mockAddress);

        return mapToResponse(escrowAccount);
    }

    @Transactional
    public void deposit(String email, UUID loanId, BigDecimal amountBtc) {
        User borrower = userRepository.findByEmail(email)
                .orElseThrow(() -> new UsernameNotFoundException("User not found"));

        Loan loan = getLoan(loanId);
        validateBorrower(loan, borrower);

        if (loan.getStatus() != LoanStatus.AWAITING_COLLATERAL && loan.getStatus() != LoanStatus.COLLATERAL_LOCKED) {
            throw new RuntimeException("Loan cannot accept deposits in this state");
        }

        if (!escrowAccountRepository.existsById(loanId)) {
            throw new RuntimeException("Escrow account not generated for this loan yet");
        }

        // Convert BTC to Sats (1 BTC = 100,000,000 Sats)
        long sats = amountBtc.multiply(new BigDecimal("100000000")).longValue();
        String fakeTxHash = "txmock" + UUID.randomUUID().toString().substring(0, 12);

        BitcoinTransaction tx = BitcoinTransaction.builder()
                .txHash(fakeTxHash)
                .loan(loan)
                .type(BitcoinTransactionType.DEPOSIT)
                .amountSats(sats)
                .confirmations(0)
                .build();

        bitcoinTransactionRepository.save(tx);
        
        chatService.sendSystemMessage(loanId, "SYSTEM: Deposit detected (" + amountBtc + " BTC). Waiting for admin verification.");
    }

    @Transactional
    public void verifyDeposit(UUID loanId) {
        Loan loan = getLoan(loanId);

        EscrowAccount escrowAccount = escrowAccountRepository.findById(loanId)
                .orElseThrow(() -> new RuntimeException("Escrow Account missing"));

        List<BitcoinTransaction> pendingTransactions = bitcoinTransactionRepository.findByLoanId(loanId).stream()
                .filter(tx -> tx.getConfirmations() == 0 && tx.getType() == BitcoinTransactionType.DEPOSIT)
                .toList();

        if (pendingTransactions.isEmpty()) {
            throw new RuntimeException("No pending deposits found to verify");
        }

        long newlyAddedSats = processPendingTransactions(pendingTransactions);

        long totalSatsNow = escrowAccount.getCurrentBalanceSats() + newlyAddedSats;
        escrowAccount.setCurrentBalanceSats(totalSatsNow);
        escrowAccountRepository.save(escrowAccount);

        if (loan.getStatus() == LoanStatus.AWAITING_COLLATERAL) {
            boolean satisfied = validateCollateralAmount(loan, totalSatsNow);
            if (satisfied) {
                loan.setStatus(LoanStatus.COLLATERAL_LOCKED);
                
                // Also initialize collateral_btc_amount to accurate sizing
                BigDecimal sumBtc = new BigDecimal(totalSatsNow).divide(new BigDecimal("100000000"), 10, RoundingMode.HALF_UP);
                loan.setCollateralBtcAmount(sumBtc);
                
                loanRepository.save(loan);
                chatService.sendSystemMessage(loanId, "SYSTEM: Collateral Verified! LTV satisfied. Loan status is now COLLATERAL_LOCKED.");
            } else {
                chatService.sendSystemMessage(loanId, "SYSTEM: Partial verification complete. More collateral required to satisfy LTV.");
            }
        } else {
            // Already locked, just topup
            chatService.sendSystemMessage(loanId, "SYSTEM: Deposit verified. Total collateral augmented.");
        }
    }

    @Transactional
    public void submitTopup(String email, UUID loanId, BigDecimal amountBtc) {
        User borrower = userRepository.findByEmail(email)
                .orElseThrow(() -> new UsernameNotFoundException("User not found"));

        Loan loan = getLoan(loanId);
        validateBorrower(loan, borrower);

        if (loan.getStatus() != LoanStatus.MARGIN_CALL && loan.getStatus() != LoanStatus.LIQUIDATION_ELIGIBLE) {
            throw new RuntimeException("Loan is not in a margin call state to receive top-ups.");
        }

        if (!escrowAccountRepository.existsById(loanId)) {
            throw new RuntimeException("Escrow account not generated for this loan yet");
        }

        long sats = amountBtc.multiply(new BigDecimal("100000000")).longValue();
        String fakeTxHash = "txmock" + UUID.randomUUID().toString().substring(0, 12);

        BitcoinTransaction tx = BitcoinTransaction.builder()
                .txHash(fakeTxHash)
                .loan(loan)
                .type(BitcoinTransactionType.COLLATERAL_TOPUP)
                .amountSats(sats)
                .confirmations(0)
                .build();

        bitcoinTransactionRepository.save(tx);
        chatService.sendSystemMessage(loanId, "SYSTEM: Collateral Top-Up detected (" + amountBtc + " BTC). Waiting for admin verification.");
    }

    @Transactional
    public void verifyTopup(UUID loanId) {
        Loan loan = getLoan(loanId);

        EscrowAccount escrowAccount = escrowAccountRepository.findById(loanId)
                .orElseThrow(() -> new RuntimeException("Escrow Account missing"));

        List<BitcoinTransaction> pendingTransactions = bitcoinTransactionRepository.findByLoanId(loanId).stream()
                .filter(tx -> tx.getConfirmations() == 0 && tx.getType() == BitcoinTransactionType.COLLATERAL_TOPUP)
                .toList();

        if (pendingTransactions.isEmpty()) {
            throw new RuntimeException("No pending top-ups found to verify");
        }

        long newlyAddedSats = processPendingTransactions(pendingTransactions);

        long totalSatsNow = escrowAccount.getCurrentBalanceSats() + newlyAddedSats;
        escrowAccount.setCurrentBalanceSats(totalSatsNow);
        escrowAccountRepository.save(escrowAccount);

        BigDecimal currentBtcPrice = btcPriceService.getCurrentBtcPrice();
        BigDecimal newBtcAmount = new BigDecimal(totalSatsNow).divide(new BigDecimal("100000000"), 10, RoundingMode.HALF_UP);
        BigDecimal collateralValueInr = newBtcAmount.multiply(currentBtcPrice);
        
        loan.setCollateralBtcAmount(newBtcAmount);
        loan.setCollateralValueInr(collateralValueInr);
        
        BigDecimal currentLtvPercent = loan.getTotalOutstanding()
                .divide(collateralValueInr, 4, RoundingMode.HALF_UP)
                .multiply(new BigDecimal("100"));
        
        loan.setCurrentLtvPercent(currentLtvPercent);
        
        int marginThreshold = loan.getMarginCallLtvPercent() != null ? loan.getMarginCallLtvPercent() : 85;

        if (currentLtvPercent.compareTo(new BigDecimal(marginThreshold)) < 0) {
            loan.setStatus(LoanStatus.ACTIVE);
            chatService.sendSystemMessage(loanId, String.format("SYSTEM: Top-up verified. LTV reduced to %.2f%%. Margin call resolved, loan is now ACTIVE.", currentLtvPercent));
            
            // Resolve active margin calls
            List<LoanMarginCall> activeCalls = marginCallRepository.findByLoanAndStatusIn(loan, List.of(MarginCallStatus.OPEN, MarginCallStatus.ESCALATED));
            for (LoanMarginCall mc : activeCalls) {
                mc.setStatus(MarginCallStatus.RESOLVED);
                mc.setResolvedAt(LocalDateTime.now());
                marginCallRepository.save(mc);
            }
        } else {
            chatService.sendSystemMessage(loanId, String.format("SYSTEM: Top-up verified. Collateral augmented, but LTV (%.2f%%) still exceeds safety thresholds.", currentLtvPercent));
        }

        loanRepository.save(loan);
    }

    private long processPendingTransactions(List<BitcoinTransaction> pendingTransactions) {
        long newlyAddedSats = 0;
        for (BitcoinTransaction tx : pendingTransactions) {
            tx.setConfirmations(1);
            newlyAddedSats += tx.getAmountSats();
            bitcoinTransactionRepository.save(tx);
        }
        return newlyAddedSats;
    }

    private boolean validateCollateralAmount(Loan loan, long currentSats) {
        BigDecimal btcPriceInr = btcPriceService.getCurrentBtcPrice();
        BigDecimal expectedLtv = new BigDecimal(loan.getExpectedLtvPercent()).divide(new BigDecimal("100"), 4, RoundingMode.HALF_UP);
        
        // Target Fiat Value needed = Principal / expected LTV
        BigDecimal requiredCollateralFiat = loan.getPrincipalAmount().divide(expectedLtv, 2, RoundingMode.HALF_UP);

        // Required BTC = required Fiat / BTC Price
        BigDecimal requiredBtc = requiredCollateralFiat.divide(btcPriceInr, 10, RoundingMode.HALF_UP);
        
        // Current BTC = sats / 100,000,000
        BigDecimal currentBtc = new BigDecimal(currentSats).divide(new BigDecimal("100000000"), 10, RoundingMode.HALF_UP);

        // Required sats
        return currentBtc.compareTo(requiredBtc) >= 0;
    }

    private Loan getLoan(UUID loanId) {
        return loanRepository.findById(loanId)
                .orElseThrow(() -> new RuntimeException("Loan not found"));
    }

    private void validateBorrower(Loan loan, User user) {
        if (!loan.getBorrower().getId().equals(user.getId())) {
            throw new RuntimeException("Unauthorized: Only borrower can perform this action");
        }
    }

    private EscrowResponse mapToResponse(EscrowAccount account) {
        return EscrowResponse.builder()
                .loanId(account.getLoanId())
                .escrowAddress(account.getEscrowAddress())
                .currentBalanceSats(account.getCurrentBalanceSats())
                .build();
    }
}
