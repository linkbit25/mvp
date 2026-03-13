package com.linkbit.mvp.repository;

import com.linkbit.mvp.domain.LoanOffer;
import com.linkbit.mvp.domain.LoanOfferStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface LoanOfferRepository extends JpaRepository<LoanOffer, UUID>, JpaSpecificationExecutor<LoanOffer> {
    List<LoanOffer> findByStatus(LoanOfferStatus status);
}
