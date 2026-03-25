package com.linkbit.mvp.dto;

import com.linkbit.mvp.domain.RepaymentType;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UpdateTermsRequest {

    @NotNull
    @com.fasterxml.jackson.annotation.JsonProperty("principalAmount")
    private BigDecimal principalAmount;

    @NotNull
    @com.fasterxml.jackson.annotation.JsonProperty("interestRate")
    private BigDecimal interestRate;

    @NotNull
    @com.fasterxml.jackson.annotation.JsonProperty("tenureDays")
    private Integer tenureDays;

    @NotNull
    @com.fasterxml.jackson.annotation.JsonProperty("repaymentType")
    private RepaymentType repaymentType;

    @NotNull
    @com.fasterxml.jackson.annotation.JsonProperty("emiCount")
    private Integer emiCount;

    @NotNull
    @com.fasterxml.jackson.annotation.JsonProperty("expectedLtvPercent")
    private Integer expectedLtvPercent;

    @NotNull
    @com.fasterxml.jackson.annotation.JsonProperty("marginCallLtvPercent")
    private Integer marginCallLtvPercent;

    @NotNull
    @com.fasterxml.jackson.annotation.JsonProperty("liquidationLtvPercent")
    private Integer liquidationLtvPercent;
}
