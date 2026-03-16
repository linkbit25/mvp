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

    private static final String COINGECKO_PRICE_URL =
            "https://api.coingecko.com/api/v3/simple/price?ids=bitcoin&vs_currencies=inr";

    private final RestTemplate restTemplate;
    private final AtomicReference<BigDecimal> cachedBtcPrice =
            new AtomicReference<>(new BigDecimal("8200000.00"));

    public BtcPriceService(RestTemplateBuilder restTemplateBuilder) {
        this.restTemplate = restTemplateBuilder.build();
    }

    @Scheduled(fixedRate = 1000)
    public void fetchLatestBtcPrice() {
        try {
            JsonNode response = restTemplate.getForObject(COINGECKO_PRICE_URL, JsonNode.class);
            if (response != null && response.has("bitcoin") && response.get("bitcoin").has("inr")) {
                BigDecimal latestBtcInr = new BigDecimal(response.get("bitcoin").get("inr").asText())
                        .setScale(2, RoundingMode.HALF_UP);
                cachedBtcPrice.set(latestBtcInr);
                log.info("BTC/INR price updated from CoinGecko: {}", latestBtcInr);
            }
        } catch (Exception e) {
            log.warn("Failed to fetch BTC price: {}", e.getMessage());
        }
    }

    public BigDecimal getCurrentBtcPrice() {
        return cachedBtcPrice.get();
    }
}
