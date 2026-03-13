package com.linkbit.mvp.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentDetailsResponse {

    @JsonProperty("account_number")
    private String accountNumber;

    private String ifsc;

    @JsonProperty("upi_id")
    private String upiId;
}
