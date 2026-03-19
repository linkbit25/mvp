package com.linkbit.mvp.repository;

import com.linkbit.mvp.domain.Loan;
import com.linkbit.mvp.domain.LoanOffer;
import com.linkbit.mvp.domain.LoanStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import jakarta.persistence.LockModeType;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface LoanRepository extends JpaRepository<Loan, UUID> {
    List<Loan> findByOffer(LoanOffer offer);
    boolean existsByOffer(LoanOffer offer);
    boolean existsByOfferAndStatusNot(LoanOffer offer, LoanStatus status);
    boolean existsByOfferAndStatusIn(LoanOffer offer, List<LoanStatus> statuses);
    List<Loan> findByStatusIn(List<LoanStatus> statuses);
    Page<Loan> findByStatusIn(List<LoanStatus> statuses, Pageable pageable);
    List<Loan> findByStatusAndUpdatedAtBefore(LoanStatus status, LocalDateTime threshold);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<Loan> findByIdWithLock(UUID id);

    @Query("""
            select l from Loan l
            where l.borrower.id = :userId or l.lender.id = :userId
            order by l.updatedAt desc, l.createdAt desc
            """)
    List<Loan> findDashboardLoansByUserId(@Param("userId") java.util.UUID userId);

    @Query("SELECT COUNT(l) > 0 FROM Loan l WHERE l.id = :id AND (l.borrower.email = :email OR l.lender.email = :email)")
    boolean isParticipant(@Param("id") UUID id, @Param("email") String email);
}
