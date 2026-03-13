package com.linkbit.mvp.domain;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

@Entity
@Table(name = "platform_fees")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PlatformFee {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "loan_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private Loan loan;

    @Column(name = "amount_inr", nullable = false, precision = 19, scale = 2)
    private BigDecimal amountInr;

    @Column(name = "payment_gateway_ref")
    private String paymentGatewayRef;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private PlatformFeeStatus status;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
}

