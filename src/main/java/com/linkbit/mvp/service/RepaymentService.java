package com.linkbit.mvp.service;

import com.linkbit.mvp.domain.*;
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

        if (loan.getOffer().getTenureDays() > 0) {
            generateEmiSchedule(loan);
        }
    }

    private void generateEmiSchedule(Loan loan) {
        int emiCount = loan.getOffer().getTenureDays() / 30;
        if (emiCount == 0) emiCount = 1; // Treat <30 days as 1 bullet / single EMI at end

        BigDecimal emiAmount = loan.getTotalRepaymentAmount()
                .divide(BigDecimal.valueOf(emiCount), 2, RoundingMode.HALF_UP);

        BigDecimal remainingBalance = loan.getTotalRepaymentAmount();

        for (int i = 1; i <= emiCount; i++) {
            BigDecimal currentEmiAmount = (i == emiCount) ? remainingBalance : emiAmount;
            remainingBalance = remainingBalance.subtract(currentEmiAmount);

            LoanEmi emi = LoanEmi.builder()
                    .loan(loan)
                    .emiNumber(i)
                    .dueDate(LocalDate.now().plusDays(30L * i))
                    .emiAmount(currentEmiAmount)
                    .amountPaid(BigDecimal.ZERO)
                    .status(EmiStatus.PENDING)
                    .build();
            emiRepository.save(emi);
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

        LoanRepayment repayment = LoanRepayment.builder()
                .loan(loan)
                .amountInr(request.getAmount())
                .transactionReference(request.getTransactionReference())
                .proofUrl(request.getProofImageUrl())
                .status(RepaymentStatus.PENDING)
                .build();

        repaymentRepository.save(repayment);
        chatService.sendSystemMessage(loanId, "SYSTEM: Borrower submitted a repayment of ₹" + request.getAmount() + ". Admin verification required.");
    }

    @Transactional
    public void verifyRepayment(UUID repaymentId) {
        LoanRepayment repayment = repaymentRepository.findById(repaymentId)
                .orElseThrow(() -> new RuntimeException("Repayment not found"));

        if (repayment.getStatus() != RepaymentStatus.PENDING) {
            throw new RuntimeException("Repayment is already verified or rejected");
        }

        Loan loan = repayment.getLoan();

        repayment.setStatus(RepaymentStatus.VERIFIED);
        repaymentRepository.save(repayment);

        processRepaymentFinancials(loan, repayment.getAmountInr());
        createLedgerEntry(loan, LedgerEntryType.BORROWER_REPAYMENT, repayment.getAmountInr(), "Repayment Verified. Ref: " + repayment.getTransactionReference());

        chatService.sendSystemMessage(loan.getId(), "SYSTEM: Admin verified repayment of ₹" + repayment.getAmountInr() + ". Outstanding balance updated.");

        if (loan.getTotalOutstanding().compareTo(BigDecimal.ZERO) <= 0) {
            loan.setStatus(LoanStatus.REPAID);
            loanRepository.save(loan);
            chatService.sendSystemMessage(loan.getId(), "SYSTEM: Loan is fully REPAID. (Pending collateral release stage)");
        }
    }

    private void processRepaymentFinancials(Loan loan, BigDecimal repaymentAmount) {
        BigDecimal newTotal = loan.getTotalOutstanding().subtract(repaymentAmount);
        if (newTotal.compareTo(BigDecimal.ZERO) < 0) {
             newTotal = BigDecimal.ZERO;
        }
        loan.setTotalOutstanding(newTotal);

        // Deduct interest first, then principal
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

        // Process EMIs resolving them chronologically
        List<LoanEmi> pendingEmis = emiRepository.findByLoanIdOrderByEmiNumberAsc(loan.getId())
                .stream()
                .filter(emi -> emi.getStatus() == EmiStatus.PENDING || emi.getStatus() == EmiStatus.PARTIAL || emi.getStatus() == EmiStatus.OVERDUE)
                .toList();

        BigDecimal remainingToSettle = repaymentAmount;

        for (LoanEmi emi : pendingEmis) {
            if (remainingToSettle.compareTo(BigDecimal.ZERO) <= 0) break;

            BigDecimal emiRemaining = emi.getEmiAmount().subtract(emi.getAmountPaid());
            
            if (remainingToSettle.compareTo(emiRemaining) >= 0) {
                // Fully settle this EMI
                remainingToSettle = remainingToSettle.subtract(emiRemaining);
                emi.setAmountPaid(emi.getEmiAmount());
                emi.setStatus(EmiStatus.PAID);
            } else {
                // Partially settle this EMI
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
        LoanLedger ledger = LoanLedger.builder()
                .loan(loan)
                .entryType(type)
                .amountInr(amount)
                .notes(notes)
                .build();
        ledgerRepository.save(ledger);
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
