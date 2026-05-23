package com.tim12.pk_infrastructure.service;

import com.tim12.pk_infrastructure.model.User;
import com.tim12.pk_infrastructure.repository.UserRepository;
import com.tim12.pk_infrastructure.security.JwtUtil;
import dev.samstevens.totp.code.*;
import dev.samstevens.totp.exceptions.QrGenerationException;
import dev.samstevens.totp.qr.QrData;
import dev.samstevens.totp.qr.QrGenerator;
import dev.samstevens.totp.qr.ZxingPngQrGenerator;
import dev.samstevens.totp.secret.DefaultSecretGenerator;
import dev.samstevens.totp.secret.SecretGenerator;
import dev.samstevens.totp.time.SystemTimeProvider;
import dev.samstevens.totp.time.TimeProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.stereotype.Service;

import static dev.samstevens.totp.util.Utils.getDataUriForImage;

@Service
@RequiredArgsConstructor
public class TwoFactorService {

    private final UserRepository userRepository;
    private final JwtUtil jwtUtil;
    private final KeyEncryptionService keyEncryptionService; 

    private static final String ISSUER = "PKI System";

public SetupResponse setup(String email) {
        SecretGenerator secretGenerator = new DefaultSecretGenerator();
        String secret = secretGenerator.generate();

        QrData qrData = new QrData.Builder()
                .label(email)
                .secret(secret)
                .issuer(ISSUER)
                .algorithm(HashingAlgorithm.SHA1)
                .digits(6)
                .period(30)
                .build();

        try {
            QrGenerator generator = new ZxingPngQrGenerator();
            byte[] imageData = generator.generate(qrData);
            String qrCodeDataUri = getDataUriForImage(imageData, generator.getImageMimeType());
            return new SetupResponse(secret, qrCodeDataUri);
        } catch (QrGenerationException e) {
            throw new RuntimeException("Failed to generate QR code", e);
        }
    }

   public void enable(String email, String secret, String code) {
        if (!verifyCode(secret, code)) {
            throw new BadCredentialsException("Invalid authenticator code");
        }

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found"));

        user.setTwoFactorSecret(keyEncryptionService.encrypt(secret));
        user.setTwoFactorEnabled(true);
        userRepository.save(user);
    }
public String verify(String preAuthToken, String code) {
        if (!jwtUtil.isTokenValid(preAuthToken) || !jwtUtil.isTwoFaRequired(preAuthToken)) {
            throw new BadCredentialsException("Invalid or expired pre-auth token");
        }

        String email = jwtUtil.extractEmail(preAuthToken);
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found"));

        if (!user.isTwoFactorEnabled() || user.getTwoFactorSecret() == null) {
            throw new RuntimeException("2FA is not set up for this user");
        }

        String plainSecret = keyEncryptionService.decrypt(user.getTwoFactorSecret());
        if (!verifyCode(plainSecret, code)) {
            throw new BadCredentialsException("Invalid authenticator code");
        }

        return jwtUtil.generateAccessToken(user);
    }

    public void disable(String email, String code) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found"));

        if (!user.isTwoFactorEnabled()) {
            throw new RuntimeException("2FA is not enabled");
        }

        String plainSecret = keyEncryptionService.decrypt(user.getTwoFactorSecret());
        if (!verifyCode(plainSecret, code)) {
            throw new BadCredentialsException("Invalid authenticator code");
        }

        user.setTwoFactorEnabled(false);
        user.setTwoFactorSecret(null); 
        userRepository.save(user);
    }

   private boolean verifyCode(String secret, String code) {
        TimeProvider timeProvider = new SystemTimeProvider();
        CodeGenerator codeGenerator = new DefaultCodeGenerator(HashingAlgorithm.SHA1, 6);
        DefaultCodeVerifier verifier = new DefaultCodeVerifier(codeGenerator, timeProvider);
        verifier.setTimePeriod(30);
        verifier.setAllowedTimePeriodDiscrepancy(0);
        return verifier.isValidCode(secret, code);
    }

    public record SetupResponse(String secret, String qrCodeDataUri) {}
}