package com.linkbit.mvp.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.linkbit.mvp.domain.*;
import com.linkbit.mvp.dto.PayFeeRequest;
import com.linkbit.mvp.repository.LoanOfferRepository;
import com.linkbit.mvp.repository.LoanRepository;
import com.linkbit.mvp.repository.PlatformFeeRepository;
import com.linkbit.mvp.repository.UserRepository;
import com.linkbit.mvp.service.JwtService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public class PaymentControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private LoanOfferRepository loanOfferRepository;

    @Autowired
    private LoanRepository loanRepository;

    @Autowired
    private PlatformFeeRepository platformFeeRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtService jwtService;

    private User borrower;
    private User lender;
    private User admin;
    private Loan loan;
    private String borrowerToken;
    private String lenderToken;
    private String adminToken;

    @BeforeEach
    void setUp() {
        platformFeeRepository.deleteAll();
        loanRepository.deleteAll();
        loanOfferRepository.deleteAll();
        userRepository.deleteAll();

        lender = User.builder()
                .email("lender@example.com")
                .password(passwordEncoder.encode("password"))
                .phoneNumber("1111111111")
                .pseudonym("Lender1")
                .kycStatus(KycStatus.VERIFIED)
                .build();
        userRepository.save(lender);
        lenderToken = "Bearer " + jwtService.generateToken(lender);

        borrower = User.builder()
                .email("borrower@example.com")
                .password(passwordEncoder.encode("password"))
                .phoneNumber("3333333333")
                .pseudonym("Borrower1")
                .kycStatus(KycStatus.VERIFIED)
                .build();
        userRepository.save(borrower);
        borrowerToken = "Bearer " + jwtService.generateToken(borrower);

        admin = User.builder()
                .email("admin@example.com")
                .password(passwordEncoder.encode("password"))
                .phoneNumber("9999999999")
                .pseudonym("AdminUser")
                .kycStatus(KycStatus.VERIFIED)
                .build();
        userRepository.save(admin);
        adminToken = "Bearer " + jwtService.generateToken(admin);

        LoanOffer offer = LoanOffer.builder()
                .lender(lender)
                .loanAmountInr(new BigDecimal("100000"))
                .interestRate(new BigDecimal("12.0"))
                .expectedLtvPercent(60)
                .tenureDays(90)
                .status(LoanOfferStatus.OPEN)
                .build();
        loanOfferRepository.save(offer);

        loan = Loan.builder()
                .offer(offer)
                .lender(lender)
                .borrower(borrower)
                .principalAmount(new BigDecimal("100000"))
                .interestRate(new BigDecimal("12.0"))
                .tenureDays(90)
                .status(LoanStatus.AWAITING_FEE)
                .build();
        loanRepository.save(loan);
    }

    @Test
    void shouldInitiateFeePaymentSuccessfully() throws Exception {
        PayFeeRequest request = new PayFeeRequest();
        request.setLoanId(loan.getId());

        mockMvc.perform(post("/payments/fee/pay")
                .header("Authorization", borrowerToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.amount_inr").value(2000.0)) // 2% of 100,000
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.fee_id").exists());
    }

    @Test
    void shouldFailInitiateFeePaymentIfNotBorrower() throws Exception {
        PayFeeRequest request = new PayFeeRequest();
        request.setLoanId(loan.getId());

        mockMvc.perform(post("/payments/fee/pay")
                .header("Authorization", lenderToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isInternalServerError());
    }

    @Test
    void shouldVerifyPaymentAndTransitionStatus() throws Exception {
        // Act: Initiate payment
        PayFeeRequest request = new PayFeeRequest();
        request.setLoanId(loan.getId());

        String responseJson = mockMvc.perform(post("/payments/fee/pay")
                .header("Authorization", borrowerToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        String feeId = objectMapper.readTree(responseJson).get("fee_id").asText();

        // Act: Verify payment
        mockMvc.perform(post("/admin/payments/" + feeId + "/verify")
                .header("Authorization", adminToken))
                .andExpect(status().isOk());

        // Assert: Check loan status manually matching the ID
        Loan updatedLoan = loanRepository.findById(loan.getId()).orElseThrow();
        assert updatedLoan.getStatus() == LoanStatus.AWAITING_COLLATERAL;

        PlatformFee updatedFee = platformFeeRepository.findById(java.util.UUID.fromString(feeId)).orElseThrow();
        assert updatedFee.getStatus() == PlatformFeeStatus.SUCCESS;
    }
}
