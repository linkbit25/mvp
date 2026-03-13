package com.linkbit.mvp.repository;

import com.linkbit.mvp.domain.Loan;
import com.linkbit.mvp.domain.LoanMarginCall;
import com.linkbit.mvp.domain.MarginCallStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface LoanMarginCallRepository extends JpaRepository<LoanMarginCall, UUID> {
    Optional<LoanMarginCall> findFirstByLoanAndStatusOrderByTriggeredAtDesc(Loan loan, MarginCallStatus status);
    List<LoanMarginCall> findByLoanAndStatusIn(Loan loan, List<MarginCallStatus> statuses);
}
