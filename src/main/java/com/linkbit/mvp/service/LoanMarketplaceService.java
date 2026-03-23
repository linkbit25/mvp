package com.linkbit.mvp.service;

import com.linkbit.mvp.domain.KycStatus;
import com.linkbit.mvp.domain.Loan;
import com.linkbit.mvp.domain.LoanOffer;
import com.linkbit.mvp.domain.LoanOfferStatus;
import com.linkbit.mvp.domain.LoanStatus;
import com.linkbit.mvp.domain.User;
import com.linkbit.mvp.dto.CreateOfferRequest;
import com.linkbit.mvp.dto.OfferResponse;
import com.linkbit.mvp.repository.LoanOfferRepository;
import com.linkbit.mvp.repository.LoanRepository;
import com.linkbit.mvp.repository.UserRepository;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class LoanMarketplaceService {

    private static final List<LoanStatus> BLOCKING_OFFER_STATUSES = List.of(
            LoanStatus.NEGOTIATING,
            LoanStatus.AWAITING_SIGNATURES,
            LoanStatus.AWAITING_FEE,
            LoanStatus.AWAITING_COLLATERAL,
            LoanStatus.COLLATERAL_LOCKED,
            LoanStatus.ACTIVE,
            LoanStatus.DISPUTE_OPEN,
            LoanStatus.MARGIN_CALL,
            LoanStatus.LIQUIDATION_ELIGIBLE,
            LoanStatus.REPAID,
            LoanStatus.LIQUIDATED,
            LoanStatus.CLOSED
    );

    private final LoanOfferRepository loanOfferRepository;
    private final LoanRepository loanRepository;
    private final UserRepository userRepository;

    @Transactional
    public void createOffer(String email, CreateOfferRequest request) {
        User lender = getUser(email);
        if (lender.getKycStatus() != KycStatus.VERIFIED) {
            throw new RuntimeException("User is not verified");
        }

        loanOfferRepository.save(LoanOffer.builder()
                .lender(lender)
                .loanAmountInr(request.getLoanAmountInr())
                .interestRate(request.getInterestRate())
                .expectedLtvPercent(request.getExpectedLtvPercent())
                .tenureDays(request.getTenureDays())
                .status(LoanOfferStatus.OPEN)
                .build());
    }

    @Transactional(readOnly = true)
    public List<OfferResponse> getOpenOffers(BigDecimal amount, Integer tenureDays, BigDecimal interestRate, Integer expectedLtv) {
        Specification<LoanOffer> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("status"), LoanOfferStatus.OPEN));

            if (amount != null) {
                predicates.add(cb.equal(root.get("loanAmountInr"), amount));
            }
            if (tenureDays != null) {
                predicates.add(cb.equal(root.get("tenureDays"), tenureDays));
            }
            if (interestRate != null) {
                predicates.add(cb.equal(root.get("interestRate"), interestRate));
            }
            if (expectedLtv != null) {
                predicates.add(cb.equal(root.get("expectedLtvPercent"), expectedLtv));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };

        return loanOfferRepository.findAll(spec, Sort.by(Sort.Direction.ASC, "interestRate")).stream()
                .map(this::mapToOfferResponse)
                .collect(Collectors.toList());
    }

    @Transactional
    public void editOffer(String email, UUID offerId, CreateOfferRequest request) {
        User lender = getUser(email);
        LoanOffer offer = loanOfferRepository.findById(offerId)
                .orElseThrow(() -> new RuntimeException("Offer not found"));

        if (!offer.getLender().getId().equals(lender.getId())) {
            throw new RuntimeException("Unauthorized to edit this offer");
        }
        if (offer.getStatus() != LoanOfferStatus.OPEN) {
            throw new RuntimeException("Offer is not OPEN");
        }
        if (loanRepository.existsByOfferAndStatusNot(offer, LoanStatus.CANCELLED)) {
            throw new RuntimeException("Cannot edit offer with existing negotiations");
        }

        offer.setLoanAmountInr(request.getLoanAmountInr());
        offer.setInterestRate(request.getInterestRate());
        offer.setExpectedLtvPercent(request.getExpectedLtvPercent());
        offer.setTenureDays(request.getTenureDays());
        loanOfferRepository.save(offer);
    }

    @Transactional
    public UUID connectOffer(String email, UUID offerId) {
        User borrower = getUser(email);

        if (borrower.getKycStatus() != KycStatus.VERIFIED) {
            throw new RuntimeException("KYC verification required to connect to a loan offer");
        }

        // Use pessimistic lock to prevent concurrent connections to the same offer
        LoanOffer offer = loanOfferRepository.findByIdForUpdate(offerId)
                .orElseThrow(() -> new RuntimeException("Offer not found"));

        if (offer.getStatus() != LoanOfferStatus.OPEN) {
            throw new RuntimeException("Offer is no longer available");
        }
        if (offer.getLender().getId().equals(borrower.getId())) {
            throw new RuntimeException("Lender cannot connect to their own offer");
        }
        if (loanRepository.existsByOfferAndStatusIn(offer, BLOCKING_OFFER_STATUSES)) {
            throw new RuntimeException("Offer already has an active loan negotiation");
        }

        Loan savedLoan = loanRepository.save(Loan.builder()
                .offer(offer)
                .lender(offer.getLender())
                .borrower(borrower)
                .principalAmount(offer.getLoanAmountInr())
                .interestRate(offer.getInterestRate())
                .tenureDays(offer.getTenureDays())
                .expectedLtvPercent(offer.getExpectedLtvPercent())
                .totalOutstanding(BigDecimal.ZERO)
                .repaymentType(com.linkbit.mvp.domain.RepaymentType.BULLET)
                .emiCount(1)
                .marginCallLtvPercent(70)
                .liquidationLtvPercent(85)
                .status(LoanStatus.NEGOTIATING)
                .build());
        return savedLoan.getId();
    }

    private User getUser(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new UsernameNotFoundException("User not found"));
    }

    private OfferResponse mapToOfferResponse(LoanOffer offer) {
        return OfferResponse.builder()
                .offerId(offer.getId())
                .lenderId(offer.getLender().getId())
                .lenderPseudonym(offer.getLender().getPseudonym())
                .loanAmount(offer.getLoanAmountInr())
                .interestRate(offer.getInterestRate())
                .expectedLtv(offer.getExpectedLtvPercent())
                .tenureDays(offer.getTenureDays())
                .build();
    }
}
