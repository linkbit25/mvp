package com.linkbit.mvp.domain;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "loan_offers", indexes = {
    @Index(name = "idx_loan_offers_status", columnList = "status"),
    @Index(name = "idx_loan_offers_interest_rate", columnList = "interest_rate"),
    @Index(name = "idx_loan_offers_lender_id", columnList = "lender_id")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LoanOffer {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "lender_id", nullable = false)
    private User lender;

    @Column(name = "loan_amount_inr", nullable = false, precision = 18, scale = 2)
    private BigDecimal loanAmountInr;

    @Column(name = "interest_rate", nullable = false, precision = 5, scale = 2)
    private BigDecimal interestRate;

    @Column(name = "expected_ltv_percent", nullable = false)
    private Integer expectedLtvPercent;

    @Column(name = "tenure_days", nullable = false)
    private Integer tenureDays;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private LoanOfferStatus status;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
