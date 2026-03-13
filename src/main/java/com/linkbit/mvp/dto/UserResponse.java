package com.linkbit.mvp.dto;

import com.linkbit.mvp.domain.KycStatus;
import lombok.Builder;
import lombok.Data;

import java.util.UUID;

@Data
@Builder
public class UserResponse {

    private UUID userId;
    private String email;
    private String phoneNumber;
    private String pseudonym;
    private KycStatus kycStatus;
    private BankDetails bankDetails;

    @Data
    @Builder
    public static class BankDetails {
        private String bankAccountNumber;
        private String ifsc;
        private String upiId;
    }
}
