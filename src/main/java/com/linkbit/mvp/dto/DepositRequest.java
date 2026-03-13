package com.linkbit.mvp.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class DepositRequest {

    @NotNull(message = "Bitcoin amount is required")
    @Positive(message = "Bitcoin amount must be positive")
    @JsonProperty("amount_btc")
    private BigDecimal amountBtc;
}
