package com.linkbit.mvp.domain;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "loan_liquidations")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LoanLiquidation {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "loan_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private Loan loan;

    @Column(name = "btc_price_inr", nullable = false)
    private BigDecimal btcPriceInr;

    @Column(name = "collateral_value_inr", nullable = false)
    private BigDecimal collateralValueInr;

    @Column(name = "lender_repaid", nullable = false)
    private BigDecimal lenderRepaid;

    @Column(name = "borrower_returned", nullable = false)
    private BigDecimal borrowerReturned;

    @Column(name = "liquidation_penalty", nullable = false)
    private BigDecimal liquidationPenalty;

    @CreationTimestamp
    @Column(name = "executed_at", nullable = false, updatable = false)
    private LocalDateTime executedAt;
}
