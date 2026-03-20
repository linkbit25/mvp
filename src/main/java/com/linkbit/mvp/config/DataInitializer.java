package com.linkbit.mvp.config;

import com.linkbit.mvp.domain.*;
import com.linkbit.mvp.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.UUID;

@Component
@Profile("dev")
@RequiredArgsConstructor
@Slf4j
public class DataInitializer implements CommandLineRunner {

    private final UserRepository userRepository;
    private final LoanOfferRepository loanOfferRepository;
    private final LoanRepository loanRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    public void run(String... args) {
        if (userRepository.count() > 0) {
            log.info("Database already seeded. Skipping data initialization.");
            return;
        }

        log.info("Seeding sample data...");

        // 1. Create Users
        User lender = createUser("lender@linkbit.com", "LenderNode", "9988776655", KycStatus.VERIFIED, ActorType.LENDER);
        User borrower = createUser("borrower@linkbit.com", "SatoshiBorrower", "8877665544", KycStatus.VERIFIED, ActorType.BORROWER);
        User admin = createUser("admin@linkbit.com", "SystemAdmin", "0000000000", KycStatus.VERIFIED, ActorType.ADMIN);
        // System service account — required by CollateralReleaseService.releaseCollateral() on auto-release
        createUser("system@linkbit.internal", "SystemService", "0000000001", KycStatus.VERIFIED, ActorType.SYSTEM);

        // 2. Create Loan Offers
        createOffer(lender, new BigDecimal("100000"), new BigDecimal("12.5"), 50, 90);
        createOffer(lender, new BigDecimal("250000"), new BigDecimal("11.0"), 40, 180);
        LoanOffer connectedOffer = createOffer(lender, new BigDecimal("50000"), new BigDecimal("15.0"), 60, 30);

        // 3. Create an active Loan (Negotiation)
        Loan loan = Loan.builder()
                .offer(connectedOffer)
                .lender(lender)
                .borrower(borrower)
                .principalAmount(connectedOffer.getLoanAmountInr())
                .interestRate(connectedOffer.getInterestRate())
                .tenureDays(connectedOffer.getTenureDays())
                .status(LoanStatus.NEGOTIATING)
                .build();
        loanRepository.save(loan);

        log.info("Sample data seeded successfully.");
    }

    private User createUser(String email, String pseudonym, String phone, KycStatus status, ActorType role) {
        User user = User.builder()
                .email(email)
                .password(passwordEncoder.encode("password123"))
                .phoneNumber(phone)
                .pseudonym(pseudonym)
                .kycStatus(status)
                .role(role)
                .build();

        UserKycDetails kycDetails = UserKycDetails.builder()
                .user(user)
                .fullLegalName(pseudonym + " Full Name")
                .bankAccountNumber("12345678" + (int)(Math.random() * 90 + 10))
                .ifsc("SBIN0001234")
                .upiId(pseudonym.toLowerCase() + "@upi")
                .build();

        user.setKycDetails(kycDetails);
        return userRepository.save(user);
    }

    private LoanOffer createOffer(User lender, BigDecimal amount, BigDecimal interest, int ltv, int tenure) {
        LoanOffer offer = LoanOffer.builder()
                .lender(lender)
                .loanAmountInr(amount)
                .interestRate(interest)
                .expectedLtvPercent(ltv)
                .tenureDays(tenure)
                .status(LoanOfferStatus.OPEN)
                .build();
        return loanOfferRepository.save(offer);
    }
}
