package com.linkbit.mvp.domain;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "loan_audit_log")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LoanAuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "loan_id", nullable = false)
    private UUID loanId;

    @Enumerated(EnumType.STRING)
    @Column(name = "previous_state")
    private LoanStatus previousState;

    @Enumerated(EnumType.STRING)
    @Column(name = "new_state", nullable = false)
    private LoanStatus newState;

    @Enumerated(EnumType.STRING)
    @Column(name = "action", nullable = false)
    private LoanAction action;

    @Enumerated(EnumType.STRING)
    @Column(name = "actor", nullable = false)
    private ActorType actor;

    @CreationTimestamp
    @Column(name = "timestamp", updatable = false)
    private LocalDateTime timestamp;
}
