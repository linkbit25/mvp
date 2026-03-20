package com.linkbit.mvp.controller;

import com.linkbit.mvp.dto.DepositRequest;
import com.linkbit.mvp.dto.EscrowResponse;
import com.linkbit.mvp.dto.TopupRequest;
import com.linkbit.mvp.service.EscrowService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequiredArgsConstructor
public class EscrowController {

    private final EscrowService escrowService;

    @PostMapping("/loans/{loanId}/escrow/generate")
    public ResponseEntity<EscrowResponse> generateEscrowAddress(
            Authentication authentication,
            @PathVariable UUID loanId) {
        
        EscrowResponse response = escrowService.generateAddress(authentication.getName(), loanId);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/loans/{loanId}/deposit")
    public ResponseEntity<Void> submitDeposit(
            Authentication authentication,
            @PathVariable UUID loanId,
            @Valid @RequestBody DepositRequest request) {
        
        escrowService.deposit(authentication.getName(), loanId, request.getAmountBtc());
        return ResponseEntity.status(HttpStatus.ACCEPTED).build();
    }

    @org.springframework.security.access.prepost.PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/admin/collateral/{loanId}/verify")
    public ResponseEntity<Void> verifyCollateralDeposit(@PathVariable UUID loanId) {
        escrowService.verifyDeposit(loanId);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/loans/{loanId}/topup-collateral")
    public ResponseEntity<Void> submitTopup(
            Authentication authentication,
            @PathVariable UUID loanId,
            @Valid @RequestBody TopupRequest request) {
        
        escrowService.submitTopup(authentication.getName(), loanId, request.getAmountBtc());
        return ResponseEntity.status(HttpStatus.ACCEPTED).build();
    }

    @org.springframework.security.access.prepost.PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/admin/collateral/{loanId}/verify-topup")
    public ResponseEntity<Void> verifyTopup(@PathVariable UUID loanId) {
        escrowService.verifyTopup(loanId);
        return ResponseEntity.ok().build();
    }
}
