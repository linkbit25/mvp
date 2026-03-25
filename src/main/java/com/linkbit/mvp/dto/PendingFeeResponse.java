package com.linkbit.mvp.dto;

import com.linkbit.mvp.domain.ActorType;
import com.linkbit.mvp.domain.PlatformFeeStatus;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
public class PendingFeeResponse {
    private UUID feeId;
    private BigDecimal amountInr;
    private PlatformFeeStatus status;
    private ActorType payerRole;
    private LocalDateTime createdAt;
}
