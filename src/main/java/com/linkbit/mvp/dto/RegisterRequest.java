package com.linkbit.mvp.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class RegisterRequest {

    @NotBlank(message = "Name is required")
    private String name;

    @NotBlank(message = "Email is required")
    @Email(message = "Invalid email format")
    private String email;

    @NotBlank(message = "Password is required")
    private String password;

    @NotBlank(message = "Date of birth is required")
    private String dob;

    private String phoneNumber;

    private String pseudonym;

    private String bankAccountNumber;

    private String ifsc;

    private String upiId;
}
