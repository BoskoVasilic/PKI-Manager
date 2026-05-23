package com.tim12.pk_infrastructure.service;

import com.tim12.pk_infrastructure.model.ActivationToken;
import com.tim12.pk_infrastructure.model.User;
import com.tim12.pk_infrastructure.repository.ActivationTokenRepository;
import com.tim12.pk_infrastructure.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

import com.tim12.pk_infrastructure.security.JwtUtil;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.AuthenticationException;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final ActivationTokenRepository tokenRepository;
    private final AuthenticationManager authenticationManager;
    private final UserRepository userRepository;
    private final JwtUtil jwtUtil;
    private final PasswordEncoder passwordEncoder;

    public void validateToken(String token) {
        ActivationToken at = tokenRepository.findByToken(token)
                .orElseThrow(() -> new RuntimeException("Invalid token"));

        if (at.isUsed() || at.getExpiresAt().isBefore(LocalDateTime.now()))
            throw new RuntimeException("Token expired or used");
    }

    public void activateCaUser(String token, String newPassword) {
        ActivationToken at = tokenRepository.findByToken(token)
                .orElseThrow(() -> new RuntimeException("Invalid token"));

        if (at.isUsed() || at.getExpiresAt().isBefore(LocalDateTime.now()))
            throw new RuntimeException("Token expired or used");

        User user = userRepository.findById(at.getUserId()).orElseThrow();
        user.setPassword(passwordEncoder.encode(newPassword));
        user.setEnabled(true);
        userRepository.save(user);

        at.setUsed(true);
        tokenRepository.save(at);
    }

    public LoginResult login(String email, String password) {
        try {
            authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(email, password)
            );
        } catch (AuthenticationException e) {
            throw new BadCredentialsException("Invalid email or password");
        }

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found"));

        if (user.isTwoFactorEnabled()) {
            // Pre-auth token only — refresh token issued after 2FA completes
            return new LoginResult(jwtUtil.generatePreAuthToken(user), null, true);
        }

        return new LoginResult(
                jwtUtil.generateAccessToken(user),
                jwtUtil.generateRefreshToken(user),
                false
        );
    }

    public LoginResult refresh(String refreshToken) {
        if (!jwtUtil.isTokenValid(refreshToken) || !jwtUtil.isRefreshToken(refreshToken)) {
            throw new BadCredentialsException("Invalid or expired refresh token");
        }

        String email = jwtUtil.extractEmail(refreshToken);
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found"));

        return new LoginResult(
                jwtUtil.generateAccessToken(user),
                jwtUtil.generateRefreshToken(user),
                false
        );
    }

    public record LoginResult(String accessToken, String refreshToken, boolean twoFaRequired) {}
}