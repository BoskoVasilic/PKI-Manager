package com.tim12.pk_infrastructure.service;

import com.tim12.pk_infrastructure.model.MasterKey;
import com.tim12.pk_infrastructure.model.enums.MasterKeyStatus;
import com.tim12.pk_infrastructure.repository.MasterKeyRepository;
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
    private String bootstrapKeyBase64; // env-level key, wraps the DB keys

    private final MasterKeyRepository masterKeyRepository;

    public KeyEncryptionService(MasterKeyRepository masterKeyRepository) {
        this.masterKeyRepository = masterKeyRepository;
    }

    // ── Public API ──────────────────────────────────────────────────────────

    public String encrypt(String plaintext) {
        return encryptWithKey(plaintext, loadActiveKey());
    }

    public String decrypt(String encryptedBase64) {
        return decryptWithKey(encryptedBase64, loadActiveKey());
    }

    /** Used during rotation: decrypt old, re-encrypt with new. */
    public String reEncrypt(String encryptedBase64, SecretKeySpec oldKey, SecretKeySpec newKey) {
        String plaintext = decryptWithKey(encryptedBase64, oldKey);
        return encryptWithKey(plaintext, newKey);
    }

    public String generateRandomPassword() {
        byte[] raw = new byte[32];
        new SecureRandom().nextBytes(raw);
        return Base64.getEncoder().encodeToString(raw);
    }

    /** Generate a new random AES-256 key and return it wrapped (encrypted by the bootstrap key). */
    public String generateAndWrapNewMasterKey() {
        byte[] raw = new byte[32];
        new SecureRandom().nextBytes(raw);
        SecretKeySpec bootstrapKey = loadBootstrapKey();
        return encryptWithKey(Base64.getEncoder().encodeToString(raw), bootstrapKey);
    }

    /** Unwrap (decrypt) a stored DB master key using the bootstrap key. */
    public SecretKeySpec unwrapMasterKey(String wrappedKeyBase64) {
        SecretKeySpec bootstrapKey = loadBootstrapKey();
        String rawBase64 = decryptWithKey(wrappedKeyBase64, bootstrapKey);
        byte[] keyBytes = Base64.getDecoder().decode(rawBase64);
        return new SecretKeySpec(keyBytes, "AES");
    }

    // ── Internals ───────────────────────────────────────────────────────────

    private SecretKeySpec loadActiveKey() {
        MasterKey active = masterKeyRepository.findByStatus(MasterKeyStatus.ACTIVE)
                .orElseThrow(() -> new IllegalStateException("No active master key found in database"));
        return unwrapMasterKey(active.getKeyBase64());
    }

    private SecretKeySpec loadBootstrapKey() {
        byte[] keyBytes = Base64.getDecoder().decode(bootstrapKeyBase64);
        if (keyBytes.length != 32)
            throw new IllegalStateException("pki.master.key must be a Base64-encoded 256-bit key");
        return new SecretKeySpec(keyBytes, "AES");
    }

    String encryptWithKey(String plaintext, SecretKeySpec key) {
        try {
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
            throw new RuntimeException("Encryption failed: " + e.getMessage(), e);
        }
    }

    String decryptWithKey(String encryptedBase64, SecretKeySpec key) {
        try {
            byte[] payload    = Base64.getDecoder().decode(encryptedBase64);
            byte[] iv         = Arrays.copyOfRange(payload, 0, IV_LEN);
            byte[] ciphertext = Arrays.copyOfRange(payload, IV_LEN, payload.length);
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(GCM_TAG_LEN, iv));
            return new String(cipher.doFinal(ciphertext), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new RuntimeException("Decryption failed: " + e.getMessage(), e);
        }
    }
}