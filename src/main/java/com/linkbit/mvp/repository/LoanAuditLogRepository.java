package com.linkbit.mvp.repository;

import com.linkbit.mvp.domain.LoanAuditLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface LoanAuditLogRepository extends JpaRepository<LoanAuditLog, Long> {
    List<LoanAuditLog> findByLoanId(UUID loanId);
}
