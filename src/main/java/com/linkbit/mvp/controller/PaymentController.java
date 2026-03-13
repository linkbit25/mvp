package com.linkbit.mvp.controller;

import com.linkbit.mvp.dto.FeeResponse;
import com.linkbit.mvp.dto.PayFeeRequest;
import com.linkbit.mvp.service.PaymentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentService paymentService;

    @PostMapping("/payments/fee/pay")
    public ResponseEntity<FeeResponse> initiateFeePayment(
            Authentication authentication,
            @Valid @RequestBody PayFeeRequest request) {
        
        FeeResponse response = paymentService.initiateFeePayment(authentication.getName(), request.getLoanId());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/admin/payments/{feeId}/verify")
    public ResponseEntity<Void> verifyPayment(@PathVariable UUID feeId) {
        paymentService.verifyPayment(feeId);
        return ResponseEntity.ok().build();
    }
}
