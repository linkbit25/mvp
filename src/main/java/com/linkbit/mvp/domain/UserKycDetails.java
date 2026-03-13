package com.linkbit.mvp.domain;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "user_kyc_details")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserKycDetails {

    @Id
    @Column(name = "user_id")
    private UUID userId;

    @OneToOne
    @MapsId
    @JoinColumn(name = "user_id")
    private User user;

    @Column(name = "full_legal_name")
    private String fullLegalName;

    @Column(name = "bank_account_number")
    private String bankAccountNumber;

    private String ifsc;

    @Column(name = "upi_id")
    private String upiId;

    @Column(name = "national_id_hash")
    private String nationalIdHash;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
}
