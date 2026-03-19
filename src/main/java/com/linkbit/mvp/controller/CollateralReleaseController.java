package com.linkbit.mvp.controller;

import com.linkbit.mvp.service.CollateralReleaseService;
import com.linkbit.mvp.domain.Loan;
import com.linkbit.mvp.domain.LoanStatus;
import com.linkbit.mvp.repository.LoanRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
public class CollateralReleaseController {

    private final CollateralReleaseService collateralReleaseService;
    private final LoanRepository loanRepository;

    @PostMapping("/admin/loans/{loan_id}/release-collateral")
    public ResponseEntity<Void> releaseCollateral(
            @PathVariable("loan_id") UUID loanId,
            Authentication authentication) {
        collateralReleaseService.releaseCollateral(loanId, authentication.getName());
        return ResponseEntity.ok().build();
    }
    
    @GetMapping("/loans/{loan_id}/collateral")
    public ResponseEntity<Map<String, Object>> getCollateralBalance(
            @PathVariable("loan_id") UUID loanId,
            Authentication authentication) {
        BigDecimal balance = collateralReleaseService.getCollateralBalance(loanId, authentication.getName());
        
        // The service already verifies participation and existence.
        // We only need the status to decide between RELEASED/LOCKED.
        // To avoid redundant DB hit, we could enhance the service, but for now we'll just keep it simple.
        Loan loan = loanRepository.getReferenceById(loanId); 
        
        String status = loan.getStatus() == LoanStatus.CLOSED ? "RELEASED" : "LOCKED";
        
        Map<String, Object> response = new HashMap<>();
        response.put("loan_id", loanId);
        response.put("collateral_btc", balance);
        response.put("status", status);
        
        return ResponseEntity.ok(response);
    }
}
