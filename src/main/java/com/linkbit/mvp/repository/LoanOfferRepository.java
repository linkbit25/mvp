package com.linkbit.mvp.repository;

import com.linkbit.mvp.domain.LoanOffer;
import com.linkbit.mvp.domain.LoanOfferStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface LoanOfferRepository extends JpaRepository<LoanOffer, UUID>, JpaSpecificationExecutor<LoanOffer> {
    List<LoanOffer> findByStatus(LoanOfferStatus status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT o FROM LoanOffer o WHERE o.id = :id")
    Optional<LoanOffer> findByIdForUpdate(@Param("id") UUID id);
}
