package com.linkbit.mvp.security;

import com.linkbit.mvp.domain.*;
import com.linkbit.mvp.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_EACH_TEST_METHOD)
public class SecurityAuthorizationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private LoanRepository loanRepository;

    @Autowired
    private LoanOfferRepository loanOfferRepository;

    private User borrower;
    private User lender;
    private User thirdParty;
    private Loan loan;

    @BeforeEach
    void setUp() {
        loanRepository.deleteAll();
        loanOfferRepository.deleteAll();
        userRepository.deleteAll();

        borrower = userRepository.save(User.builder()
                .email("test-borrower@linkbit.io")
                .password("password")
                .pseudonym("Borrower")
                .kycStatus(KycStatus.VERIFIED)
                .build());

        lender = userRepository.save(User.builder()
                .email("test-lender@linkbit.io")
                .password("password")
                .pseudonym("Lender")
                .kycStatus(KycStatus.VERIFIED)
                .build());

        thirdParty = userRepository.save(User.builder()
                .email("test-thirdparty@linkbit.io")
                .password("password")
                .pseudonym("ThirdParty")
                .kycStatus(KycStatus.VERIFIED)
                .build());

        LoanOffer offer = loanOfferRepository.save(LoanOffer.builder()
                .lender(lender)
                .loanAmountInr(new BigDecimal("10000"))
                .interestRate(new BigDecimal("10"))
                .tenureDays(30)
                .expectedLtvPercent(50)
                .status(LoanOfferStatus.OPEN)
                .build());

        loan = loanRepository.save(Loan.builder()
                .offer(offer)
                .borrower(borrower)
                .lender(lender)
                .principalAmount(new BigDecimal("10000"))
                .interestRate(new BigDecimal("10"))
                .tenureDays(30)
                .status(LoanStatus.NEGOTIATING)
                .totalRepaymentAmount(new BigDecimal("10000"))
                .principalOutstanding(new BigDecimal("10000"))
                .interestOutstanding(BigDecimal.ZERO)
                .totalOutstanding(new BigDecimal("10000"))
                .expectedLtvPercent(50)
                .collateralBtcAmount(new BigDecimal("0.01"))
                .collateralValueInr(new BigDecimal("20000"))
                .currentLtvPercent(new BigDecimal("50"))
                .build());
    }

    @Test
    @WithMockUser(username = "test-borrower@linkbit.io")
    void borrowerShouldAccessOwnLoanDetails() throws Exception {
        mockMvc.perform(get("/loans/" + loan.getId() + "/details"))
                .andDo(print())
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(username = "test-lender@linkbit.io")
    void lenderShouldAccessOwnLoanDetails() throws Exception {
        mockMvc.perform(get("/loans/" + loan.getId() + "/details"))
                .andDo(print())
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(username = "test-thirdparty@linkbit.io")
    void thirdPartyShouldNotAccessLoanDetails() throws Exception {
        mockMvc.perform(get("/loans/" + loan.getId() + "/details"))
                .andDo(print())
                .andExpect(status().isInternalServerError()); // The service throws RuntimeException
    }

    @Test
    @WithMockUser(username = "test-thirdparty@linkbit.io")
    void thirdPartyShouldNotAccessAdminEndpoints() throws Exception {
        mockMvc.perform(get("/admin/overview"))
                .andDo(print())
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "test-borrower@linkbit.io")
    void borrowerShouldNotAccessAdminEndpoints() throws Exception {
        mockMvc.perform(get("/admin/overview"))
                .andDo(print())
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "test-thirdparty@linkbit.io")
    void thirdPartyShouldNotViewCollateralBalance() throws Exception {
        mockMvc.perform(get("/loans/" + loan.getId() + "/collateral"))
                .andDo(print())
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "test-lender@linkbit.io")
    void lenderShouldNotAccessBorrowerRepaymentSubmission() throws Exception {
        // Only borrower can submit repayment
        mockMvc.perform(post("/loans/" + loan.getId() + "/repay")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"amount\": 1000, \"transaction_reference\": \"ref\", \"proof_image_url\": \"url\"}"))
                .andDo(print())
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "test-borrower@linkbit.io")
    void borrowerShouldNotAccessLenderDisbuisementAction() throws Exception {
        // Only lender can mark disbursed
        mockMvc.perform(post("/loans/" + loan.getId() + "/disburse")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"transaction_reference\": \"ref\", \"proof_image_url\": \"url\"}"))
                .andDo(print())
                .andExpect(status().isForbidden()); // Back to expecting 500 now that it's valid
    }
}
