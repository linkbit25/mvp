package com.linkbit.mvp.service;

import com.linkbit.mvp.domain.EmiStatus;
import com.linkbit.mvp.domain.LedgerEntryType;
import com.linkbit.mvp.domain.Loan;
import com.linkbit.mvp.domain.LoanEmi;
import com.linkbit.mvp.domain.LoanLedger;
import com.linkbit.mvp.domain.LoanRepayment;
import com.linkbit.mvp.domain.LoanStatus;
import com.linkbit.mvp.domain.ActorType;
import com.linkbit.mvp.domain.LoanAction;
import com.linkbit.mvp.domain.RepaymentStatus;
import com.linkbit.mvp.domain.RepaymentType;
import com.linkbit.mvp.domain.User;
import com.linkbit.mvp.dto.LedgerResponse;
import com.linkbit.mvp.dto.RepaymentRequest;
import com.linkbit.mvp.repository.LoanEmiRepository;
import com.linkbit.mvp.repository.LoanLedgerRepository;
import com.linkbit.mvp.repository.LoanRepaymentRepository;
import com.linkbit.mvp.repository.LoanRepository;
import com.linkbit.mvp.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class RepaymentService {

    private final LoanRepository loanRepository;
    private final LoanEmiRepository emiRepository;
    private final LoanRepaymentRepository repaymentRepository;
    private final LoanLedgerRepository ledgerRepository;
    private final UserRepository userRepository;
    private final ChatService chatService;
    private final StateMachineService stateMachineService;

    @Transactional
    public void initializeLoanFinancials(Loan loan) {
        if (loan.getTotalRepaymentAmount() == null) {
            BigDecimal principal = loan.getPrincipalAmount() != null ? loan.getPrincipalAmount() : BigDecimal.ZERO;
            BigDecimal rate = loan.getInterestRate() != null ? loan.getInterestRate() : BigDecimal.ZERO;
            int days = loan.getTenureDays() != null ? loan.getTenureDays() : 30;
            BigDecimal interest = principal.multiply(rate).multiply(BigDecimal.valueOf(days))
                    .divide(new BigDecimal("36500"), 2, RoundingMode.HALF_UP);
            loan.setTotalRepaymentAmount(principal.add(interest));
        }

        loan.setPrincipalOutstanding(loan.getPrincipalAmount());
        loan.setInterestOutstanding(loan.getTotalRepaymentAmount().subtract(loan.getPrincipalAmount()));
        loan.setTotalOutstanding(loan.getTotalRepaymentAmount());
        loanRepository.save(loan);

        createLedgerEntry(loan, LedgerEntryType.FIAT_DISBURSEMENT, loan.getPrincipalAmount(), "Loan Activated: Fiat Transferred");

        if (loan.getRepaymentType() == RepaymentType.EMI || (loan.getRepaymentType() == null && loan.getEmiCount() != null && loan.getEmiCount() > 1)) {
            generateEmiSchedule(loan);
        }
    }

    private void generateEmiSchedule(Loan loan) {
        int emiCount = loan.getEmiCount() != null && loan.getEmiCount() > 0 ? loan.getEmiCount() : 1;
        BigDecimal scheduledEmiAmount = loan.getEmiAmount() != null
                ? loan.getEmiAmount()
                : loan.getTotalRepaymentAmount().divide(BigDecimal.valueOf(emiCount), 2, RoundingMode.HALF_UP);

        BigDecimal remainingBalance = loan.getTotalRepaymentAmount();
        for (int i = 1; i <= emiCount; i++) {
            BigDecimal currentEmiAmount = (i == emiCount) ? remainingBalance : scheduledEmiAmount;
            remainingBalance = remainingBalance.subtract(currentEmiAmount);

            emiRepository.save(LoanEmi.builder()
                    .loan(loan)
                    .emiNumber(i)
                    .dueDate(LocalDate.now().plusDays(30L * i))
                    .emiAmount(currentEmiAmount)
                    .amountPaid(BigDecimal.ZERO)
                    .status(EmiStatus.PENDING)
                    .build());
        }
    }

    @Transactional
    public void submitRepayment(UUID loanId, String email, RepaymentRequest request) {
        Loan loan = getLoan(loanId);
        User user = getUserByEmail(email);

        if (!loan.getBorrower().getId().equals(user.getId())) {
            throw new RuntimeException("Unauthorized: Only borrower can submit a repayment");
        }
        if (loan.getStatus() != LoanStatus.ACTIVE) {
            throw new RuntimeException("Loan must be in ACTIVE status to submit repayments");
        }

        if (request.getAmount().compareTo(loan.getTotalOutstanding()) > 0) {
            throw new IllegalArgumentException("Repayment amount cannot exceed total outstanding balance of INR " + loan.getTotalOutstanding());
        }

        repaymentRepository.save(LoanRepayment.builder()
                .loan(loan)
                .amountInr(request.getAmount())
                .transactionReference(request.getTransactionReference())
                .proofUrl(request.getProofImageUrl())
                .status(RepaymentStatus.PENDING)
                .build());
        chatService.sendSystemMessage(loanId, "SYSTEM: Borrower submitted a repayment of INR " + request.getAmount() + ". Admin verification required.");
    }

    @Transactional
    public void verifyRepayment(UUID repaymentId) {
        LoanRepayment repayment = repaymentRepository.findById(repaymentId)
                .orElseThrow(() -> new RuntimeException("Repayment not found"));
        if (repayment.getStatus() != RepaymentStatus.PENDING) {
            throw new RuntimeException("Repayment is already verified or rejected");
        }

        Loan loan = repayment.getLoan();
        if (loan.getStatus() == LoanStatus.REPAID) return;
        
        repayment.setStatus(RepaymentStatus.VERIFIED);
        repaymentRepository.save(repayment);

        processRepaymentFinancials(loan, repayment.getAmountInr());
        createLedgerEntry(loan, LedgerEntryType.BORROWER_REPAYMENT, repayment.getAmountInr(), "Repayment Verified. Ref: " + repayment.getTransactionReference());
        chatService.sendSystemMessage(loan.getId(), "SYSTEM: Admin verified repayment of INR " + repayment.getAmountInr() + ". Outstanding balance updated.");

        if (loan.getTotalOutstanding().compareTo(BigDecimal.ZERO) == 0) {
            stateMachineService.transition(loan, LoanAction.REPAY_LOAN, ActorType.SYSTEM);
            loanRepository.save(loan);
            chatService.sendSystemMessage(loan.getId(), "SYSTEM: Loan is fully REPAID. Pending collateral release.");
        }
    }

    private void processRepaymentFinancials(Loan loan, BigDecimal repaymentAmount) {
        if (repaymentAmount.compareTo(loan.getTotalOutstanding()) > 0) {
            throw new IllegalArgumentException("Repayment exceeds outstanding balance");
        }

        BigDecimal newTotal = loan.getTotalOutstanding().subtract(repaymentAmount);
        loan.setTotalOutstanding(newTotal);

        BigDecimal currentInterest = loan.getInterestOutstanding();
        if (repaymentAmount.compareTo(currentInterest) >= 0) {
            loan.setInterestOutstanding(BigDecimal.ZERO);
            BigDecimal overflowToPrincipal = repaymentAmount.subtract(currentInterest);
            BigDecimal newPrincipal = loan.getPrincipalOutstanding().subtract(overflowToPrincipal);
            loan.setPrincipalOutstanding(newPrincipal.max(BigDecimal.ZERO));
        } else {
            loan.setInterestOutstanding(currentInterest.subtract(repaymentAmount));
        }
        loanRepository.save(loan);

        BigDecimal remainingToSettle = repaymentAmount;
        for (LoanEmi emi : emiRepository.findByLoanIdOrderByEmiNumberAsc(loan.getId()).stream()
                .filter(existing -> existing.getStatus() == EmiStatus.PENDING || existing.getStatus() == EmiStatus.PARTIAL || existing.getStatus() == EmiStatus.OVERDUE)
                .toList()) {
            if (remainingToSettle.compareTo(BigDecimal.ZERO) <= 0) {
                break;
            }

            BigDecimal emiRemaining = emi.getEmiAmount().subtract(emi.getAmountPaid());
            if (remainingToSettle.compareTo(emiRemaining) >= 0) {
                remainingToSettle = remainingToSettle.subtract(emiRemaining);
                emi.setAmountPaid(emi.getEmiAmount());
                emi.setStatus(EmiStatus.PAID);
            } else {
                emi.setAmountPaid(emi.getAmountPaid().add(remainingToSettle));
                if (emi.getStatus() == EmiStatus.PENDING) {
                    emi.setStatus(EmiStatus.PARTIAL);
                }
                remainingToSettle = BigDecimal.ZERO;
            }
            emiRepository.save(emi);
        }
    }

    @Transactional(readOnly = true)
    public List<LedgerResponse> getLoanLedger(UUID loanId, String email) {
        Loan loan = getLoan(loanId);
        User user = getUserByEmail(email);

        if (!loan.getBorrower().getId().equals(user.getId()) && !loan.getLender().getId().equals(user.getId())) {
            throw new RuntimeException("Unauthorized: Only participants can view the ledger");
        }

        return ledgerRepository.findByLoanIdOrderByCreatedAtAsc(loanId).stream()
                .map(ledger -> LedgerResponse.builder()
                        .type(ledger.getEntryType())
                        .amount(ledger.getAmountInr())
                        .build())
                .collect(Collectors.toList());
    }

    private void createLedgerEntry(Loan loan, LedgerEntryType type, BigDecimal amount, String notes) {
        ledgerRepository.save(LoanLedger.builder()
                .loan(loan)
                .entryType(type)
                .amountInr(amount)
                .notes(notes)
                .build());
    }

    private User getUserByEmail(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found"));
    }

    private Loan getLoan(UUID loanId) {
        return loanRepository.findById(loanId)
                .orElseThrow(() -> new RuntimeException("Loan not found"));
    }
}
