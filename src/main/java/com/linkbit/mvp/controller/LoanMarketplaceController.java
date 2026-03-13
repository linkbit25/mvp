package com.linkbit.mvp.controller;

import com.linkbit.mvp.dto.ConnectOfferRequest;
import com.linkbit.mvp.dto.CreateOfferRequest;
import com.linkbit.mvp.dto.OfferResponse;
import com.linkbit.mvp.service.LoanMarketplaceService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
public class LoanMarketplaceController {

    private final LoanMarketplaceService loanMarketplaceService;

    @PostMapping("/offers")
    public ResponseEntity<Void> createOffer(Authentication authentication, @Valid @RequestBody CreateOfferRequest request) {
        loanMarketplaceService.createOffer(authentication.getName(), request);
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    @GetMapping("/offers")
    public ResponseEntity<List<OfferResponse>> getOffers(
            @RequestParam(value = "amount", required = false) BigDecimal amount,
            @RequestParam(value = "tenure_days", required = false) Integer tenureDays,
            @RequestParam(value = "interest_rate", required = false) BigDecimal interestRate,
            @RequestParam(value = "expected_ltv_percent", required = false) Integer expectedLtv) {
        
        return ResponseEntity.ok(loanMarketplaceService.getOpenOffers(amount, tenureDays, interestRate, expectedLtv));
    }

    @PutMapping("/offers/{offerId}")
    public ResponseEntity<Void> editOffer(
            Authentication authentication,
            @PathVariable UUID offerId,
            @Valid @RequestBody CreateOfferRequest request) {
        
        loanMarketplaceService.editOffer(authentication.getName(), offerId, request);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/loans/connect")
    public ResponseEntity<UUID> connectOffer(Authentication authentication, @Valid @RequestBody ConnectOfferRequest request) {
        UUID loanId = loanMarketplaceService.connectOffer(authentication.getName(), request.getOfferId());
        return ResponseEntity.status(HttpStatus.CREATED).body(loanId);
    }
}
