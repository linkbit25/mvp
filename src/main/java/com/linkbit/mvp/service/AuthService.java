package com.linkbit.mvp.service;

import com.linkbit.mvp.domain.KycStatus;
import com.linkbit.mvp.domain.PasswordResetToken;
import com.linkbit.mvp.domain.User;
import com.linkbit.mvp.domain.UserKycDetails;
import com.linkbit.mvp.dto.*;
import com.linkbit.mvp.repository.PasswordResetTokenRepository;
import com.linkbit.mvp.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordResetTokenRepository passwordResetTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AuthenticationManager authenticationManager;

    @Transactional
    public void register(RegisterRequest request) {
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new RuntimeException("Email already exists");
        }

        User user = User.builder()
                .email(request.getEmail())
                .password(passwordEncoder.encode(request.getPassword()))
                .phoneNumber(request.getPhoneNumber())
                .pseudonym(request.getPseudonym())
                .kycStatus(KycStatus.PENDING)
                .build();

        UserKycDetails kycDetails = UserKycDetails.builder()
                .user(user)
                .bankAccountNumber(request.getBankAccountNumber())
                .ifsc(request.getIfsc())
                .upiId(request.getUpiId())
                .build();

        user.setKycDetails(kycDetails);

        userRepository.save(user);
    }

    public AuthResponse login(LoginRequest request) {
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(
                        request.getEmail(),
                        request.getPassword()
                )
        );

        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new UsernameNotFoundException("User not found"));

        String jwtToken = jwtService.generateToken(user);
        String refreshToken = jwtService.generateRefreshToken(user);

        return AuthResponse.builder()
                .accessToken(jwtToken)
                .refreshToken(refreshToken)
                .userId(user.getId())
                .kycStatus(user.getKycStatus())
                .build();
    }

    public UserResponse me(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new UsernameNotFoundException("User not found"));

        UserKycDetails kycDetails = user.getKycDetails();

        UserResponse.BankDetails bankDetails = null;
        if (kycDetails != null) {
            bankDetails = UserResponse.BankDetails.builder()
                    .bankAccountNumber(kycDetails.getBankAccountNumber())
                    .ifsc(kycDetails.getIfsc())
                    .upiId(kycDetails.getUpiId())
                    .build();
        }

        return UserResponse.builder()
                .userId(user.getId())
                .email(user.getEmail())
                .phoneNumber(user.getPhoneNumber())
                .pseudonym(user.getPseudonym())
                .kycStatus(user.getKycStatus())
                .bankDetails(bankDetails)
                .build();
    }

    @Transactional
    public String forgotPassword(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new UsernameNotFoundException("User not found"));

        String token = UUID.randomUUID().toString();
        PasswordResetToken resetToken = PasswordResetToken.builder()
                .token(token)
                .user(user)
                .expiryDate(LocalDateTime.now().plusMinutes(15))
                .build();

        passwordResetTokenRepository.save(resetToken);

        return token;
    }

    @Transactional
    public void resetPassword(ResetPasswordRequest request) {
        PasswordResetToken resetToken = passwordResetTokenRepository.findByToken(request.getToken())
                .orElseThrow(() -> new RuntimeException("Invalid token"));

        if (resetToken.getExpiryDate().isBefore(LocalDateTime.now())) {
            throw new RuntimeException("Token expired");
        }

        User user = resetToken.getUser();
        user.setPassword(passwordEncoder.encode(request.getNewPassword()));
        userRepository.save(user);

        passwordResetTokenRepository.delete(resetToken);
    }
}
