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
@Table(name = "loans")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Loan {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "offer_id", nullable = false)
    private LoanOffer offer;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "lender_id", nullable = false)
    private User lender;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "borrower_id", nullable = false)
    private User borrower;

    @Column(name = "principal_amount", nullable = false)
    private BigDecimal principalAmount;

    @Column(name = "interest_rate", nullable = false)
    private BigDecimal interestRate;

    @Column(name = "tenure_days", nullable = false)
    private Integer tenureDays;

    @Enumerated(EnumType.STRING)
    @Column(name = "repayment_type")
    private RepaymentType repaymentType;

    @Column(name = "emi_count")
    private Integer emiCount;

    @Column(name = "emi_amount")
    private BigDecimal emiAmount;

    @Column(name = "total_repayment_amount")
    private BigDecimal totalRepaymentAmount;

    @Column(name = "principal_outstanding")
    private BigDecimal principalOutstanding;

    @Column(name = "interest_outstanding")
    private BigDecimal interestOutstanding;

    @Column(name = "total_outstanding")
    private BigDecimal totalOutstanding;

    @Column(name = "expected_ltv_percent")
    private Integer expectedLtvPercent;

    @Column(name = "collateral_btc_amount")
    private BigDecimal collateralBtcAmount;

    @Column(name = "collateral_value_inr")
    private BigDecimal collateralValueInr;

    @Column(name = "current_ltv_percent")
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

    @Column(name = "lender_signature", columnDefinition = "TEXT")
    private String lenderSignature;

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

    @Column(name = "liquidation_price_inr")
    private BigDecimal liquidationPriceInr;

    @Column(name = "lender_repayment_amount")
    private BigDecimal lenderRepaymentAmount;

    @Column(name = "borrower_return_amount")
    private BigDecimal borrowerReturnAmount;

    @Column(name = "liquidation_penalty_amount")
    private BigDecimal liquidationPenaltyAmount;

    @Column(name = "collateral_released_at")
    private LocalDateTime collateralReleasedAt;

    @Column(name = "collateral_released_btc")
    private BigDecimal collateralReleasedBtc;

    @OneToMany(mappedBy = "loan", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<NegotiationMessage> negotiationMessages;
}
