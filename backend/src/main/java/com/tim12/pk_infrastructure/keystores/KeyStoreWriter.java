package com.tim12.pk_infrastructure.keystores;

import org.springframework.stereotype.Component;

import java.io.*;
import java.security.*;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;


@Component
public class KeyStoreWriter {

    public void write(String keyStoreFile, String alias,
                      PrivateKey privateKey, char[] password, Certificate certificate) {
        try {
            KeyStore ks = KeyStore.getInstance("JKS", "SUN");

            File file = new File(keyStoreFile);
            if (file.exists()) {
                // Load the existing KeyStore so we don't overwrite other entries
                try (BufferedInputStream in = new BufferedInputStream(new FileInputStream(file))) {
                    ks.load(in, password);
                }
            } else {
                // Create parent directories if needed (e.g. keystores/)
                file.getParentFile().mkdirs();
                // Initialise a brand-new, empty KeyStore
                ks.load(null, password);
            }

            // Store the private key entry (protected by the same password)
            ks.setKeyEntry(alias, privateKey, password, new Certificate[]{certificate});

            // Persist back to disk
            try (FileOutputStream fos = new FileOutputStream(file)) {
                ks.store(fos, password);
            }

        } catch (KeyStoreException | NoSuchProviderException | NoSuchAlgorithmException |
                 CertificateException | IOException e) {
            e.printStackTrace();
            throw new RuntimeException("Greška pri čuvanju privatnog ključa u KeyStore: " + e.getMessage(), e);
        }
    }
}