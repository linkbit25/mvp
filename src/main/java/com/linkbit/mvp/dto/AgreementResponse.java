package com.linkbit.mvp.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class AgreementResponse {
    private String agreementHash;
    private String borrowerSignature;
    private String lenderSignature;
    private LocalDateTime agreementFinalizedAt;
}
