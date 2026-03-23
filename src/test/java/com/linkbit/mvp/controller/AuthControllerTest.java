package com.linkbit.mvp.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.linkbit.mvp.dto.ForgotPasswordRequest;
import com.linkbit.mvp.dto.LoginRequest;
import com.linkbit.mvp.dto.RegisterRequest;
import com.linkbit.mvp.dto.ResetPasswordRequest;
import com.linkbit.mvp.repository.PasswordResetTokenRepository;
import com.linkbit.mvp.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordResetTokenRepository passwordResetTokenRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        passwordResetTokenRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    void shouldRegisterUserSuccessfully() throws Exception {
        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(registerRequest("test@example.com"))))
                .andDo(org.springframework.test.web.servlet.result.MockMvcResultHandlers.print())
                .andExpect(status().isCreated());

        var savedUser = userRepository.findByEmail("test@example.com").orElseThrow();
        assertThat(savedUser.getPassword()).isNotEqualTo("password123");
        assertThat(savedUser.getKycStatus().name()).isEqualTo("PENDING");
        assertThat(savedUser.getKycDetails()).isNotNull();
    }

    @Test
    void shouldLoginUserSuccessfullyAndReturnProfile() throws Exception {
        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(registerRequest("login@example.com"))))
                .andExpect(status().isCreated());

        LoginRequest loginRequest = new LoginRequest();
        loginRequest.setEmail("login@example.com");
        loginRequest.setPassword("password123");

        String response = mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").exists())
                .andExpect(jsonPath("$.refreshToken").exists())
                .andExpect(jsonPath("$.userId").exists())
                .andExpect(jsonPath("$.kycStatus").value("PENDING"))
                .andReturn()
                .getResponse()
                .getContentAsString();

        JsonNode json = objectMapper.readTree(response);
        mockMvc.perform(get("/auth/me")
                        .header("Authorization", "Bearer " + json.get("accessToken").asText()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("login@example.com"))
                .andExpect(jsonPath("$.bankDetails.upiId").value("test@upi"));
    }

    @Test
    void shouldHidePasswordResetTokenAndResetPassword() throws Exception {
        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(registerRequest("forgot@example.com"))))
                .andExpect(status().isCreated());

        ForgotPasswordRequest forgotRequest = new ForgotPasswordRequest();
        forgotRequest.setEmail("forgot@example.com");

        mockMvc.perform(post("/auth/password/forgot")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(forgotRequest)))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$").doesNotExist());

        var token = passwordResetTokenRepository.findAll().get(0).getToken();

        ResetPasswordRequest resetPasswordRequest = new ResetPasswordRequest();
        resetPasswordRequest.setToken(token);
        resetPasswordRequest.setNewPassword("newPassword123");

        mockMvc.perform(post("/auth/password/reset")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(resetPasswordRequest)))
                .andExpect(status().isOk());

        LoginRequest loginRequest = new LoginRequest();
        loginRequest.setEmail("forgot@example.com");
        loginRequest.setPassword("newPassword123");

        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isOk());
    }

    @Test
    void shouldRateLimitRepeatedFailedLogins() throws Exception {
        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(registerRequest("wrongpass@example.com"))))
                .andExpect(status().isCreated());

        LoginRequest badLogin = new LoginRequest();
        badLogin.setEmail("wrongpass@example.com");
        badLogin.setPassword("wrongpassword");

        for (int attempt = 0; attempt < 5; attempt++) {
            mockMvc.perform(post("/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(badLogin)))
                    .andExpect(status().isUnauthorized());
        }

        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(badLogin)))
                .andExpect(status().isTooManyRequests());
    }

    private RegisterRequest registerRequest(String email) {
        RegisterRequest request = new RegisterRequest();
        request.setName("Test User");
        request.setEmail(email);
        request.setPassword("password123");
        request.setDob("1990-01-01");
        request.setPhoneNumber("1234567890");
        request.setPseudonym("TestUser");
        request.setBankAccountNumber("123456789012");
        request.setIfsc("IFSC0001234");
        request.setUpiId("test@upi");
        return request;
    }
}
