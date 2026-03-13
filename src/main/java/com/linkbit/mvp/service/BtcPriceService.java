package com.linkbit.mvp.service;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.concurrent.atomic.AtomicReference;

@Slf4j
@Service
public class BtcPriceService {

    private static final String BINANCE_TICKER_URL =
            "https://api.binance.com/api/v3/ticker/price?symbol=BTCUSDT";
    
    private static final String WAZIRX_USDT_INR_URL =
            "https://api.wazirx.com/sapi/v1/ticker/24hr?symbol=usdtinr";

    private final RestTemplate restTemplate;

    // Seeded with a reasonable default so LTV monitoring starts immediately
    private final AtomicReference<BigDecimal> cachedBtcPrice =
            new AtomicReference<>(new BigDecimal("8200000.00")); // Updated default to more modern levels

    public BtcPriceService(RestTemplateBuilder restTemplateBuilder) {
        this.restTemplate = restTemplateBuilder.build();
    }

    /**
     * Polls Binance and WazirX every second to calculate the latest BTC/INR market price.
     * Calculation: BTC/INR = (BTC/USDT from Binance) * (USDT/INR from WazirX)
     */
    @Scheduled(fixedRate = 30000)
    public void fetchLatestBtcPrice() {
        try {
            // 1. Fetch BTC/USDT from Binance
            JsonNode binanceResponse = restTemplate.getForObject(BINANCE_TICKER_URL, JsonNode.class);
            BigDecimal btcUsdt = null;
            if (binanceResponse != null && binanceResponse.has("price")) {
                btcUsdt = new BigDecimal(binanceResponse.get("price").asText());
            }

            // 2. Fetch USDT/INR from WazirX
            JsonNode wazirxResponse = restTemplate.getForObject(WAZIRX_USDT_INR_URL, JsonNode.class);
            BigDecimal usdtInr = null;
            if (wazirxResponse != null && wazirxResponse.has("lastPrice")) {
                usdtInr = new BigDecimal(wazirxResponse.get("lastPrice").asText());
            }

            // 3. Calculate and cache BTC/INR
            if (btcUsdt != null && btcUsdt.compareTo(BigDecimal.ZERO) > 0 && 
                usdtInr != null && usdtInr.compareTo(BigDecimal.ZERO) > 0) {
                
                BigDecimal latestBtcInr = btcUsdt.multiply(usdtInr).setScale(2, RoundingMode.HALF_UP);
                cachedBtcPrice.set(latestBtcInr);
                log.info("BTC/INR price updated: {} (Binance BTC/USDT: {}, WazirX USDT/INR: {})", 
                        latestBtcInr, btcUsdt, usdtInr);
            }
        } catch (Exception e) {
            log.warn("Failed to fetch BTC price: {}", e.getMessage(), e);
        }
    }

    /**
     * Returns the most recently cached BTC/INR price.
     * Never null — falls back to seed value on startup.
     */
    public BigDecimal getCurrentBtcPrice() {
        return cachedBtcPrice.get();
    }
}
