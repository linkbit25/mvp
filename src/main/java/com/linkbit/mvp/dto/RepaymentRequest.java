package com.linkbit.mvp.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RepaymentRequest {

    @NotNull(message = "Amount is required")
    @DecimalMin(value = "0.01", message = "Amount must be strictly greater than 0")
    private BigDecimal amount;

    @NotBlank(message = "Transaction reference is required")
    @JsonProperty("transaction_reference")
    private String transactionReference;

    @NotBlank(message = "Proof image URL is required")
    @JsonProperty("proof_image_url")
    private String proofImageUrl;
}
