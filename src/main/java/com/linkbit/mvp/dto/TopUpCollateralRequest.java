package com.linkbit.mvp.dto;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class TopUpCollateralRequest {
    private BigDecimal amount_btc;
}
