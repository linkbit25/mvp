package com.linkbit.mvp.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Data;

import java.util.UUID;

@Data
@Builder
public class EscrowResponse {

    @JsonProperty("loan_id")
    private UUID loanId;

    @JsonProperty("escrow_address")
    private String escrowAddress;

    @JsonProperty("current_balance_sats")
    private Long currentBalanceSats;
}
