package com.linkbit.mvp.repository;

import com.linkbit.mvp.domain.LoanLedger;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface LoanLedgerRepository extends JpaRepository<LoanLedger, UUID> {
    List<LoanLedger> findByLoanIdOrderByCreatedAtAsc(UUID loanId);
}
