package com.linkbit.mvp.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.linkbit.mvp.domain.PlatformFeeStatus;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.util.UUID;

@Data
@Builder
public class FeeResponse {

    @JsonProperty("fee_id")
    private UUID feeId;

    @JsonProperty("loan_id")
    private UUID loanId;

    @JsonProperty("amount_inr")
    private BigDecimal amountInr;

    @JsonProperty("status")
    private PlatformFeeStatus status;
}
