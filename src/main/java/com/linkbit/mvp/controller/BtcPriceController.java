package com.linkbit.mvp.controller;

import com.linkbit.mvp.dto.BtcHistoryResponse;
import com.linkbit.mvp.dto.BtcPriceResponse;
import com.linkbit.mvp.service.BtcPriceService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/btc")
@RequiredArgsConstructor
public class BtcPriceController {

    private final BtcPriceService btcPriceService;

    @GetMapping("/price")
    public ResponseEntity<BtcPriceResponse> getCurrentPrice() {
        return ResponseEntity.ok(btcPriceService.getFullPriceResponse());
    }

    @GetMapping("/price/history")
    public ResponseEntity<List<BtcHistoryResponse>> getPriceHistory() {
        return ResponseEntity.ok(btcPriceService.getBtcHistory());
    }
}
