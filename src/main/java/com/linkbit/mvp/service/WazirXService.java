package com.linkbit.mvp.service;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Fetches live BTC/INR price from WazirX public API.
 * WazirX is an Indian exchange with no API key requirement and generous rate limits,
 * supporting the 1 req/sec cadence needed for real-time LTV monitoring.
 *
 * Endpoint: GET https://api.wazirx.com/sapi/v1/ticker/24hr?symbol=btcinr
 * Response: { "symbol": "btcinr", "lastPrice": "7234500.0", ... }
 */
@Slf4j
@Service
public class WazirXService {

    private static final String WAZIRX_TICKER_URL =
            "https://api.wazirx.com/sapi/v1/ticker/24hr?symbol=btcinr";

    private final RestTemplate restTemplate;

    // Seeded with a reasonable default so LTV monitoring starts immediately
    private final AtomicReference<BigDecimal> cachedBtcPrice =
            new AtomicReference<>(new BigDecimal("7000000.00"));

    public WazirXService(RestTemplateBuilder restTemplateBuilder) {
        this.restTemplate = restTemplateBuilder.build();
    }

    /**
     * Polls WazirX every second for the latest BTC/INR market price.
     * The cached value is served to callers without a network call.
     */
    @Scheduled(fixedRate = 1000)
    public void fetchLatestBtcPrice() {
        try {
            JsonNode response = restTemplate.getForObject(WAZIRX_TICKER_URL, JsonNode.class);

            if (response != null && response.has("lastPrice")) {
                String rawPrice = response.get("lastPrice").asText();
                BigDecimal latestPrice = new BigDecimal(rawPrice);
                if (latestPrice.compareTo(BigDecimal.ZERO) > 0) {
                    cachedBtcPrice.set(latestPrice);
                    log.debug("BTC/INR price updated: {}", latestPrice);
                }
            }
        } catch (Exception e) {
            // Retain the last known good price — monitoring continues uninterrupted
            log.warn("Failed to fetch BTC/INR price from WazirX: {}", e.getMessage());
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
