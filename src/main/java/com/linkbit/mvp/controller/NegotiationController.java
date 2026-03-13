package com.linkbit.mvp.controller;

import com.linkbit.mvp.dto.SignContractRequest;
import com.linkbit.mvp.dto.UpdateTermsRequest;
import com.linkbit.mvp.service.NegotiationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/loans")
@RequiredArgsConstructor
public class NegotiationController {

    private final NegotiationService negotiationService;

    @PutMapping("/{loanId}/terms")
    public ResponseEntity<Void> updateTerms(
            Authentication authentication,
            @PathVariable UUID loanId,
            @Valid @RequestBody UpdateTermsRequest request) {
        
        negotiationService.updateTerms(authentication.getName(), loanId, request);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/{loanId}/finalize")
    public ResponseEntity<Void> finalizeContract(
            Authentication authentication,
            @PathVariable UUID loanId) {
        
        negotiationService.finalizeContract(authentication.getName(), loanId);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/{loanId}/sign")
    public ResponseEntity<Void> signContract(
            Authentication authentication,
            @PathVariable UUID loanId,
            @Valid @RequestBody SignContractRequest request) {
        
        negotiationService.signContract(authentication.getName(), loanId, request.getSignatureString());
        return ResponseEntity.ok().build();
    }

    @PostMapping("/{loanId}/cancel")
    public ResponseEntity<Void> cancelNegotiation(
            Authentication authentication,
            @PathVariable UUID loanId) {
        
        negotiationService.cancelNegotiation(authentication.getName(), loanId);
        return ResponseEntity.ok().build();
    }
}
