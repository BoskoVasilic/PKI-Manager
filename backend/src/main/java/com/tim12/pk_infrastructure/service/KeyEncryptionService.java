package com.tim12.pk_infrastructure.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;

@Service
public class KeyEncryptionService {

    private static final String ALGORITHM   = "AES/GCM/NoPadding";
    private static final int    GCM_TAG_LEN = 128;
    private static final int    IV_LEN      = 12;

    @Value("${pki.master.key}")
    private String masterKeyBase64;

    public String encrypt(String plaintext) {
        try {
            SecretKeySpec key = loadMasterKey();

            byte[] iv = new byte[IV_LEN];
            new SecureRandom().nextBytes(iv);

            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(GCM_TAG_LEN, iv));
            byte[] ciphertext = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));

            byte[] payload = new byte[IV_LEN + ciphertext.length];
            System.arraycopy(iv, 0, payload, 0, IV_LEN);
            System.arraycopy(ciphertext, 0, payload, IV_LEN, ciphertext.length);

            return Base64.getEncoder().encodeToString(payload);

        } catch (Exception e) {
            throw new RuntimeException("Failed to encrypt keystore password: " + e.getMessage(), e);
        }
    }

    public String decrypt(String encryptedBase64) {
        try {
            SecretKeySpec key = loadMasterKey();

            byte[] payload    = Base64.getDecoder().decode(encryptedBase64);
            byte[] iv         = Arrays.copyOfRange(payload, 0, IV_LEN);
            byte[] ciphertext = Arrays.copyOfRange(payload, IV_LEN, payload.length);

            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(GCM_TAG_LEN, iv));
            byte[] decrypted = cipher.doFinal(ciphertext);

            return new String(decrypted, StandardCharsets.UTF_8);

        } catch (Exception e) {
            throw new RuntimeException("Failed to decrypt keystore password: " + e.getMessage(), e);
        }
    }

    public String generateRandomPassword() {
        byte[] raw = new byte[32];
        new SecureRandom().nextBytes(raw);
        return Base64.getEncoder().encodeToString(raw);
    }

    private SecretKeySpec loadMasterKey() {
        byte[] keyBytes = Base64.getDecoder().decode(masterKeyBase64);
        if (keyBytes.length != 32) {
            throw new IllegalStateException(
                    "app.master-key must be a Base64-encoded 32-byte (256-bit) key. " +
                            "Current decoded length: " + keyBytes.length + " bytes.");
        }
        return new SecretKeySpec(keyBytes, "AES");
    }
}