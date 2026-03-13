package com.linkbit.mvp.domain;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "loan_ltv_history")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LoanLtvHistory {

    @Id
    @GeneratedValue
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "loan_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private Loan loan;

    @Column(name = "btc_price_inr", nullable = false)
    private BigDecimal btcPriceInr;

    @Column(name = "collateral_value_inr", nullable = false)
    private BigDecimal collateralValueInr;

    @Column(name = "ltv_percent", nullable = false)
    private BigDecimal ltvPercent;

    @CreationTimestamp
    @Column(name = "recorded_at", nullable = false, updatable = false)
    private LocalDateTime recordedAt;
}
