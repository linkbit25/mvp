package com.linkbit.mvp.repository;

import com.linkbit.mvp.domain.Loan;
import com.linkbit.mvp.domain.NegotiationMessage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface NegotiationMessageRepository extends JpaRepository<NegotiationMessage, Long> {
    List<NegotiationMessage> findByLoanOrderBySentAtAsc(Loan loan);
}
