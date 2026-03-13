package com.linkbit.mvp.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class SignContractRequest {

    @NotBlank
    @JsonProperty("signature_string")
    private String signatureString;
}
