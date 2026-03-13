package com.linkbit.mvp.repository;

import com.linkbit.mvp.domain.LoanRepayment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface LoanRepaymentRepository extends JpaRepository<LoanRepayment, UUID> {
    List<LoanRepayment> findByLoanIdOrderByCreatedAtDesc(UUID loanId);
}
