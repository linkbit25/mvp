package com.linkbit.mvp.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DisbursementRequest {

    @NotBlank(message = "Transaction reference is required")
    @JsonProperty("transaction_reference")
    private String transactionReference;

    @JsonProperty("proof_image_url")
    private String proofImageUrl;
}
