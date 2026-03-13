package com.linkbit.mvp.domain;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

@Entity
@Table(name = "loan_repayments")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LoanRepayment {

    @Id
    @GeneratedValue
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "loan_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private Loan loan;

    @Column(name = "amount_inr", nullable = false)
    private BigDecimal amountInr;

    @Column(name = "transaction_reference", nullable = false)
    private String transactionReference;

    @Column(name = "proof_url", nullable = false)
    private String proofUrl;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private RepaymentStatus status;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
}

