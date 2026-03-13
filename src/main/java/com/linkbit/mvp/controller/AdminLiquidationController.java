package com.linkbit.mvp.controller;

import com.linkbit.mvp.service.LiquidationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/admin/loans")
@RequiredArgsConstructor
public class AdminLiquidationController {

    private final LiquidationService liquidationService;

    @PostMapping("/{loan_id}/execute-liquidation")
    public ResponseEntity<Void> executeLiquidation(@PathVariable("loan_id") UUID loanId) {
        liquidationService.executeLiquidation(loanId);
        return ResponseEntity.ok().build();
    }
}
