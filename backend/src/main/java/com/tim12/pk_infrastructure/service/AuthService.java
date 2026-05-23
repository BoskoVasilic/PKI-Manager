package com.tim12.pk_infrastructure.service;

import com.tim12.pk_infrastructure.model.ActivationToken;
import com.tim12.pk_infrastructure.model.Organization;
import com.tim12.pk_infrastructure.model.User;
import com.tim12.pk_infrastructure.model.dtos.*;
import com.tim12.pk_infrastructure.model.enums.Role;
import com.tim12.pk_infrastructure.repository.ActivationTokenRepository;
import com.tim12.pk_infrastructure.repository.OrganizatioRepository;
import com.tim12.pk_infrastructure.repository.UserRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.UUID;

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
    private final CryptoService cryptoService;
    private final EmailService emailService;
    private final OrganizatioRepository organizatioRepository;

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
            return new LoginResult(jwtUtil.generatePreAuthToken(user), true);
        }

        return new LoginResult(jwtUtil.generateAccessToken(user), false);
    }

    public record LoginResult(String token, boolean twoFaRequired) {}

    @Transactional
    public void register(RegisterRequestDTO req) {

        if (userRepository.existsByEmail(req.getEmail())) {
            throw new RuntimeException("A user with this email already exists.");
        }

        if (!req.getPassword().equals(req.getConfirmPassword())) {
            throw new RuntimeException("Passwords do not match.");
        }

        Organization org = organizatioRepository.findByName(req.getOrganizationName()).orElseThrow(() ->
                new RuntimeException("Organization '" + req.getOrganizationName() + "' not found.")
        );

        validatePassword(req.getPassword());

        if (!cryptoService.isValidPublicKey(req.getPublicKeyPem())) {
            throw new RuntimeException("Invalid public key format. Use an RSA public key in PEM format.");
        }

        String plainChallenge = cryptoService.generateChallenge();
        String encryptedChallenge = cryptoService.encryptWithPublicKey(
                plainChallenge, req.getPublicKeyPem()
        );

        User user = User.builder()
                .email(req.getEmail())
                .password(passwordEncoder.encode(req.getPassword()))
                .firstName(req.getFirstName())
                .lastName(req.getLastName())
                .organization(org)
                .role(Role.USER)
                .enabled(false)
                .publicKey(req.getPublicKeyPem())
                .activationChallenge(encryptedChallenge)
                .plainChallenge(plainChallenge)
                .build();

        userRepository.save(user);

        String tokenValue = UUID.randomUUID().toString();
        ActivationToken token = ActivationToken.builder()
                .token(tokenValue)
                .userId(user.getId())
                .expiresAt(LocalDateTime.now().plusHours(24))
                .used(false)
                .tokenType("REGISTRATION")
                .build();

        tokenRepository.save(token);

        emailService.sendRegistrationActivationEmail(user.getEmail(), tokenValue);
    }


    public ChallengeResponseDTO getRegistrationChallenge(String tokenValue) {
        ActivationToken token = validateToken(tokenValue, "REGISTRATION");
        User user = userRepository.findById(token.getUserId())
                .orElseThrow(() -> new RuntimeException("User not found."));

        if (user.isEnabled()) {
            throw new RuntimeException("Account is already activated.");
        }

        return new ChallengeResponseDTO(user.getActivationChallenge(), tokenValue);
    }

    @Transactional
    public void activateAccount(ActivateAccountRequestDTO req) {
        ActivationToken token = validateToken(req.getToken(), "REGISTRATION");
        User user = userRepository.findById(token.getUserId())
                .orElseThrow(() -> new RuntimeException("User not found."));

        if (user.isEnabled()) {
            throw new RuntimeException("Account is already activated.");
        }

        if (!user.getPlainChallenge().equals(req.getDecryptedChallenge().trim())) {
            throw new RuntimeException("Verification failed. Please ensure you are using the correct private key.");
        }

        user.setEnabled(true);
        user.setPlainChallenge(null);
        userRepository.save(user);

        token.setUsed(true);
        tokenRepository.save(token);
    }


    @Transactional
    public void initiateForgotPassword(ForgotPasswordRequestDTO req) {
        User user = userRepository.findByEmail(req.getEmail()).orElse(null);
        if (user == null || !user.isEnabled()) return;

        String plainChallenge = cryptoService.generateChallenge();
        String encryptedChallenge = cryptoService.encryptWithPublicKey(
                plainChallenge, user.getPublicKey()
        );

        user.setPlainChallenge(plainChallenge);
        user.setActivationChallenge(encryptedChallenge);
        userRepository.save(user);

        tokenRepository.findByUserIdAndTokenTypeAndUsedFalse(user.getId(), "PASSWORD_RESET")
                .ifPresent(t -> { t.setUsed(true); tokenRepository.save(t); });

        String tokenValue = UUID.randomUUID().toString();
        ActivationToken token = ActivationToken.builder()
                .token(tokenValue)
                .userId(user.getId())
                .expiresAt(LocalDateTime.now().plusHours(1))
                .used(false)
                .tokenType("PASSWORD_RESET")
                .build();

        tokenRepository.save(token);
        emailService.sendPasswordResetEmail(user.getEmail(), tokenValue);
    }


    public ChallengeResponseDTO getForgotPasswordChallenge(String tokenValue) {
        ActivationToken token = validateToken(tokenValue, "PASSWORD_RESET");
        User user = userRepository.findById(token.getUserId())
                .orElseThrow(() -> new RuntimeException("User not found."));

        return new ChallengeResponseDTO(user.getActivationChallenge(), tokenValue);
    }


    @Transactional
    public void resetPassword(ResetPasswordRequestDTO req) {
        ActivationToken token = validateToken(req.getToken(), "PASSWORD_RESET");
        User user = userRepository.findById(token.getUserId())
                .orElseThrow(() -> new RuntimeException("User not found."));

        if (!req.getNewPassword().equals(req.getConfirmNewPassword())) {
            throw new RuntimeException("Passwords do not match.");
        }

        validatePassword(req.getNewPassword());

        if (!user.getPlainChallenge().equals(req.getDecryptedChallenge().trim())) {
            throw new RuntimeException("Verification failed. Please ensure you are using the correct private key.");
        }

        user.setPassword(passwordEncoder.encode(req.getNewPassword()));
        user.setPlainChallenge(null);
        userRepository.save(user);

        token.setUsed(true);
        tokenRepository.save(token);
    }

    private void validatePassword(String password) {
        if (password == null || password.length() < 8) {
            throw new RuntimeException("Password must be at least 8 characters long.");
        }
        if (password.length() > 128) {
            throw new RuntimeException("Password must not be longer than 128 characters.");
        }
        if (!password.matches(".*[A-Z].*")) {
            throw new RuntimeException("Password must contain at least one uppercase letter.");
        }
        if (!password.matches(".*[a-z].*")) {
            throw new RuntimeException("Password must contain at least one lowercase letter.");
        }
        if (!password.matches(".*[0-9].*")) {
            throw new RuntimeException("Password must contain at least one number.");
        }
        if (!password.matches(".*[^A-Za-z0-9].*")) {
            throw new RuntimeException("Password must contain at least one special character (!@#$%...).");
        }
    }

    private ActivationToken validateToken(String tokenValue, String expectedType) {
        ActivationToken token = tokenRepository.findByToken(tokenValue)
                .orElseThrow(() -> new RuntimeException("Token not found."));

        if (token.isUsed()) {
            throw new RuntimeException("Token has already been used.");
        }
        if (token.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new RuntimeException("Token has expired.");
        }
        if (!token.getTokenType().equals(expectedType)) {
            throw new RuntimeException("Invalid token type.");
        }
        return token;
    }
}