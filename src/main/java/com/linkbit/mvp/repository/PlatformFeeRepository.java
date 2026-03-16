package com.linkbit.mvp.repository;

import com.linkbit.mvp.domain.PlatformFee;
import com.linkbit.mvp.domain.PlatformFeeStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PlatformFeeRepository extends JpaRepository<PlatformFee, UUID> {
    Optional<PlatformFee> findTopByLoanIdAndStatusInOrderByCreatedAtDesc(UUID loanId, Collection<PlatformFeeStatus> statuses);
}
