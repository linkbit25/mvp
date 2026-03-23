package com.linkbit.mvp.dto;

import com.linkbit.mvp.domain.LoanStatus;
import com.linkbit.mvp.domain.RepaymentType;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Data
@Builder
public class LoanDetailResponse {
    private UUID loanId;
    private String role;
    private LoanStatus status;
    private String borrowerPseudonym;
    private String lenderPseudonym;
    private BigDecimal principalAmount;
    private BigDecimal interestRate;
    private Integer tenureDays;
    private RepaymentType repaymentType;
    private Integer emiCount;
    private BigDecimal emiAmount;
    private BigDecimal totalRepaymentAmount;
    private BigDecimal principalOutstanding;
    private BigDecimal interestOutstanding;
    private BigDecimal totalOutstanding;
    private Integer expectedLtvPercent;
    private Integer marginCallLtvPercent;
    private Integer liquidationLtvPercent;
    private BigDecimal collateralBtcAmount;
    private BigDecimal collateralValueInr;
    private BigDecimal currentLtvPercent;
    private String agreementHash;
    private LocalDateTime agreementFinalizedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime fiatDisbursedAt;
    private LocalDateTime fiatReceivedConfirmedAt;
    private String disbursementReference;
    private String disbursementProofUrl;
    private BigDecimal collateralReleasedBtc;
    private LocalDateTime collateralReleasedAt;
    private String escrowAddress;
    private Long escrowBalanceSats;
    private Boolean lenderFinalized;
    private Boolean borrowerFinalized;
    private PendingFeeResponse pendingFee;
    private List<PendingRepaymentResponse> pendingRepayments;
}
