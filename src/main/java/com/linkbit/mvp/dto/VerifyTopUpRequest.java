package com.linkbit.mvp.dto;

import lombok.Data;

@Data
public class VerifyTopUpRequest {
    private String txHash;
    private Double amountBtc;
}
