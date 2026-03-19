package com.linkbit.mvp.service;

import com.linkbit.mvp.domain.Loan;
import com.linkbit.mvp.domain.LoanStatus;
import com.linkbit.mvp.domain.ActorType;
import com.linkbit.mvp.domain.LoanAction;
import com.linkbit.mvp.domain.PlatformFee;
import com.linkbit.mvp.domain.PlatformFeeStatus;
import com.linkbit.mvp.domain.User;
import com.linkbit.mvp.dto.FeeResponse;
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
        User borrower = userRepository.findByEmail(email)
                .orElseThrow(() -> new UsernameNotFoundException("User not found"));
        Loan loan = loanRepository.findById(loanId)
                .orElseThrow(() -> new RuntimeException("Loan not found"));

        if (!loan.getBorrower().getId().equals(borrower.getId())) {
            throw new RuntimeException("Only borrower can initiate fee payment");
        }
        if (loan.getStatus() != LoanStatus.AWAITING_FEE) {
            throw new RuntimeException("Loan is not awaiting fee");
        }

        BigDecimal feeAmount = loan.getPrincipalAmount()
                .multiply(new BigDecimal("0.02"))
                .setScale(2, RoundingMode.HALF_UP);

        PlatformFee savedFee = platformFeeRepository
                .findTopByLoanIdAndStatusInOrderByCreatedAtDesc(loanId, List.of(PlatformFeeStatus.PENDING, PlatformFeeStatus.SUCCESS))
                .orElseGet(() -> platformFeeRepository.save(PlatformFee.builder()
                        .loan(loan)
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
        platformFeeRepository.save(fee);

        Loan loan = fee.getLoan();
        if (loan.getStatus() == LoanStatus.AWAITING_FEE) {
            stateMachineService.transition(loan, LoanAction.PAY_FEE, ActorType.BORROWER);
            loanRepository.save(loan);
            chatService.sendSystemMessage(loan.getId(), "SYSTEM: Processing fee verified. Loan status: AWAITING_COLLATERAL.");
        }
    }
}
