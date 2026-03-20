package com.linkbit.mvp.service;

import com.linkbit.mvp.domain.Loan;
import com.linkbit.mvp.domain.LoanOffer;
import com.linkbit.mvp.domain.LoanOfferStatus;
import com.linkbit.mvp.domain.LoanStatus;
import com.linkbit.mvp.domain.LoanAction;
import com.linkbit.mvp.domain.ActorType;
import com.linkbit.mvp.domain.RepaymentType;
import com.linkbit.mvp.domain.User;
import com.linkbit.mvp.dto.UpdateTermsRequest;
import com.linkbit.mvp.dto.AgreementResponse;
import com.linkbit.mvp.repository.LoanOfferRepository;
import com.linkbit.mvp.repository.LoanRepository;
import com.linkbit.mvp.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class NegotiationService {

    private final LoanRepository loanRepository;
    private final LoanOfferRepository loanOfferRepository;
    private final UserRepository userRepository;
    private final ChatService chatService;
    private final StateMachineService stateMachineService;

    @Transactional
    public void updateTerms(String email, UUID loanId, UpdateTermsRequest request) {
        Loan loan = getLoan(loanId);
        User lender = getUser(email);

        if (!loan.getLender().getId().equals(lender.getId()) && !loan.getBorrower().getId().equals(lender.getId())) {
            throw new RuntimeException("Only participants can update terms");
        }
        if (loan.getStatus() != LoanStatus.NEGOTIATING) {
            throw new RuntimeException("Loan is not in negotiating status");
        }

        loan.setPrincipalAmount(request.getPrincipalAmount());
        loan.setInterestRate(request.getInterestRate());
        loan.setTenureDays(request.getTenureDays());
        loan.setRepaymentType(request.getRepaymentType());
        loan.setEmiCount(request.getEmiCount());
        loan.setExpectedLtvPercent(request.getExpectedLtvPercent());
        loan.setMarginCallLtvPercent(request.getMarginCallLtvPercent());
        loan.setLiquidationLtvPercent(request.getLiquidationLtvPercent());
        loanRepository.save(loan);

        chatService.sendSystemMessage(loanId, "SYSTEM: Terms updated by " + lender.getPseudonym());
    }

    @Transactional
    public void finalizeContract(String email, UUID loanId) {
        Loan loan = getLoan(loanId);
        User lender = getUser(email);

        if (!loan.getLender().getId().equals(lender.getId()) && !loan.getBorrower().getId().equals(lender.getId())) {
            throw new RuntimeException("Only participants can finalize contract");
        }
        if (loan.getStatus() != LoanStatus.NEGOTIATING) {
            throw new RuntimeException("Loan is not in negotiating status");
        }

        // Lock the offer first to ensure atomicity
        LoanOffer offer = loanOfferRepository.findByIdForUpdate(loan.getOffer().getId())
                .orElseThrow(() -> new RuntimeException("Offer not found"));
        
        if (offer.getStatus() != LoanOfferStatus.OPEN) {
            throw new RuntimeException("Offer is no longer open for finalization");
        }

        if (loan.getRepaymentType() == null || loan.getExpectedLtvPercent() == null
                || loan.getMarginCallLtvPercent() == null || loan.getLiquidationLtvPercent() == null) {
            throw new RuntimeException("Loan terms are incomplete");
        }

        calculateRepayment(loan);
        LocalDateTime finalizedAt = LocalDateTime.now();
        loan.setAgreementFinalizedAt(finalizedAt);
        loan.setAgreementHash(generateHash(buildAgreementData(loan, finalizedAt)));
        
        ActorType actorType = loan.getLender().getId().equals(lender.getId()) ? ActorType.LENDER : ActorType.BORROWER;
        stateMachineService.transition(loan, LoanAction.FINALIZE_CONTRACT, actorType);

        offer.setStatus(LoanOfferStatus.CLOSED);
        loanOfferRepository.save(offer);
        loanRepository.save(loan);

        chatService.sendSystemMessage(loanId, "SYSTEM: Contract finalized. Awaiting signatures.");
    }

    @Transactional
    public void signContract(String email, UUID loanId, String signature) {
        Loan loan = getLoan(loanId);
        User user = getUser(email);

        if (loan.getStatus() != LoanStatus.AWAITING_SIGNATURES) {
            throw new RuntimeException("Loan is not awaiting signatures");
        }
        if (loan.getAgreementHash() == null || loan.getAgreementFinalizedAt() == null) {
            throw new RuntimeException("Agreement must be finalized before signatures");
        }

        if (loan.getBorrower().getId().equals(user.getId())) {
            if (loan.getBorrowerSignature() != null) {
                throw new RuntimeException("Borrower already signed");
            }
            loan.setBorrowerSignature(signature);
            loan.setBorrowerSignedAt(LocalDateTime.now());
        } else if (loan.getLender().getId().equals(user.getId())) {
            if (loan.getLenderSignature() != null) {
                throw new RuntimeException("Lender already signed");
            }
            loan.setLenderSignature(signature);
            loan.setLenderSignedAt(LocalDateTime.now());
        } else {
            throw new RuntimeException("User is not a participant");
        }

        if (loan.getBorrowerSignature() != null && loan.getLenderSignature() != null) {
            ActorType signerType = user.getId().equals(loan.getBorrower().getId()) ? ActorType.BORROWER : ActorType.LENDER;
            stateMachineService.transition(loan, LoanAction.SIGN_CONTRACT, signerType);
            chatService.sendSystemMessage(loanId, "SYSTEM: Contract signed by both parties. Loan status: AWAITING_FEE");
        } else {
            chatService.sendSystemMessage(loanId, "SYSTEM: Contract signed by " + user.getPseudonym());
        }

        loanRepository.save(loan);
    }

    @Transactional(readOnly = true)
    public void cancelNegotiation(String email, UUID loanId) {
        Loan loan = getLoan(loanId);
        User user = getUser(email);

        if (!loan.getBorrower().getId().equals(user.getId())) {
            throw new RuntimeException("Only borrower can cancel negotiation");
        }
        if (loan.getStatus() != LoanStatus.NEGOTIATING) {
            throw new RuntimeException("Cannot cancel loan in current status");
        }

        stateMachineService.transition(loan, LoanAction.CANCEL_NEGOTIATION, ActorType.BORROWER);
        loanRepository.save(loan);

        LoanOffer offer = loanOfferRepository.findByIdForUpdate(loan.getOffer().getId())
                .orElseThrow(() -> new RuntimeException("Offer not found"));
        offer.setStatus(LoanOfferStatus.OPEN);
        loanOfferRepository.save(offer);

        chatService.sendSystemMessage(loanId, "SYSTEM: Negotiation cancelled by borrower");
    }

    @Transactional(readOnly = true)
    public AgreementResponse getAgreement(String email, UUID loanId) {
        Loan loan = getLoan(loanId);
        User user = getUser(email);

        if (!loan.getBorrower().getId().equals(user.getId()) && !loan.getLender().getId().equals(user.getId())) {
             throw new RuntimeException("Only participants can view agreement");
        }

        return AgreementResponse.builder()
                .agreementHash(loan.getAgreementHash())
                .borrowerSignature(loan.getBorrowerSignature())
                .borrowerSignedAt(loan.getBorrowerSignedAt())
                .lenderSignature(loan.getLenderSignature())
                .lenderSignedAt(loan.getLenderSignedAt())
                .agreementFinalizedAt(loan.getAgreementFinalizedAt())
                .build();
    }

    private Loan getLoan(UUID loanId) {
        return loanRepository.findById(loanId)
                .orElseThrow(() -> new RuntimeException("Loan not found"));
    }

    private User getUser(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new UsernameNotFoundException("User not found"));
    }

    private void calculateRepayment(Loan loan) {
        BigDecimal principal = loan.getPrincipalAmount();
        BigDecimal rate = loan.getInterestRate();
        Integer tenureDays = loan.getTenureDays();

        if (loan.getRepaymentType() == RepaymentType.BULLET) {
            BigDecimal annualRate = rate.divide(new BigDecimal("100"), 10, RoundingMode.HALF_UP);
            BigDecimal timeInYears = new BigDecimal(tenureDays).divide(new BigDecimal("365"), 10, RoundingMode.HALF_UP);
            BigDecimal total = principal.add(principal.multiply(annualRate).multiply(timeInYears));
            loan.setEmiCount(1);
            loan.setEmiAmount(total.setScale(2, RoundingMode.HALF_UP));
            loan.setTotalRepaymentAmount(total.setScale(2, RoundingMode.HALF_UP));
            return;
        }

        int emiCount = loan.getEmiCount() != null && loan.getEmiCount() > 0 ? loan.getEmiCount() : Math.max(1, tenureDays / 30);
        loan.setEmiCount(emiCount);

        BigDecimal annualRate = rate.divide(new BigDecimal("100"), 10, RoundingMode.HALF_UP);
        BigDecimal monthlyRate = annualRate.divide(new BigDecimal("12"), 10, RoundingMode.HALF_UP);
        if (monthlyRate.compareTo(BigDecimal.ZERO) == 0) {
            BigDecimal emi = principal.divide(new BigDecimal(emiCount), 2, RoundingMode.HALF_UP);
            loan.setEmiAmount(emi);
            loan.setTotalRepaymentAmount(emi.multiply(new BigDecimal(emiCount)));
            return;
        }

        BigDecimal onePlusR = BigDecimal.ONE.add(monthlyRate);
        BigDecimal onePlusRToN = onePlusR.pow(emiCount);
        BigDecimal emi = principal.multiply(monthlyRate).multiply(onePlusRToN)
                .divide(onePlusRToN.subtract(BigDecimal.ONE), 2, RoundingMode.HALF_UP);

        loan.setEmiAmount(emi);
        loan.setTotalRepaymentAmount(emi.multiply(new BigDecimal(emiCount)));
    }

    private String buildAgreementData(Loan loan, LocalDateTime finalizedAt) {
        return loan.getBorrower().getId() + ":" +
                loan.getLender().getId() + ":" +
                loan.getPrincipalAmount() + ":" +
                loan.getInterestRate() + ":" +
                loan.getTenureDays() + ":" +
                loan.getRepaymentType() + ":" +
                loan.getEmiCount() + ":" +
                loan.getEmiAmount() + ":" +
                loan.getTotalRepaymentAmount() + ":" +
                loan.getExpectedLtvPercent() + ":" +
                loan.getMarginCallLtvPercent() + ":" +
                loan.getLiquidationLtvPercent() + ":" +
                finalizedAt;
    }

    private String generateHash(String data) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return bytesToHex(digest.digest(data.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 algorithm not found", e);
        }
    }

    private String bytesToHex(byte[] hash) {
        StringBuilder hexString = new StringBuilder(2 * hash.length);
        for (byte value : hash) {
            String hex = Integer.toHexString(0xff & value);
            if (hex.length() == 1) {
                hexString.append('0');
            }
            hexString.append(hex);
        }
        return hexString.toString();
    }
}
