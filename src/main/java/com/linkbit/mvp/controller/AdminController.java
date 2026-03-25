package com.linkbit.mvp.controller;

import com.linkbit.mvp.dto.PendingFeeResponse;
import com.linkbit.mvp.dto.PendingRepaymentResponse;
import com.linkbit.mvp.service.EscrowService;
import com.linkbit.mvp.service.PaymentService;
import com.linkbit.mvp.service.RepaymentService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/admin")
@org.springframework.security.access.prepost.PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class AdminController {

    private final com.linkbit.mvp.repository.UserRepository userRepository;
    private final EscrowService escrowService;
    private final RepaymentService repaymentService;
    private final PaymentService paymentService;
    private final com.linkbit.mvp.service.NotificationService notificationService;

    /** List all PENDING repayments across every loan — for the admin repayment verification panel */
    @GetMapping("/repayments/pending")
    public ResponseEntity<List<PendingRepaymentResponse>> getPendingRepayments() {
        return ResponseEntity.ok(repaymentService.getAllPendingRepayments());
    }

    /** Verify (approve) a repayment submission */
    @PostMapping("/repayments/{repaymentId}/verify")
    public ResponseEntity<Void> verifyRepayment(@PathVariable UUID repaymentId) {
        repaymentService.verifyRepayment(repaymentId);
        return ResponseEntity.ok().build();
    }

    /** Verify that collateral has been deposited for a loan */
    @PostMapping("/loans/{loanId}/verify-deposit")
    public ResponseEntity<Void> verifyDeposit(
            @PathVariable UUID loanId,
            Authentication auth) {
        escrowService.verifyDeposit(loanId);
        return ResponseEntity.ok().build();
    }

    /** List all PENDING fees across every loan — for the admin fee verification panel */
    @GetMapping("/fees/pending")
    public ResponseEntity<List<PendingFeeResponse>> getPendingFees() {
        return ResponseEntity.ok(paymentService.getAllPendingFees());
    }

    /** Verify (approve) a fee payment */
    @PostMapping("/fees/{feeId}/verify")
    public ResponseEntity<Void> verifyFee(@PathVariable UUID feeId) {
        paymentService.verifyPayment(feeId);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/kyc-applications")
    @org.springframework.transaction.annotation.Transactional(readOnly = true)
    public ResponseEntity<List<com.linkbit.mvp.dto.UserResponse>> getKycApplications() {
        return ResponseEntity.ok(userRepository.findByKycStatus(com.linkbit.mvp.domain.KycStatus.SUBMITTED).stream()
                .map(this::mapToUserResponse)
                .toList());
    }

    @GetMapping("/users")
    @org.springframework.transaction.annotation.Transactional(readOnly = true)
    public ResponseEntity<List<com.linkbit.mvp.dto.UserResponse>> getAllUsers() {
        return ResponseEntity.ok(userRepository.findAll().stream()
                .map(this::mapToUserResponse)
                .toList());
    }

    private com.linkbit.mvp.dto.UserResponse mapToUserResponse(com.linkbit.mvp.domain.User user) {
        com.linkbit.mvp.domain.UserKycDetails details = user.getKycDetails();
        com.linkbit.mvp.dto.UserResponse.BankDetails bankDetails = null;
        if (details != null) {
            bankDetails = com.linkbit.mvp.dto.UserResponse.BankDetails.builder()
                    .bankAccountNumber(details.getBankAccountNumber())
                    .ifsc(details.getIfsc())
                    .upiId(details.getUpiId())
                    .build();
        }
        return com.linkbit.mvp.dto.UserResponse.builder()
                .userId(user.getId())
                .email(user.getEmail())
                .phoneNumber(user.getPhoneNumber())
                .pseudonym(user.getPseudonym())
                .fullLegalName(details != null ? details.getFullLegalName() : null)
                .kycStatus(user.getKycStatus())
                .bankDetails(bankDetails)
                .admin(user.getRole() == com.linkbit.mvp.domain.ActorType.ADMIN)
                .build();
    }

    @PostMapping("/users/{userId}/kyc/approve")
    @org.springframework.transaction.annotation.Transactional
    public ResponseEntity<Void> approveKyc(@PathVariable UUID userId) {
        com.linkbit.mvp.domain.User user = userRepository.findById(userId).orElseThrow();
        user.setKycStatus(com.linkbit.mvp.domain.KycStatus.VERIFIED);
        userRepository.save(user);
        
        notificationService.createForUser(userId, "KYC Approved", "Your identity verification has been approved. You can now use the marketplace.");
        return ResponseEntity.ok().build();
    }

    @PostMapping("/users/{userId}/kyc/reject")
    @org.springframework.transaction.annotation.Transactional
    public ResponseEntity<Void> rejectKyc(@PathVariable UUID userId) {
        com.linkbit.mvp.domain.User user = userRepository.findById(userId).orElseThrow();
        user.setKycStatus(com.linkbit.mvp.domain.KycStatus.REJECTED);
        userRepository.save(user);
        
        notificationService.createForUser(userId, "KYC Rejected", "Your identity verification application was rejected. Please review your details and resubmit.");
        return ResponseEntity.ok().build();
    }
}
