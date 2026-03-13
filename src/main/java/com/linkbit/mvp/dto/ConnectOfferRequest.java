package com.linkbit.mvp.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.UUID;

@Data
public class ConnectOfferRequest {

    @NotNull(message = "Offer ID is required")
    @JsonProperty("offer_id")
    private UUID offerId;
}
