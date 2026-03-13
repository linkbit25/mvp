package com.linkbit.mvp.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class CreateOfferRequest {

    @NotNull(message = "Loan amount is required")
    @Min(value = 1000, message = "Loan amount must be at least 1000")
    @JsonProperty("loan_amount_inr")
    private BigDecimal loanAmountInr;

    @NotNull(message = "Interest rate is required")
    @Min(value = 0, message = "Interest rate cannot be negative")
    @Max(value = 100, message = "Interest rate cannot exceed 100")
    @JsonProperty("interest_rate")
    private BigDecimal interestRate;

    @NotNull(message = "Expected LTV is required")
    @Min(value = 1, message = "LTV must be positive")
    @Max(value = 90, message = "LTV cannot exceed 90%")
    @JsonProperty("expected_ltv_percent")
    private Integer expectedLtvPercent;

    @NotNull(message = "Tenure is required")
    @Min(value = 1, message = "Tenure must be at least 1 day")
    @JsonProperty("tenure_days")
    private Integer tenureDays;
}
