package com.tim12.pk_infrastructure.service;

import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.Security;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.UUID;

@Service
public class CryptoService {

    static {
        Security.addProvider(new BouncyCastleProvider());
    }

    public String generateChallenge() {
        return UUID.randomUUID().toString();
    }

    public String encryptWithPublicKey(String plaintext, String publicKeyPem) {
        try {
            PublicKey publicKey = parsePemPublicKey(publicKeyPem);

            Cipher cipher = Cipher.getInstance("RSA/ECB/OAEPWithSHA-256AndMGF1Padding", "BC");
            cipher.init(Cipher.ENCRYPT_MODE, publicKey);

            byte[] encrypted = cipher.doFinal(plaintext.getBytes());
            return Base64.getEncoder().encodeToString(encrypted);

        } catch (Exception e) {
            throw new RuntimeException("Error encrypting challenge: " + e.getMessage(), e);
        }
    }

    public PublicKey parsePemPublicKey(String pem) {
        try {
            String cleaned = pem
                    .replace("-----BEGIN PUBLIC KEY-----", "")
                    .replace("-----END PUBLIC KEY-----", "")
                    .replaceAll("\\s+", "");

            byte[] keyBytes = Base64.getDecoder().decode(cleaned);
            X509EncodedKeySpec spec = new X509EncodedKeySpec(keyBytes);
            KeyFactory kf = KeyFactory.getInstance("RSA");
            return kf.generatePublic(spec);

        } catch (Exception e) {
            throw new RuntimeException("Invalid public key format: " + e.getMessage(), e);
        }
    }

    public boolean isValidPublicKey(String pem) {
        if (pem == null || pem.isBlank()) return false;
        if (!pem.contains("-----BEGIN PUBLIC KEY-----")) return false;
        try {
            parsePemPublicKey(pem);
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}