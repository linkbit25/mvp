package com.linkbit.mvp.controller;

import com.linkbit.mvp.service.BtcPriceService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.Map;

@RestController
@RequestMapping("/btc")
@RequiredArgsConstructor
public class BtcPriceController {

    private final BtcPriceService btcPriceService;

    @GetMapping("/price")
    public ResponseEntity<Map<String, BigDecimal>> getCurrentPrice() {
        BigDecimal price = btcPriceService.getCurrentBtcPrice();
        return ResponseEntity.ok(Map.of("inr", price));
    }
}
