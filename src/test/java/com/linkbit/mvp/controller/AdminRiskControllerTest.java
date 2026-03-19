package com.linkbit.mvp.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.linkbit.mvp.domain.*;
import com.linkbit.mvp.dto.SetRiskStateRequest;
import com.linkbit.mvp.repository.LoanOfferRepository;
import com.linkbit.mvp.repository.LoanRepository;
import com.linkbit.mvp.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
public class AdminRiskControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private LoanOfferRepository loanOfferRepository;

    @Autowired
    private LoanRepository loanRepository;

    private Loan activeLoan;

    @BeforeEach
    void setUp() {
        User lender = userRepository.save(User.builder().email("lender_adminrisk@example.com").password("pass").phoneNumber("1").pseudonym("L1").kycStatus(KycStatus.VERIFIED).build());
        User borrower = userRepository.save(User.builder().email("borrower_adminrisk@example.com").password("pass").phoneNumber("2").pseudonym("B1").kycStatus(KycStatus.VERIFIED).build());

        LoanOffer offer = loanOfferRepository.save(LoanOffer.builder()
                .lender(lender)
                .loanAmountInr(new BigDecimal("100"))
                .interestRate(new BigDecimal("10"))
                .tenureDays(30)
                .expectedLtvPercent(100)
                .status(LoanOfferStatus.OPEN)
                .build());

        activeLoan = loanRepository.save(Loan.builder()
                .offer(offer)
                .lender(lender)
                .borrower(borrower)
                .principalAmount(offer.getLoanAmountInr())
                .interestRate(offer.getInterestRate())
                .tenureDays(30)
                .totalOutstanding(new BigDecimal("100"))
                .collateralBtcAmount(new BigDecimal("1.5"))
                .status(LoanStatus.ACTIVE)
                .build());
    }

    @Test
    @WithMockUser(username = "admin@example.com")
    void adminShouldBeAbleToOverrideRiskState() throws Exception {
        SetRiskStateRequest req = new SetRiskStateRequest(LoanStatus.MARGIN_CALL);

        mockMvc.perform(post("/admin/loans/{loanId}/set-risk-state", activeLoan.getId())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isAccepted());

        Loan l = loanRepository.findById(activeLoan.getId()).orElseThrow();
        assert l.getStatus() == LoanStatus.MARGIN_CALL;
    }

    @Test
    void unauthenticatedUserShouldBeForbiddenFromAdminRiskEndpoint() throws Exception {
        SetRiskStateRequest req = new SetRiskStateRequest(LoanStatus.LIQUIDATION_ELIGIBLE);

        mockMvc.perform(post("/admin/loans/{loanId}/set-risk-state", activeLoan.getId())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isForbidden());
    }
}
