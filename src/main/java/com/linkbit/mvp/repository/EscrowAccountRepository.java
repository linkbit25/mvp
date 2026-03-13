package com.linkbit.mvp.repository;

import com.linkbit.mvp.domain.EscrowAccount;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface EscrowAccountRepository extends JpaRepository<EscrowAccount, UUID> {

    @Modifying
    @Query(value = "INSERT INTO escrow_accounts (loan_id, escrow_address, current_balance_sats, created_at) VALUES (:loanId, :address, :balance, CURRENT_TIMESTAMP)", nativeQuery = true)
    void insertEscrowAccount(@Param("loanId") UUID loanId, @Param("address") String address, @Param("balance") Long balance);
    
    Optional<EscrowAccount> findByLoanId(UUID loanId);
}
