package com.linkbit.mvp.repository;

import com.linkbit.mvp.domain.Loan;
import com.linkbit.mvp.domain.LoanOffer;
import com.linkbit.mvp.domain.LoanStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface LoanRepository extends JpaRepository<Loan, UUID> {
    List<Loan> findByOffer(LoanOffer offer);
    boolean existsByOffer(LoanOffer offer);
    boolean existsByOfferAndStatusNot(LoanOffer offer, LoanStatus status);
    boolean existsByOfferAndStatusIn(LoanOffer offer, List<LoanStatus> statuses);
    List<Loan> findByStatusIn(List<LoanStatus> statuses);
}
