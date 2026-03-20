package com.linkbit.mvp.controller;

import com.linkbit.mvp.dto.PendingRepaymentResponse;
import com.linkbit.mvp.service.CollateralService;
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

    private final CollateralService collateralService;
    private final RepaymentService repaymentService;

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
        collateralService.verifyCollateralDeposit(loanId, auth.getName());
        return ResponseEntity.ok().build();
    }
}
