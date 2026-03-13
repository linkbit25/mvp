package com.linkbit.mvp.repository;

import com.linkbit.mvp.domain.BitcoinTransaction;
import com.linkbit.mvp.domain.BitcoinTransactionType;
import com.linkbit.mvp.domain.Loan;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface BitcoinTransactionRepository extends JpaRepository<BitcoinTransaction, String> {
    List<BitcoinTransaction> findByLoanId(java.util.UUID loanId);
    List<BitcoinTransaction> findByLoanAndTypeAndStatus(Loan loan, BitcoinTransactionType type, String status);
}
