package com.linkbit.mvp.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class KycSubmitRequest {
    @NotBlank(message = "Full Legal Name is required")
    private String fullLegalName;

    @NotBlank(message = "Bank Account Number is required")
    private String bankAccountNumber;

    @NotBlank(message = "IFSC code is required")
    private String ifsc;

    @NotBlank(message = "UPI ID is required")
    private String upiId;
}
