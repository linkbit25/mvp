package com.linkbit.mvp.repository;

import com.linkbit.mvp.domain.LoanEmi;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface LoanEmiRepository extends JpaRepository<LoanEmi, UUID> {
    List<LoanEmi> findByLoanIdOrderByEmiNumberAsc(UUID loanId);
}
