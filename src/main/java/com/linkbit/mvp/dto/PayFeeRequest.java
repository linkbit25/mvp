package com.linkbit.mvp.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.UUID;

@Data
public class PayFeeRequest {

    @NotNull(message = "Loan ID is required")
    @JsonProperty("loan_id")
    private UUID loanId;
}
