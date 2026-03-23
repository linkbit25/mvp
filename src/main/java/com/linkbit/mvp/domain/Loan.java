package com.linkbit.mvp.domain;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "loans", indexes = {
    @Index(name = "idx_loans_status", columnList = "status"),
    @Index(name = "idx_loans_borrower_id", columnList = "borrower_id"),
    @Index(name = "idx_loans_lender_id", columnList = "lender_id"),
    @Index(name = "idx_loans_updated_at", columnList = "updated_at")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Loan {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Version
    private Long version;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "offer_id", nullable = false)
    private LoanOffer offer;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "lender_id", nullable = false)
    private User lender;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "borrower_id", nullable = false)
    private User borrower;

    @Column(name = "principal_amount", nullable = false, precision = 18, scale = 2)
    private BigDecimal principalAmount;

    @Column(name = "interest_rate", nullable = false, precision = 5, scale = 2)
    private BigDecimal interestRate;

    @Column(name = "tenure_days", nullable = false)
    private Integer tenureDays;

    @Enumerated(EnumType.STRING)
    @Column(name = "repayment_type")
    private RepaymentType repaymentType;

    @Column(name = "emi_count")
    private Integer emiCount;

    @Column(name = "emi_amount", precision = 18, scale = 2)
    private BigDecimal emiAmount;

    @Column(name = "total_repayment_amount", precision = 18, scale = 2)
    private BigDecimal totalRepaymentAmount;

    @Column(name = "principal_outstanding", precision = 18, scale = 2)
    private BigDecimal principalOutstanding;

    @Column(name = "interest_outstanding", precision = 18, scale = 2)
    private BigDecimal interestOutstanding;

    @Column(name = "total_outstanding", precision = 18, scale = 2)
    private BigDecimal totalOutstanding;

    @Column(name = "expected_ltv_percent")
    private Integer expectedLtvPercent;

    @Column(name = "collateral_btc_amount", precision = 24, scale = 8)
    private BigDecimal collateralBtcAmount;

    @Column(name = "collateral_value_inr", precision = 18, scale = 2)
    private BigDecimal collateralValueInr;

    @Column(name = "current_ltv_percent", precision = 5, scale = 2)
    private BigDecimal currentLtvPercent;

    @Column(name = "last_price_update")
    private LocalDateTime lastPriceUpdate;

    @Column(name = "margin_call_ltv_percent")
    private Integer marginCallLtvPercent;

    @Column(name = "liquidation_ltv_percent")
    private Integer liquidationLtvPercent;

    @Column(name = "agreement_hash")
    private String agreementHash;

    @Column(name = "agreement_finalized_at")
    private LocalDateTime agreementFinalizedAt;

    @Column(name = "borrower_signature", columnDefinition = "TEXT")
    private String borrowerSignature;

    @Column(name = "borrower_signed_at")
    private LocalDateTime borrowerSignedAt;

    @Column(name = "lender_signature", columnDefinition = "TEXT")
    private String lenderSignature;

    @Column(name = "lender_signed_at")
    private LocalDateTime lenderSignedAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private LoanStatus status;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "fiat_disbursed_at")
    private LocalDateTime fiatDisbursedAt;

    @Column(name = "fiat_received_confirmed_at")
    private LocalDateTime fiatReceivedConfirmedAt;

    @Column(name = "disbursement_reference")
    private String disbursementReference;

    @Column(name = "disbursement_proof_url")
    private String disbursementProofUrl;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "liquidation_executed_at")
    private LocalDateTime liquidationExecutedAt;

    @Column(name = "liquidation_price_inr", precision = 18, scale = 2)
    private BigDecimal liquidationPriceInr;

    @Column(name = "lender_repayment_amount", precision = 18, scale = 2)
    private BigDecimal lenderRepaymentAmount;

    @Column(name = "borrower_return_amount", precision = 18, scale = 2)
    private BigDecimal borrowerReturnAmount;

    @Column(name = "liquidation_penalty_amount", precision = 18, scale = 2)
    private BigDecimal liquidationPenaltyAmount;

    @Column(name = "collateral_released_at")
    private LocalDateTime collateralReleasedAt;

    @Column(name = "collateral_released_btc", precision = 24, scale = 8)
    private BigDecimal collateralReleasedBtc;

    @Builder.Default
    @Column(name = "lender_finalized")
    private Boolean lenderFinalized = false;

    @Builder.Default
    @Column(name = "borrower_finalized")
    private Boolean borrowerFinalized = false;

    @OneToMany(mappedBy = "loan", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<NegotiationMessage> negotiationMessages;
}
