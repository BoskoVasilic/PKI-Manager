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

    private final ActivationTokenRepository tokenRepo;
    private final UserRepository userRepo;
    private final AuthenticationManager authenticationManager;
    private final UserRepository userRepository;
    private final JwtUtil jwtUtil;
    PasswordEncoder passwordEncoder;

    public void validateToken(String token) {
        ActivationToken at = tokenRepo.findByToken(token)
                .orElseThrow(() -> new RuntimeException("Invalid token"));

        if (at.isUsed() || at.getExpiresAt().isBefore(LocalDateTime.now()))
            throw new RuntimeException("Token expired or used");
    }

    public void activateCaUser(String token, String newPassword) {
        ActivationToken at = tokenRepo.findByToken(token)
                .orElseThrow(() -> new RuntimeException("Invalid token"));

        if (at.isUsed() || at.getExpiresAt().isBefore(LocalDateTime.now()))
            throw new RuntimeException("Token expired or used");

        User user = userRepo.findById(at.getUserId()).orElseThrow();
        user.setPassword(passwordEncoder.encode(newPassword));
        user.setEnabled(true);
        userRepo.save(user);

        at.setUsed(true);
        tokenRepo.save(at);
    }

    public String login(String email, String password) {
        try {
            authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(email, password)
            );
        } catch (AuthenticationException e) {
            throw new BadCredentialsException("Invalid email or password");
        }

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found"));

        return jwtUtil.generateAccessToken(user);
    }
}
