package com.linkbit.mvp.repository;

import com.linkbit.mvp.domain.ActorType;
import com.linkbit.mvp.domain.PlatformFee;
import com.linkbit.mvp.domain.PlatformFeeStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PlatformFeeRepository extends JpaRepository<PlatformFee, UUID> {
    Optional<PlatformFee> findTopByLoanIdAndPayerRoleAndStatusInOrderByCreatedAtDesc(UUID loanId, ActorType payerRole, Collection<PlatformFeeStatus> statuses);
    List<PlatformFee> findByLoanIdAndStatus(UUID loanId, PlatformFeeStatus status);
    List<PlatformFee> findByStatusOrderByCreatedAtDesc(PlatformFeeStatus status);
}
