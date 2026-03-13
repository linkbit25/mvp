package com.linkbit.mvp.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.linkbit.mvp.domain.RepaymentType;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class UpdateTermsRequest {

    @NotNull
    @JsonProperty("principal_amount")
    private BigDecimal principalAmount;

    @NotNull
    @JsonProperty("interest_rate")
    private BigDecimal interestRate;

    @NotNull
    @JsonProperty("tenure_days")
    private Integer tenureDays;

    @NotNull
    @JsonProperty("repayment_type")
    private RepaymentType repaymentType;

    @NotNull
    @JsonProperty("emi_count")
    private Integer emiCount;

    @NotNull
    @JsonProperty("expected_ltv_percent")
    private Integer expectedLtvPercent;

    @NotNull
    @JsonProperty("margin_call_ltv_percent")
    private Integer marginCallLtvPercent;

    @NotNull
    @JsonProperty("liquidation_ltv_percent")
    private Integer liquidationLtvPercent;
}
