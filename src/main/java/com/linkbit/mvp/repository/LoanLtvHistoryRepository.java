package com.linkbit.mvp.repository;

import com.linkbit.mvp.domain.LoanLtvHistory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface LoanLtvHistoryRepository extends JpaRepository<LoanLtvHistory, UUID> {
}
