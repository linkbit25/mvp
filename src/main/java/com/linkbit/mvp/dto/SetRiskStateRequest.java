package com.linkbit.mvp.dto;

import com.linkbit.mvp.domain.LoanStatus;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SetRiskStateRequest {
    @NotNull(message = "Risk state status is required")
    private LoanStatus status;
}
