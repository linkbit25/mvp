package com.linkbit.mvp.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.util.UUID;

@Data
@Builder
public class OfferResponse {

    @JsonProperty("offer_id")
    private UUID offerId;

    @JsonProperty("lender_pseudonym")
    private String lenderPseudonym;

    @JsonProperty("loan_amount")
    private BigDecimal loanAmount;

    @JsonProperty("interest_rate")
    private BigDecimal interestRate;

    @JsonProperty("expected_ltv")
    private Integer expectedLtv;

    @JsonProperty("tenure_days")
    private Integer tenureDays;
}
