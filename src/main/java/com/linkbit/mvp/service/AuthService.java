package com.linkbit.mvp.service;

import com.linkbit.mvp.domain.KycStatus;
import com.linkbit.mvp.domain.PasswordResetToken;
import com.linkbit.mvp.domain.User;
import com.linkbit.mvp.domain.UserKycDetails;
import com.linkbit.mvp.config.AdminAccessManager;
import com.linkbit.mvp.dto.*;
import com.linkbit.mvp.repository.PasswordResetTokenRepository;
import com.linkbit.mvp.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.authentication.LockedException;
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
    private final LoginAttemptService loginAttemptService;

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
                .role(com.linkbit.mvp.domain.ActorType.BORROWER)
                .isEmailVerified(false)
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
        if (loginAttemptService.isBlocked(request.getEmail())) {
            throw new LockedException("Too many login attempts. Please try again later.");
        }

        try {
            authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(
                            request.getEmail(),
                            request.getPassword()
                    )
            );
        } catch (BadCredentialsException ex) {
            loginAttemptService.recordFailure(request.getEmail());
            throw ex;
        }

        loginAttemptService.reset(request.getEmail());

        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new UsernameNotFoundException("User not found"));

        String jwtToken = jwtService.generateToken(user);
        String refreshToken = jwtService.generateRefreshToken(user);

        return AuthResponse.builder()
                .accessToken(jwtToken)
                .refreshToken(refreshToken)
                .userId(user.getId())
                .kycStatus(user.getKycStatus())
                .email(user.getEmail())
                .role(user.getRole().name())
                .name(user.getPseudonym())
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
                .admin(user.getRole() == com.linkbit.mvp.domain.ActorType.ADMIN)
                .bankDetails(bankDetails)
                .build();
    }

    @Transactional
    public void forgotPassword(String email) {
        User user = userRepository.findByEmail(email).orElse(null);
        if (user == null) {
            return;
        }

        passwordResetTokenRepository.deleteByUser(user);

        String token = UUID.randomUUID().toString();
        PasswordResetToken resetToken = PasswordResetToken.builder()
                .token(token)
                .user(user)
                .expiryDate(LocalDateTime.now().plusMinutes(15))
                .build();

        passwordResetTokenRepository.save(resetToken);
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

    @Transactional
    public void submitKyc(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new UsernameNotFoundException("User not found"));
        
        user.setKycStatus(KycStatus.VERIFIED);
        userRepository.save(user);
    }
}
