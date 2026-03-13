package com.linkbit.mvp.domain;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

@Entity
@Table(name = "escrow_accounts")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EscrowAccount {

    @Id
    @Column(name = "loan_id")
    private UUID loanId;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "loan_id", insertable = false, updatable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private Loan loan;

    @Column(name = "escrow_address", nullable = false)
    private String escrowAddress;

    @Column(name = "current_balance_sats", nullable = false)
    private Long currentBalanceSats;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
}

