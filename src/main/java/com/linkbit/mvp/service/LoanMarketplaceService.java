package com.linkbit.mvp.service;

import com.linkbit.mvp.domain.*;
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

    private final LoanOfferRepository loanOfferRepository;
    private final LoanRepository loanRepository;
    private final UserRepository userRepository;

    @Transactional
    public void createOffer(String email, CreateOfferRequest request) {
        User lender = userRepository.findByEmail(email)
                .orElseThrow(() -> new UsernameNotFoundException("User not found"));

        if (lender.getKycStatus() != KycStatus.VERIFIED) {
            throw new RuntimeException("User is not verified");
        }

        LoanOffer offer = LoanOffer.builder()
                .lender(lender)
                .loanAmountInr(request.getLoanAmountInr())
                .interestRate(request.getInterestRate())
                .expectedLtvPercent(request.getExpectedLtvPercent())
                .tenureDays(request.getTenureDays())
                .status(LoanOfferStatus.OPEN)
                .build();

        loanOfferRepository.save(offer);
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

        Sort sort = Sort.by(Sort.Direction.ASC, "interestRate");

        return loanOfferRepository.findAll(spec, sort).stream()
                .map(this::mapToOfferResponse)
                .collect(Collectors.toList());
    }

    private OfferResponse mapToOfferResponse(LoanOffer offer) {
        return OfferResponse.builder()
                .offerId(offer.getId())
                .lenderPseudonym(offer.getLender().getPseudonym())
                .loanAmount(offer.getLoanAmountInr())
                .interestRate(offer.getInterestRate())
                .expectedLtv(offer.getExpectedLtvPercent())
                .tenureDays(offer.getTenureDays())
                .build();
    }

    @Transactional
    public void editOffer(String email, UUID offerId, CreateOfferRequest request) {
        User lender = userRepository.findByEmail(email)
                .orElseThrow(() -> new UsernameNotFoundException("User not found"));

        LoanOffer offer = loanOfferRepository.findById(offerId)
                .orElseThrow(() -> new RuntimeException("Offer not found"));

        if (!offer.getLender().getId().equals(lender.getId())) {
            throw new RuntimeException("Unauthorized to edit this offer");
        }

        if (offer.getStatus() != LoanOfferStatus.OPEN) {
            throw new RuntimeException("Offer is not OPEN");
        }

        if (loanRepository.existsByOffer(offer)) {
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
        User borrower = userRepository.findByEmail(email)
                .orElseThrow(() -> new UsernameNotFoundException("User not found"));

        LoanOffer offer = loanOfferRepository.findById(offerId)
                .orElseThrow(() -> new RuntimeException("Offer not found"));

        if (offer.getStatus() != LoanOfferStatus.OPEN) {
            throw new RuntimeException("Offer is not available");
        }

        if (offer.getLender().getId().equals(borrower.getId())) {
            throw new RuntimeException("Lender cannot connect to their own offer");
        }

        Loan loan = Loan.builder()
                .offer(offer)
                .lender(offer.getLender())
                .borrower(borrower)
                .principalAmount(offer.getLoanAmountInr())
                .interestRate(offer.getInterestRate())
                .tenureDays(offer.getTenureDays())
                .status(LoanStatus.NEGOTIATING)
                .build();

        Loan savedLoan = loanRepository.save(loan);
        return savedLoan.getId();
    }
}
