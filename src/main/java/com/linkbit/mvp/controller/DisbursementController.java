package com.linkbit.mvp.controller;

import com.linkbit.mvp.dto.DisbursementRequest;
import com.linkbit.mvp.dto.PaymentDetailsResponse;
import com.linkbit.mvp.service.DisbursementService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequiredArgsConstructor
public class DisbursementController {

    private final DisbursementService disbursementService;

    @GetMapping("/loans/{loanId}/payment-details")
    public ResponseEntity<PaymentDetailsResponse> getPaymentDetails(
            @PathVariable UUID loanId,
            Authentication authentication) {
        
        PaymentDetailsResponse response = disbursementService.getPaymentDetails(loanId, authentication.getName());
        return ResponseEntity.ok(response);
    }

    @PostMapping("/loans/{loanId}/disburse")
    public ResponseEntity<Void> markDisbursed(
            @PathVariable UUID loanId,
            @Valid @RequestBody DisbursementRequest request,
            Authentication authentication) {
        
        disbursementService.markDisbursed(loanId, authentication.getName(), request);
        return ResponseEntity.accepted().build();
    }

    @PostMapping("/loans/{loanId}/confirm-receipt")
    public ResponseEntity<Void> confirmReceipt(
            @PathVariable UUID loanId,
            Authentication authentication) {
        
        disbursementService.confirmReceipt(loanId, authentication.getName());
        return ResponseEntity.accepted().build();
    }

    @PostMapping("/loans/{loanId}/cancel-disbursement")
    public ResponseEntity<Void> cancelDisbursement(
            @PathVariable UUID loanId,
            Authentication authentication) {
        
        disbursementService.cancelDisbursement(loanId, authentication.getName());
        return ResponseEntity.accepted().build();
    }

    @PostMapping("/loans/{loanId}/open-dispute")
    public ResponseEntity<Void> openDispute(
            @PathVariable UUID loanId,
            Authentication authentication) {
        
        disbursementService.openDispute(loanId, authentication.getName());
        return ResponseEntity.accepted().build();
    }

    @PostMapping("/admin/loans/{loanId}/activate")
    public ResponseEntity<Void> activateLoanAdmin(@PathVariable UUID loanId) {
        disbursementService.activateLoanAdmin(loanId);
        return ResponseEntity.accepted().build();
    }

    @PostMapping("/admin/loans/{loanId}/refund-collateral")
    public ResponseEntity<Void> refundCollateralAdmin(@PathVariable UUID loanId) {
        disbursementService.refundCollateralAdmin(loanId);
        return ResponseEntity.accepted().build();
    }
}
