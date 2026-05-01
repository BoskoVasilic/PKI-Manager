package com.tim12.pk_infrastructure.keystores;

import org.springframework.stereotype.Component;

import java.io.*;
import java.security.*;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;

@Component
public class KeyStoreWriter {

    private final KeyStore keyStore;

    public KeyStoreWriter() {
        try {
            keyStore = KeyStore.getInstance("JKS", "SUN");
        } catch (KeyStoreException | NoSuchProviderException e) {
            throw new RuntimeException("Nije moguće inicijalizovati KeyStoreWriter", e);
        }
    }

    public void loadKeyStore(String fileName, char[] password) {
        try {
            if (fileName != null) {
                File file = new File(fileName);
                if (file.exists()) {
                    keyStore.load(new FileInputStream(file), password);
                } else {
                    keyStore.load(null, password);
                }
            } else {
                keyStore.load(null, password);
            }
        } catch (NoSuchAlgorithmException | CertificateException | IOException e) {
            throw new RuntimeException("Greška pri učitavanju keystora: " + e.getMessage(), e);
        }
    }

    public void saveKeyStore(String fileName, char[] password) {
        try {
            File file = new File(fileName);
            file.getParentFile().mkdirs();

            keyStore.store(new FileOutputStream(file), password);
        } catch (KeyStoreException | NoSuchAlgorithmException | CertificateException | IOException e) {
            throw new RuntimeException("Greška pri snimanju keystora: " + e.getMessage(), e);
        }
    }

    public void write(String alias, PrivateKey privateKey, char[] password, Certificate certificate) {
        try {
            keyStore.setKeyEntry(alias, privateKey, password, new Certificate[]{certificate});
        } catch (KeyStoreException e) {
            throw new RuntimeException("Greška pri upisivanju u keystore: " + e.getMessage(), e);
        }
    }
}