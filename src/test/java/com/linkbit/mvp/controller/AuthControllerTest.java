package com.linkbit.mvp.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.linkbit.mvp.dto.LoginRequest;
import com.linkbit.mvp.dto.RegisterRequest;
import com.linkbit.mvp.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        userRepository.deleteAll();
    }

    @Test
    void shouldRegisterUserSuccessfully() throws Exception {
        RegisterRequest request = new RegisterRequest();
        request.setEmail("test@example.com");
        request.setPassword("password123");
        request.setPhoneNumber("1234567890");
        request.setPseudonym("TestUser");
        request.setBankAccountNumber("123456789012");
        request.setIfsc("IFSC0001234");
        request.setUpiId("test@upi");

        mockMvc.perform(post("/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated());
    }

    @Test
    void shouldLoginUserSuccessfully() throws Exception {
        // First register
        RegisterRequest registerRequest = new RegisterRequest();
        registerRequest.setEmail("login@example.com");
        registerRequest.setPassword("password123");
        registerRequest.setPhoneNumber("9876543210");
        registerRequest.setPseudonym("LoginUser");
        registerRequest.setBankAccountNumber("098765432109");
        registerRequest.setIfsc("IFSC0004321");
        registerRequest.setUpiId("login@upi");

        mockMvc.perform(post("/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(registerRequest)))
                .andExpect(status().isCreated());

        // Then login
        LoginRequest loginRequest = new LoginRequest();
        loginRequest.setEmail("login@example.com");
        loginRequest.setPassword("password123");

        mockMvc.perform(post("/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").exists())
                .andExpect(jsonPath("$.refreshToken").exists())
                .andExpect(jsonPath("$.userId").exists())
                .andExpect(jsonPath("$.kycStatus").value("PENDING"));
    }

    @Test
    void shouldFailLoginWithWrongPassword() throws Exception {
        // First register
        RegisterRequest registerRequest = new RegisterRequest();
        registerRequest.setEmail("wrongpass@example.com");
        registerRequest.setPassword("password123");
        registerRequest.setPhoneNumber("5555555555");
        registerRequest.setPseudonym("WrongPassUser");
        registerRequest.setBankAccountNumber("555555555555");
        registerRequest.setIfsc("IFSC0005555");
        registerRequest.setUpiId("wrong@upi");

        mockMvc.perform(post("/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(registerRequest)))
                .andExpect(status().isCreated());

        // Then login with wrong password
        LoginRequest loginRequest = new LoginRequest();
        loginRequest.setEmail("wrongpass@example.com");
        loginRequest.setPassword("wrongpassword");

        mockMvc.perform(post("/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isUnauthorized());
    }
}
