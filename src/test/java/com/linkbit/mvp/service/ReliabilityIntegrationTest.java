package com.linkbit.mvp.service;

import com.linkbit.mvp.domain.*;
import com.linkbit.mvp.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
public class ReliabilityIntegrationTest {

    @Autowired
    private StateMachineService stateMachineService;

    @Autowired
    private LoanRepository loanRepository;

    @Autowired
    private LoanAuditLogRepository auditLogRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private LoanOfferRepository loanOfferRepository;

    private Loan testLoan;

    @BeforeEach
    void setUp() {
        // @Transactional will handle cleanup via rollback, but manual cleanup is safer for some DBs
        auditLogRepository.deleteAll();
        loanRepository.deleteAll();
        loanOfferRepository.deleteAll();
        userRepository.deleteAll();

        User lender = userRepository.save(User.builder().email("lender@test.com").password("pass").phoneNumber("1").pseudonym("L1").kycStatus(KycStatus.VERIFIED).build());
        User borrower = userRepository.save(User.builder().email("borrower@test.com").password("pass").phoneNumber("2").pseudonym("B1").kycStatus(KycStatus.VERIFIED).build());

        LoanOffer offer = loanOfferRepository.save(LoanOffer.builder()
                .lender(lender)
                .loanAmountInr(new BigDecimal("1000"))
                .interestRate(new BigDecimal("10"))
                .tenureDays(30)
                .expectedLtvPercent(60)
                .status(LoanOfferStatus.OPEN)
                .build());

        testLoan = loanRepository.save(Loan.builder()
                .offer(offer)
                .lender(lender)
                .borrower(borrower)
                .principalAmount(new BigDecimal("1000"))
                .interestRate(new BigDecimal("10"))
                .tenureDays(30)
                .status(LoanStatus.AWAITING_FEE)
                .build());
    }

    @Test
    void shouldBeIdempotentOnDuplicateTransitions() {
        // First transition: AWAITING_FEE -> AWAITING_COLLATERAL (via PAY_FEE)
        stateMachineService.transition(testLoan, LoanAction.PAY_FEE, ActorType.BORROWER);
        loanRepository.save(testLoan); // Manual save since we aren't using a service wrapper
        
        assertThat(testLoan.getStatus()).isEqualTo(LoanStatus.AWAITING_COLLATERAL);

        List<LoanAuditLog> logs = auditLogRepository.findByLoanId(testLoan.getId());
        assertThat(logs).hasSize(1);
        assertThat(logs.get(0).getNewState()).isEqualTo(LoanStatus.AWAITING_COLLATERAL);

        // Second transition (Retry): Should be a NO-OP
        stateMachineService.transition(testLoan, LoanAction.PAY_FEE, ActorType.BORROWER);
        loanRepository.save(testLoan);
        
        // Verify state remains same
        assertThat(testLoan.getStatus()).isEqualTo(LoanStatus.AWAITING_COLLATERAL);
        
        List<LoanAuditLog> logsAfterRetry = auditLogRepository.findByLoanId(testLoan.getId());
        assertThat(logsAfterRetry).hasSize(1); 
    }

    @Test
    void shouldBeIdempotentOnAdminTransitions() {
        stateMachineService.adminTransition(testLoan, LoanStatus.CANCELLED);
        loanRepository.save(testLoan);
        
        assertThat(testLoan.getStatus()).isEqualTo(LoanStatus.CANCELLED);

        stateMachineService.adminTransition(testLoan, LoanStatus.CANCELLED);
        loanRepository.save(testLoan);
        
        assertThat(testLoan.getStatus()).isEqualTo(LoanStatus.CANCELLED);

        List<LoanAuditLog> logs = auditLogRepository.findByLoanId(testLoan.getId());
        assertThat(logs).hasSize(1);
    }

    @Test
    void shouldMaintainConsistencyOnPartialFailureSimulatedByRetry() {
        // 1. Initial State: AWAITING_FEE
        assertThat(testLoan.getStatus()).isEqualTo(LoanStatus.AWAITING_FEE);

        // 2. First attempt (Simulated "crash" after transition committed)
        stateMachineService.transition(testLoan, LoanAction.PAY_FEE, ActorType.BORROWER);
        loanRepository.save(testLoan); 
        
        // 3. Verify state is updated in DB
        Loan dbLoan = loanRepository.findById(testLoan.getId()).orElseThrow();
        assertThat(dbLoan.getStatus()).isEqualTo(LoanStatus.AWAITING_COLLATERAL);

        // 4. Retry the exact same action
        stateMachineService.transition(dbLoan, LoanAction.PAY_FEE, ActorType.BORROWER);
        loanRepository.save(dbLoan);

        // 5. Final verification
        assertThat(dbLoan.getStatus()).isEqualTo(LoanStatus.AWAITING_COLLATERAL);
        List<LoanAuditLog> logs = auditLogRepository.findByLoanId(testLoan.getId());
        assertThat(logs).hasSize(1);
    }
}
