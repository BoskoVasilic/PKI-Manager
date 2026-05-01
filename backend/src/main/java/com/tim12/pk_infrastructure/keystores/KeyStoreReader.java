package com.tim12.pk_infrastructure.keystores;

import com.tim12.pk_infrastructure.model.Issuer;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.cert.jcajce.JcaX509CertificateHolder;
import org.springframework.stereotype.Component;

import java.io.*;
import java.security.*;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;

@Component
public class KeyStoreReader {

    private final KeyStore keyStore;

    public KeyStoreReader() {
        try {
            keyStore = KeyStore.getInstance("JKS", "SUN");
        } catch (KeyStoreException | NoSuchProviderException e) {
            throw new RuntimeException("Nije moguće inicijalizovati KeyStore", e);
        }
    }

    public Issuer readIssuerFromStore(String keyStoreFile, String alias, char[] password, char[] keyPass) {
        try {
            BufferedInputStream in = new BufferedInputStream(new FileInputStream(keyStoreFile));
            keyStore.load(in, password);

            Certificate cert = keyStore.getCertificate(alias);
            if (cert == null) {
                throw new RuntimeException("Sertifikat sa aliasom '" + alias + "' nije pronađen u keystoru");
            }

            PrivateKey privateKey = (PrivateKey) keyStore.getKey(alias, keyPass);
            if (privateKey == null) {
                throw new RuntimeException("Privatni ključ za alias '" + alias + "' nije pronađen");
            }

            X500Name issuerName = new JcaX509CertificateHolder((X509Certificate) cert).getSubject();
            return new Issuer(privateKey, cert.getPublicKey(), issuerName);

        } catch (KeyStoreException | NoSuchAlgorithmException |
                 CertificateException | UnrecoverableKeyException | IOException e) {
            throw new RuntimeException("Greška pri čitanju issuera iz keystora: " + e.getMessage(), e);
        }
    }

    public Certificate readCertificate(String keyStoreFile, String keyStorePass, String alias) {
        try {
            KeyStore ks = KeyStore.getInstance("JKS", "SUN");
            BufferedInputStream in = new BufferedInputStream(new FileInputStream(keyStoreFile));
            ks.load(in, keyStorePass.toCharArray());

            if (ks.isKeyEntry(alias)) {
                return ks.getCertificate(alias);
            }
            throw new RuntimeException("Alias '" + alias + "' nije pronađen u keystoru");

        } catch (KeyStoreException | NoSuchProviderException |
                 NoSuchAlgorithmException | CertificateException | IOException e) {
            throw new RuntimeException("Greška pri čitanju sertifikata iz keystora: " + e.getMessage(), e);
        }
    }

    public PrivateKey readPrivateKey(String keyStoreFile, String keyStorePass, String alias, String keyPass) {
        try {
            KeyStore ks = KeyStore.getInstance("JKS", "SUN");
            BufferedInputStream in = new BufferedInputStream(new FileInputStream(keyStoreFile));
            ks.load(in, keyStorePass.toCharArray());

            if (ks.isKeyEntry(alias)) {
                return (PrivateKey) ks.getKey(alias, keyPass.toCharArray());
            }
            throw new RuntimeException("Alias '" + alias + "' nije pronađen u keystoru");

        } catch (KeyStoreException | NoSuchProviderException |
                 NoSuchAlgorithmException | CertificateException | IOException |
                 UnrecoverableKeyException e) {
            throw new RuntimeException("Greška pri čitanju privatnog ključa: " + e.getMessage(), e);
        }
    }
}