package com.linkbit.mvp.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class RegisterRequest {

    @NotBlank(message = "Email is required")
    @Email(message = "Invalid email format")
    private String email;

    @NotBlank(message = "Password is required")
    private String password;

    @NotBlank(message = "Phone number is required")
    private String phoneNumber;

    @NotBlank(message = "Pseudonym is required")
    private String pseudonym;

    @NotBlank(message = "Bank account number is required")
    private String bankAccountNumber;

    @NotBlank(message = "IFSC code is required")
    private String ifsc;

    @NotBlank(message = "UPI ID is required")
    private String upiId;
}
