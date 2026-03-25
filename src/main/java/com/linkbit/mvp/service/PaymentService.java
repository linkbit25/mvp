package com.linkbit.mvp.service;

import com.linkbit.mvp.domain.Loan;
import com.linkbit.mvp.domain.LoanStatus;
import com.linkbit.mvp.domain.ActorType;
import com.linkbit.mvp.domain.LoanAction;
import com.linkbit.mvp.domain.PlatformFee;
import com.linkbit.mvp.domain.PlatformFeeStatus;
import com.linkbit.mvp.domain.User;
import com.linkbit.mvp.dto.FeeResponse;
import com.linkbit.mvp.dto.PendingFeeResponse;
import com.linkbit.mvp.repository.LoanRepository;
import com.linkbit.mvp.repository.PlatformFeeRepository;
import com.linkbit.mvp.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PaymentService {

    private final LoanRepository loanRepository;
    private final PlatformFeeRepository platformFeeRepository;
    private final UserRepository userRepository;
    private final ChatService chatService;
    private final StateMachineService stateMachineService;

    @Transactional
    public FeeResponse initiateFeePayment(String email, UUID loanId) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new UsernameNotFoundException("User not found"));
        Loan loan = loanRepository.findById(loanId)
                .orElseThrow(() -> new RuntimeException("Loan not found"));

        ActorType role;
        if (loan.getBorrower().getId().equals(user.getId())) {
            role = ActorType.BORROWER;
        } else if (loan.getLender() != null && loan.getLender().getId().equals(user.getId())) {
            role = ActorType.LENDER;
        } else {
            throw new RuntimeException("User is not authorized for this loan");
        }

        if (loan.getStatus() != LoanStatus.AWAITING_FEE) {
            throw new RuntimeException("Loan is not awaiting fee");
        }

        // Divide 2% total fee into 1% each for borrower and lender
        BigDecimal feeAmount = loan.getPrincipalAmount()
                .multiply(new BigDecimal("0.01"))
                .setScale(2, RoundingMode.HALF_UP);

        PlatformFee savedFee = platformFeeRepository
                .findTopByLoanIdAndPayerRoleAndStatusInOrderByCreatedAtDesc(loanId, role, List.of(PlatformFeeStatus.PENDING, PlatformFeeStatus.SUCCESS))
                .orElseGet(() -> platformFeeRepository.save(PlatformFee.builder()
                        .loan(loan)
                        .payerRole(role)
                        .amountInr(feeAmount)
                        .status(PlatformFeeStatus.PENDING)
                        .build()));

        return FeeResponse.builder()
                .feeId(savedFee.getId())
                .loanId(loan.getId())
                .amountInr(savedFee.getAmountInr())
                .status(savedFee.getStatus())
                .build();
    }

    @Transactional
    public void verifyPayment(UUID feeId) {
        PlatformFee fee = platformFeeRepository.findById(feeId)
                .orElseThrow(() -> new RuntimeException("Platform fee not found"));
        if (fee.getStatus() != PlatformFeeStatus.PENDING) {
            throw new RuntimeException("Fee is not in PENDING status");
        }

        fee.setStatus(PlatformFeeStatus.SUCCESS);
        platformFeeRepository.saveAndFlush(fee);

        Loan loan = fee.getLoan();
        if (loan.getStatus() == LoanStatus.AWAITING_FEE) {
            // Check if both parties have paid
            List<PlatformFee> successFees = platformFeeRepository.findByLoanIdAndStatus(loan.getId(), PlatformFeeStatus.SUCCESS);
            boolean borrowerPaid = successFees.stream().anyMatch(f -> f.getPayerRole() == ActorType.BORROWER);
            boolean lenderPaid = successFees.stream().anyMatch(f -> f.getPayerRole() == ActorType.LENDER);

            if (borrowerPaid && lenderPaid) {
                stateMachineService.transition(loan, LoanAction.PAY_FEE, ActorType.ADMIN);
                loanRepository.save(loan);
                chatService.sendSystemMessage(loan.getId(), "SYSTEM: Both borrower and lender fees verified. Loan status: AWAITING_COLLATERAL.");
            } else {
                String missingParties = !borrowerPaid ? "BORROWER" : "LENDER";
                chatService.sendSystemMessage(loan.getId(), "SYSTEM: Fee payment verified for " + fee.getPayerRole() + ". Waiting for " + missingParties + ".");
            }
        }
    }

    @Transactional(readOnly = true)
    public List<PendingFeeResponse> getAllPendingFees() {
        return platformFeeRepository.findByStatusOrderByCreatedAtDesc(PlatformFeeStatus.PENDING).stream()
                .map(fee -> PendingFeeResponse.builder()
                        .feeId(fee.getId())
                        .amountInr(fee.getAmountInr())
                        .status(fee.getStatus())
                        .payerRole(fee.getPayerRole())
                        .createdAt(fee.getCreatedAt())
                        .build())
                .toList();
    }
}
