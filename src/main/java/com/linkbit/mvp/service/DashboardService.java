package com.linkbit.mvp.service;

import com.linkbit.mvp.domain.Loan;
import com.linkbit.mvp.domain.LoanRepayment;
import com.linkbit.mvp.domain.LoanStatus;
import com.linkbit.mvp.domain.PlatformFee;
import com.linkbit.mvp.domain.PlatformFeeStatus;
import com.linkbit.mvp.domain.RepaymentStatus;
import com.linkbit.mvp.domain.User;
import com.linkbit.mvp.dto.AdminOverviewResponse;
import com.linkbit.mvp.dto.LoanDetailResponse;
import com.linkbit.mvp.dto.LoanSummaryResponse;
import com.linkbit.mvp.dto.PendingFeeResponse;
import com.linkbit.mvp.dto.PendingRepaymentResponse;
import com.linkbit.mvp.repository.EscrowAccountRepository;
import com.linkbit.mvp.repository.LoanRepaymentRepository;
import com.linkbit.mvp.repository.LoanRepository;
import com.linkbit.mvp.repository.PlatformFeeRepository;
import com.linkbit.mvp.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

@Service
@RequiredArgsConstructor
public class DashboardService {

    private final UserRepository userRepository;
    private final LoanRepository loanRepository;
    private final PlatformFeeRepository platformFeeRepository;
    private final LoanRepaymentRepository loanRepaymentRepository;
    private final EscrowAccountRepository escrowAccountRepository;

