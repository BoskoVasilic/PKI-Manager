package com.tim12.pk_infrastructure.keystores;

import org.springframework.stereotype.Component;

import java.io.*;
import java.security.*;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;

/**
 * Writes private keys and certificates into organization JKS KeyStore files.
 * Each organization has its own .jks file stored in the keystores/ directory.
 * If the file does not exist yet, a new KeyStore is created automatically.
 */
@Component
public class KeyStoreWriter {

    /**
     * Stores a private key + certificate into an organization's KeyStore file.
     * Creates a new .jks file if one does not already exist for this organization.
     *
     * @param keyStoreFile  path to the .jks file (e.g. "keystores/MyOrg.jks")
     * @param alias         alias for this entry (certificate serial number)
     * @param privateKey    the private key to store
     * @param password      password for the KeyStore and the key entry
     * @param certificate   the X509Certificate that corresponds to this key
     */
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