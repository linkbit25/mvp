package com.linkbit.mvp.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class TopupRequest {
    @NotNull(message = "Amount in BTC cannot be null")
    @DecimalMin(value = "0.00000001", message = "Amount must be at least 1 satoshi")
    private BigDecimal amountBtc;
}
