package com.linkbit.mvp.controller;

import com.linkbit.mvp.service.CollateralReleaseService;
import com.linkbit.mvp.domain.Loan;
import com.linkbit.mvp.domain.LoanStatus;
import com.linkbit.mvp.repository.LoanRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
public class CollateralReleaseController {

    private final CollateralReleaseService collateralReleaseService;
    private final LoanRepository loanRepository;

    public record CollateralBalanceResponse(UUID loanId, BigDecimal collateralBtc, String status) {}

    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/admin/loans/{loan_id}/release-collateral")
    public ResponseEntity<Void> releaseCollateral(
            @PathVariable("loan_id") UUID loanId,
            Authentication authentication) {
        collateralReleaseService.releaseCollateral(loanId, authentication.getName());
        return ResponseEntity.ok().build();
    }

    @GetMapping("/loans/{loan_id}/collateral")
    public ResponseEntity<CollateralBalanceResponse> getCollateralBalance(
            @PathVariable("loan_id") UUID loanId,
            Authentication authentication) {
        BigDecimal balance = collateralReleaseService.getCollateralBalance(loanId, authentication.getName());
        Loan loan = loanRepository.getReferenceById(loanId);
        String status = loan.getStatus() == LoanStatus.CLOSED ? "RELEASED" : "LOCKED";
        return ResponseEntity.ok(new CollateralBalanceResponse(loanId, balance, status));
    }
}

