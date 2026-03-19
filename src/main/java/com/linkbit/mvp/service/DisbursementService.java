package com.linkbit.mvp.service;

import com.linkbit.mvp.domain.EscrowAccount;
import com.linkbit.mvp.domain.Loan;
import com.linkbit.mvp.domain.LoanStatus;
import com.linkbit.mvp.domain.ActorType;
import com.linkbit.mvp.domain.LoanAction;
import com.linkbit.mvp.domain.User;
import com.linkbit.mvp.domain.UserKycDetails;
import com.linkbit.mvp.dto.DisbursementRequest;
import com.linkbit.mvp.dto.PaymentDetailsResponse;
import com.linkbit.mvp.repository.EscrowAccountRepository;
import com.linkbit.mvp.repository.LoanRepository;
import com.linkbit.mvp.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class DisbursementService {

    private final LoanRepository loanRepository;
    private final UserRepository userRepository;
    private final EscrowAccountRepository escrowAccountRepository;
    private final ChatService chatService;
    private final RepaymentService repaymentService;
    private final StateMachineService stateMachineService;

    @Transactional(readOnly = true)
    public PaymentDetailsResponse getPaymentDetails(UUID loanId, String email) {
        User user = getUserByEmail(email);
        Loan loan = getLoan(loanId);

        if (!loan.getLender().getId().equals(user.getId())) {
            throw new RuntimeException("Unauthorized: Only lender can view payment details");
        }

        if (loan.getStatus() != LoanStatus.COLLATERAL_LOCKED) {
            throw new RuntimeException("Payment details are only available when collateral is locked");
        }

        UserKycDetails borrowerKyc = loan.getBorrower().getKycDetails();
        if (borrowerKyc == null) {
            throw new RuntimeException("Borrower KYC details not found");
        }

        return PaymentDetailsResponse.builder()
                .accountNumber(borrowerKyc.getBankAccountNumber())
                .ifsc(borrowerKyc.getIfsc())
                .upiId(borrowerKyc.getUpiId())
                .build();
    }

    @Transactional
    public void markDisbursed(UUID loanId, String email, DisbursementRequest request) {
        User user = getUserByEmail(email);
        Loan loan = getLoan(loanId);

        if (!loan.getLender().getId().equals(user.getId())) {
            throw new RuntimeException("Unauthorized: Only lender can mark disbursement");
        }

        if (loan.getStatus() != LoanStatus.COLLATERAL_LOCKED) {
            throw new RuntimeException("Loan must have collateral locked before disbursement");
        }

        loan.setFiatDisbursedAt(LocalDateTime.now());
        loan.setDisbursementReference(request.getTransactionReference());
        loan.setDisbursementProofUrl(request.getProofImageUrl());
        loanRepository.save(loan);

        chatService.sendSystemMessage(loanId, "SYSTEM: Lender has marked fiat as disbursed. Reference: " + request.getTransactionReference());
    }

    @Transactional
    public void confirmReceipt(UUID loanId, String email) {
        User user = getUserByEmail(email);
        Loan loan = getLoan(loanId);

        if (!loan.getBorrower().getId().equals(user.getId())) {
            throw new RuntimeException("Unauthorized: Only borrower can confirm receipt");
        }

        if (loan.getStatus() != LoanStatus.COLLATERAL_LOCKED) {
            if (loan.getStatus() == LoanStatus.ACTIVE) return; // Prevent double activation
            throw new RuntimeException("Loan must be in COLLATERAL_LOCKED state to confirm receipt");
        }

        if (loan.getFiatDisbursedAt() == null) {
            throw new RuntimeException("Cannot confirm receipt before lender marks as disbursed");
        }

        loan.setFiatReceivedConfirmedAt(LocalDateTime.now());
        stateMachineService.transition(loan, LoanAction.DISBURSE_FIAT, ActorType.SYSTEM);
        loanRepository.save(loan);

        chatService.sendSystemMessage(loanId, "SYSTEM: Borrower confirmed receipt of fiat. Loan is now ACTIVE.");
        
        repaymentService.initializeLoanFinancials(loan);
    }

    @Transactional
    public void cancelDisbursement(UUID loanId, String email) {
        User user = getUserByEmail(email);
        Loan loan = getLoan(loanId);

        if (!loan.getLender().getId().equals(user.getId())) {
            throw new RuntimeException("Unauthorized: Only lender can cancel disbursement");
        }

        if (loan.getStatus() != LoanStatus.COLLATERAL_LOCKED) {
            throw new RuntimeException("Loan must be in COLLATERAL_LOCKED state");
        }

        if (loan.getFiatReceivedConfirmedAt() != null) {
            throw new RuntimeException("Cannot cancel after borrower has confirmed receipt");
        }

        loan.setFiatDisbursedAt(null);
        loan.setDisbursementReference(null);
        loan.setDisbursementProofUrl(null);
        loanRepository.save(loan);

        chatService.sendSystemMessage(loanId, "SYSTEM: Lender canceled previous disbursement notification.");
    }

    @Transactional
    public void openDispute(UUID loanId, String email) {
        User user = getUserByEmail(email);
        Loan loan = getLoan(loanId);

        if (!loan.getBorrower().getId().equals(user.getId())) {
            throw new RuntimeException("Unauthorized: Only borrower can open a dispute");
        }

        if (loan.getStatus() != LoanStatus.COLLATERAL_LOCKED) {
            throw new RuntimeException("Loan must be in COLLATERAL_LOCKED state to open a dispute");
        }

        if (loan.getFiatDisbursedAt() == null) {
            throw new RuntimeException("Cannot dispute before lender marks as disbursed");
        }

        stateMachineService.transition(loan, LoanAction.MARK_DISPUTE, ActorType.SYSTEM);
        loanRepository.save(loan);

        chatService.sendSystemMessage(loanId, "SYSTEM: Borrower opened a dispute claiming no fiat was received. Admin intervention required.");
    }

    @Transactional
    public void activateLoanAdmin(UUID loanId) {
        Loan loan = getLoan(loanId);

        if (loan.getStatus() != LoanStatus.DISPUTE_OPEN) {
            if (loan.getStatus() == LoanStatus.ACTIVE) return;
            throw new RuntimeException("Loan must be in DISPUTE_OPEN state for this admin action");
        }

        stateMachineService.transition(loan, LoanAction.RESOLVE_DISPUTE, ActorType.SYSTEM);
        loanRepository.save(loan);

        chatService.sendSystemMessage(loanId, "SYSTEM: Admin resolved dispute in favor of lender (proof valid). Loan is now ACTIVE.");
        
        repaymentService.initializeLoanFinancials(loan);
    }

    @Transactional
    public void refundCollateralAdmin(UUID loanId) {
        Loan loan = getLoan(loanId);

        if (loan.getStatus() != LoanStatus.DISPUTE_OPEN) {
            if (loan.getStatus() == LoanStatus.CLOSED) return;
            throw new RuntimeException("Loan must be in DISPUTE_OPEN state for this admin action");
        }

        EscrowAccount escrow = escrowAccountRepository.findById(loanId)
                .orElse(null);

        if (escrow != null && escrow.getCurrentBalanceSats() > 0) {
            escrow.setCurrentBalanceSats(0L);
            escrowAccountRepository.save(escrow);
        }

        stateMachineService.transition(loan, LoanAction.RELEASE_COLLATERAL, ActorType.SYSTEM);
        loanRepository.save(loan);

        chatService.sendSystemMessage(loanId, "SYSTEM: Admin resolved dispute in favor of borrower. Collateral marked for refund. Loan is now CLOSED.");
    }

    private User getUserByEmail(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new UsernameNotFoundException("User not found"));
    }

    private Loan getLoan(UUID loanId) {
        return loanRepository.findById(loanId)
                .orElseThrow(() -> new RuntimeException("Loan not found"));
    }
}
