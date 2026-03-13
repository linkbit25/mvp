package com.linkbit.mvp.dto;

import com.linkbit.mvp.domain.KycStatus;
import lombok.Builder;
import lombok.Data;

import java.util.UUID;

@Data
@Builder
public class AuthResponse {

    private String accessToken;
    private String refreshToken;
    private UUID userId;
    private KycStatus kycStatus;
}
