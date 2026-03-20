package com.linkbit.mvp.controller;

import com.linkbit.mvp.dto.LedgerResponse;
import com.linkbit.mvp.dto.RepaymentRequest;
import com.linkbit.mvp.service.RepaymentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.RequestParam;

@RestController
@RequiredArgsConstructor
public class RepaymentController {

    private final RepaymentService repaymentService;

    @PostMapping("/loans/{loanId}/repay")
    public ResponseEntity<Void> submitRepayment(
            @PathVariable UUID loanId,
            @Valid @RequestBody RepaymentRequest request,
            Authentication authentication) {
        
        repaymentService.submitRepayment(loanId, authentication.getName(), request);
        return ResponseEntity.accepted().build();
    }

    @GetMapping("/loans/{loanId}/ledger")
    public ResponseEntity<List<LedgerResponse>> getLoanLedger(
            @PathVariable UUID loanId,
            Authentication authentication,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {

        List<LedgerResponse> response = repaymentService.getLoanLedger(loanId, authentication.getName());
        int start = page * size;
        int end = Math.min(start + size, response.size());
        return ResponseEntity.ok(start >= response.size() ? List.of() : response.subList(start, end));
    }


}
