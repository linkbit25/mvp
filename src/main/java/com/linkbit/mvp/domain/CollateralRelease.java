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
@Table(name = "collateral_releases")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CollateralRelease {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "loan_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private Loan loan;

    @Column(name = "released_btc", nullable = false)
    private BigDecimal releasedBtc;

    @Column(name = "executed_by")
    private UUID executedBy;

    @CreationTimestamp
    @Column(name = "executed_at", nullable = false, updatable = false)
    private LocalDateTime executedAt;
}
