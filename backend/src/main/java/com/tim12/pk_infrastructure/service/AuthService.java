package com.tim12.pk_infrastructure.service;

import com.tim12.pk_infrastructure.model.ActivationToken;
import com.tim12.pk_infrastructure.model.User;
import com.tim12.pk_infrastructure.repository.ActivationTokenRepository;
import com.tim12.pk_infrastructure.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final ActivationTokenRepository tokenRepo;
    private final UserRepository userRepo;
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
}
