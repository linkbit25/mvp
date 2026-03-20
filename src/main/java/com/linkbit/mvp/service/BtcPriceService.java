package com.linkbit.mvp.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.linkbit.mvp.dto.BtcHistoryResponse;
import com.linkbit.mvp.dto.BtcPriceResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

@Slf4j
@Service
public class BtcPriceService {

    private static final String BINANCE_TICKER_24H_URL =
            "https://api.binance.com/api/v3/ticker/24hr?symbol=BTCUSDT";
    private static final String BINANCE_KLINES_URL =
            "https://api.binance.com/api/v3/klines?symbol=BTCUSDT&interval=1h&limit=24";

    private final RestTemplate restTemplate;
    
    // Using 84.0 as approximate USD-INR conversion rate
    private static final BigDecimal USD_TO_INR = new BigDecimal("84.0");

    private final AtomicReference<BtcPriceResponse> cachedPrice =
            new AtomicReference<>(BtcPriceResponse.builder()
                    .inr(new BigDecimal("7000000.00"))
                    .change24h(BigDecimal.ZERO)
                    .build());

    private final AtomicReference<List<BtcHistoryResponse>> cachedHistory =
            new AtomicReference<>(new ArrayList<>());

    public BtcPriceService(RestTemplateBuilder restTemplateBuilder) {
        this.restTemplate = restTemplateBuilder.build();
    }

    @Scheduled(fixedRate = 30000)
    public void fetchLatestBtcData() {
        fetchPriceAndChange();
        fetchHistory();
    }

    private void fetchPriceAndChange() {
        try {
            JsonNode response = restTemplate.getForObject(BINANCE_TICKER_24H_URL, JsonNode.class);
            if (response != null && response.has("lastPrice")) {
                BigDecimal lastPriceUsdt = new BigDecimal(response.get("lastPrice").asText());
                BigDecimal priceChangePercent = new BigDecimal(response.get("priceChangePercent").asText());
                
                BigDecimal priceInr = lastPriceUsdt.multiply(USD_TO_INR).setScale(2, RoundingMode.HALF_UP);
                
                cachedPrice.set(BtcPriceResponse.builder()
                        .inr(priceInr)
                        .change24h(priceChangePercent)
                        .build());
                
                log.debug("BTC price updated: {} INR ({}%)", priceInr, priceChangePercent);
            }
        } catch (Exception e) {
            log.warn("Failed to fetch BTC ticker from Binance: {}", e.getMessage());
        }
    }

    private void fetchHistory() {
        try {
            JsonNode response = restTemplate.getForObject(BINANCE_KLINES_URL, JsonNode.class);
            if (response != null && response.isArray()) {
                List<BtcHistoryResponse> history = new ArrayList<>();
                for (JsonNode kline : response) {
                    long timestamp = kline.get(0).asLong();
                    BigDecimal closePriceUsdt = new BigDecimal(kline.get(4).asText());
                    BigDecimal closePriceInr = closePriceUsdt.multiply(USD_TO_INR).setScale(2, RoundingMode.HALF_UP);
                    
                    history.add(BtcHistoryResponse.builder()
                            .timestamp(timestamp)
                            .price(closePriceInr)
                            .build());
                }
                cachedHistory.set(history);
                log.debug("BTC history updated with {} points", history.size());
            }
        } catch (Exception e) {
            log.warn("Failed to fetch BTC history from Binance: {}", e.getMessage());
        }
    }

    public BtcPriceResponse getFullPriceResponse() {
        return cachedPrice.get();
    }

    public BigDecimal getCurrentBtcPrice() {
        return cachedPrice.get().getInr();
    }

    public List<BtcHistoryResponse> getBtcHistory() {
        return cachedHistory.get();
    }
}
