package com.linkbit.mvp.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.linkbit.mvp.domain.*;
import com.linkbit.mvp.dto.ConnectOfferRequest;
import com.linkbit.mvp.dto.CreateOfferRequest;
import com.linkbit.mvp.repository.LoanOfferRepository;
import com.linkbit.mvp.repository.LoanRepository;
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
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class LoanMarketplaceControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private LoanOfferRepository loanOfferRepository;

    @Autowired
    private LoanRepository loanRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtService jwtService;

    private User verifiedLender;
    private User pendingUser;
    private User borrower;
    private String lenderToken;
    private String borrowerToken;

    @BeforeEach
    void setUp() {
        loanRepository.deleteAll();
        loanOfferRepository.deleteAll();
        userRepository.deleteAll();

        // Create verified lender
        verifiedLender = User.builder()
                .email("lender@example.com")
                .password(passwordEncoder.encode("password"))
                .phoneNumber("1111111111")
                .pseudonym("Lender1")
                .kycStatus(KycStatus.VERIFIED)
                .build();
        userRepository.save(verifiedLender);
        lenderToken = "Bearer " + jwtService.generateToken(verifiedLender);

        // Create unverified user
        pendingUser = User.builder()
                .email("pending@example.com")
                .password(passwordEncoder.encode("password"))
                .phoneNumber("2222222222")
                .pseudonym("PendingUser")
                .kycStatus(KycStatus.PENDING)
                .build();
        userRepository.save(pendingUser);

        // Create borrower
        borrower = User.builder()
                .email("borrower@example.com")
                .password(passwordEncoder.encode("password"))
                .phoneNumber("3333333333")
                .pseudonym("Borrower1")
                .kycStatus(KycStatus.VERIFIED)
                .build();
        userRepository.save(borrower);
        borrowerToken = "Bearer " + jwtService.generateToken(borrower);
    }

    @Test
    void shouldCreateOfferSuccessfully() throws Exception {
        CreateOfferRequest request = new CreateOfferRequest();
        request.setLoanAmountInr(new BigDecimal("100000"));
        request.setInterestRate(new BigDecimal("12.5"));
        request.setExpectedLtvPercent(60);
        request.setTenureDays(90);

        mockMvc.perform(post("/offers")
                .header("Authorization", lenderToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated());
    }

    @Test
    void shouldFailCreateOfferIfNotVerified() throws Exception {
        CreateOfferRequest request = new CreateOfferRequest();
        request.setLoanAmountInr(new BigDecimal("100000"));
        request.setInterestRate(new BigDecimal("12.5"));
        request.setExpectedLtvPercent(60);
        request.setTenureDays(90);

        String token = "Bearer " + jwtService.generateToken(pendingUser);

        mockMvc.perform(post("/offers")
                .header("Authorization", token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isInternalServerError()); // Or whatever exception handler returns for RuntimeException
    }

    @Test
    void shouldGetOpenOffers() throws Exception {
        // Create an offer manually
        LoanOffer offer = LoanOffer.builder()
                .lender(verifiedLender)
                .loanAmountInr(new BigDecimal("50000"))
                .interestRate(new BigDecimal("10.0"))
                .expectedLtvPercent(50)
                .tenureDays(30)
                .status(LoanOfferStatus.OPEN)
                .build();
        loanOfferRepository.save(offer);

        mockMvc.perform(get("/offers")
                .header("Authorization", borrowerToken)
                .param("amount", "50000"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].offer_id").exists())
                .andExpect(jsonPath("$[0].loan_amount").value(50000));
    }

    @Test
    void shouldConnectToOffer() throws Exception {
        LoanOffer offer = LoanOffer.builder()
                .lender(verifiedLender)
                .loanAmountInr(new BigDecimal("50000"))
                .interestRate(new BigDecimal("10.0"))
                .expectedLtvPercent(50)
                .tenureDays(30)
                .status(LoanOfferStatus.OPEN)
                .build();
        loanOfferRepository.save(offer);

        ConnectOfferRequest request = new ConnectOfferRequest();
        request.setOfferId(offer.getId());

        mockMvc.perform(post("/loans/connect")
                .header("Authorization", borrowerToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated());
    }
}
