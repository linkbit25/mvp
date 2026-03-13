package com.linkbit.mvp.dto;

import com.linkbit.mvp.domain.LedgerEntryType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LedgerResponse {
    private LedgerEntryType type;
    private BigDecimal amount;
}