    public Page<LoanSummaryResponse> getMyLoans(String email, Pageable pageable) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new UsernameNotFoundException("User not found"));

        List<LoanSummaryResponse> summaries = loanRepository.findDashboardLoansByUserId(user.getId()).stream()
                .map(loan -> toSummary(loan, user.getId()))
                .toList();

        int start = (int) pageable.getOffset();
        int end = Math.min(start + pageable.getPageSize(), summaries.size());
        List<LoanSummaryResponse> page = start >= summaries.size() ? List.of() : summaries.subList(start, end);
        return new PageImpl<>(page, pageable, summaries.size());
    }

    public LoanDetailResponse getLoanDetail(String email, UUID loanId) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new UsernameNotFoundException("User not found: " + email));
        Loan loan = findLoan(loanId);

        boolean isBorrower = loan.getBorrower().getId().equals(user.getId());
        boolean isLender = loan.getLender().getId().equals(user.getId());
        if (!isBorrower && !isLender) {
            throw new RuntimeException("You are not part of this loan");
        }

        return toDetail(loan, isBorrower ? "BORROWER" : "LENDER");
    }

    public AdminOverviewResponse getAdminOverview() {
        List<Loan> loans = loanRepository.findAll().stream()
                .sorted((left, right) -> right.getUpdatedAt().compareTo(left.getUpdatedAt()))
                .toList();

        List<PlatformFee> pendingFees = platformFeeRepository.findByStatusOrderByCreatedAtDesc(PlatformFeeStatus.PENDING);
        List<LoanRepayment> pendingRepayments = loanRepaymentRepository.findByStatusOrderByCreatedAtDesc(RepaymentStatus.PENDING);

        long activeRiskCases = loans.stream()
                .filter(loan -> List.of(LoanStatus.MARGIN_CALL, LoanStatus.LIQUIDATION_ELIGIBLE, LoanStatus.DISPUTE_OPEN).contains(loan.getStatus()))
                .count();

        long collateralAwaitingVerification = loans.stream()
                .filter(loan -> loan.getStatus() == LoanStatus.AWAITING_COLLATERAL || loan.getStatus() == LoanStatus.COLLATERAL_LOCKED)
                .count();

        return AdminOverviewResponse.builder()
                .metrics(AdminOverviewResponse.Metrics.builder()
                        .totalLoans(loans.size())
                        .pendingFees(pendingFees.size())
                        .pendingRepayments(pendingRepayments.size())
                        .activeRiskCases(activeRiskCases)
                        .collateralAwaitingVerification(collateralAwaitingVerification)
                        .build())
                .loans(loans.stream().map(loan -> toDetail(loan, "ADMIN")).toList())
                .build();
    }

    private Loan findLoan(UUID loanId) {
        return loanRepository.findById(loanId)
                .orElseThrow(() -> new RuntimeException("Loan not found"));
    }

    private LoanSummaryResponse toSummary(Loan loan, UUID userId) {
        boolean isBorrower = loan.getBorrower().getId().equals(userId);
        String counterparty = isBorrower ? loan.getLender().getPseudonym() : loan.getBorrower().getPseudonym();

        return LoanSummaryResponse.builder()
                .loanId(loan.getId())
                .role(isBorrower ? "BORROWER" : "LENDER")
                .status(loan.getStatus())
                .borrowerPseudonym(loan.getBorrower().getPseudonym())
                .lenderPseudonym(loan.getLender().getPseudonym())
                .counterpartyPseudonym(counterparty)
                .principalAmount(loan.getPrincipalAmount())
                .interestRate(loan.getInterestRate())
                .tenureDays(loan.getTenureDays())
                .repaymentType(loan.getRepaymentType())
                .emiCount(loan.getEmiCount())
                .emiAmount(loan.getEmiAmount())
                .totalRepaymentAmount(loan.getTotalRepaymentAmount())
                .totalOutstanding(loan.getTotalOutstanding())
                .expectedLtvPercent(loan.getExpectedLtvPercent())
                .currentLtvPercent(loan.getCurrentLtvPercent())
                .collateralBtcAmount(loan.getCollateralBtcAmount())
                .collateralValueInr(loan.getCollateralValueInr())
                .agreementHash(loan.getAgreementHash())
                .disbursementReference(loan.getDisbursementReference())
                .createdAt(loan.getCreatedAt())
                .updatedAt(loan.getUpdatedAt())
                .build();
    }

    private LoanDetailResponse toDetail(Loan loan, String role) {
        var escrowAccount = escrowAccountRepository.findByLoanId(loan.getId()).orElse(null);
        var pendingFee = platformFeeRepository
                .findTopByLoanIdAndStatusInOrderByCreatedAtDesc(loan.getId(), List.of(PlatformFeeStatus.PENDING))
                .map(this::toPendingFee)
                .orElse(null);

        var pendingRepayments = loanRepaymentRepository.findByLoanIdOrderByCreatedAtDesc(loan.getId()).stream()
                .filter(repayment -> repayment.getStatus() == RepaymentStatus.PENDING)
                .map(this::toPendingRepayment)
                .toList();

        return LoanDetailResponse.builder()
                .loanId(loan.getId())
                .role(role)
                .status(loan.getStatus())
                .borrowerPseudonym(loan.getBorrower().getPseudonym())
                .lenderPseudonym(loan.getLender().getPseudonym())
                .principalAmount(loan.getPrincipalAmount())
                .interestRate(loan.getInterestRate())
                .tenureDays(loan.getTenureDays())
                .repaymentType(loan.getRepaymentType())
                .emiCount(loan.getEmiCount())
                .emiAmount(loan.getEmiAmount())
                .totalRepaymentAmount(loan.getTotalRepaymentAmount())
                .principalOutstanding(loan.getPrincipalOutstanding())
                .interestOutstanding(loan.getInterestOutstanding())
                .totalOutstanding(loan.getTotalOutstanding())
                .expectedLtvPercent(loan.getExpectedLtvPercent())
                .marginCallLtvPercent(loan.getMarginCallLtvPercent())
                .liquidationLtvPercent(loan.getLiquidationLtvPercent())
                .collateralBtcAmount(loan.getCollateralBtcAmount())
                .collateralValueInr(loan.getCollateralValueInr())
                .currentLtvPercent(loan.getCurrentLtvPercent())
                .agreementHash(loan.getAgreementHash())
                .agreementFinalizedAt(loan.getAgreementFinalizedAt())
                .createdAt(loan.getCreatedAt())
                .updatedAt(loan.getUpdatedAt())
                .fiatDisbursedAt(loan.getFiatDisbursedAt())
                .fiatReceivedConfirmedAt(loan.getFiatReceivedConfirmedAt())
                .disbursementReference(loan.getDisbursementReference())
                .disbursementProofUrl(loan.getDisbursementProofUrl())
                .collateralReleasedBtc(loan.getCollateralReleasedBtc())
                .collateralReleasedAt(loan.getCollateralReleasedAt())
                .escrowAddress(escrowAccount != null ? escrowAccount.getEscrowAddress() : null)
                .escrowBalanceSats(escrowAccount != null ? escrowAccount.getCurrentBalanceSats() : null)
                .pendingFee(pendingFee)
                .pendingRepayments(pendingRepayments)
                .build();
    }

    private PendingFeeResponse toPendingFee(PlatformFee fee) {
        return PendingFeeResponse.builder()
                .feeId(fee.getId())
                .amountInr(fee.getAmountInr())
                .status(fee.getStatus())
                .createdAt(fee.getCreatedAt())
                .build();
    }

    private PendingRepaymentResponse toPendingRepayment(LoanRepayment repayment) {
        return PendingRepaymentResponse.builder()
                .repaymentId(repayment.getId())
                .amountInr(repayment.getAmountInr())
                .transactionReference(repayment.getTransactionReference())
                .proofUrl(repayment.getProofUrl())
                .status(repayment.getStatus())
                .createdAt(repayment.getCreatedAt())
                .build();
    }
}
