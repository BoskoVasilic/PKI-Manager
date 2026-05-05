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

    public PrivateKey readPrivateKey(String keyStoreFile, String alias, char[] password, char[] keyPass) {
        try {
            KeyStore ks = KeyStore.getInstance("JKS", "SUN");
            BufferedInputStream in = new BufferedInputStream(new FileInputStream(keyStoreFile));
            ks.load(in, password);

            if (ks.isKeyEntry(alias)) {
                return (PrivateKey) ks.getKey(alias, keyPass);
            }
        } catch (KeyStoreException | NoSuchProviderException | NoSuchAlgorithmException |
                 CertificateException | UnrecoverableKeyException | IOException e) {
            e.printStackTrace();
        }
        return null;
    }

    public Certificate readCertificate(String keyStoreFile, String alias, char[] password) {
        try {
            KeyStore ks = KeyStore.getInstance("JKS", "SUN");
            BufferedInputStream in = new BufferedInputStream(new FileInputStream(keyStoreFile));
            ks.load(in, password);

            if (ks.isKeyEntry(alias)) {
                return ks.getCertificate(alias);
            }
        } catch (KeyStoreException | NoSuchProviderException | NoSuchAlgorithmException |
                 CertificateException | IOException e) {
            e.printStackTrace();
        }
        return null;
    }

    public Issuer readIssuerFromStore(String keyStoreFile, String alias, char[] password, char[] keyPass) {
        try {
            KeyStore ks = KeyStore.getInstance("JKS", "SUN");
            BufferedInputStream in = new BufferedInputStream(new FileInputStream(keyStoreFile));
            ks.load(in, password);

            Certificate cert = ks.getCertificate(alias);
            if (cert == null) {
                throw new RuntimeException("Sertifikat sa aliasom '" + alias + "' nije pronađen u keystoru");
            }

            PrivateKey privateKey = (PrivateKey) ks.getKey(alias, keyPass);
            if (privateKey == null) {
                throw new RuntimeException("Privatni ključ za alias '" + alias + "' nije pronađen");
            }

            X500Name issuerName = new JcaX509CertificateHolder((X509Certificate) cert).getSubject();
            return new Issuer(privateKey, cert.getPublicKey(), issuerName);

        } catch (KeyStoreException | NoSuchAlgorithmException |
                 CertificateException | UnrecoverableKeyException | IOException e) {
            throw new RuntimeException("Greška pri čitanju issuera iz keystora: " + e.getMessage(), e);
        } catch (NoSuchProviderException e) {
            throw new RuntimeException(e);
        }
    }

    public X509Certificate readX509Certificate(String keyStoreFile, String alias, char[] password) {
        try {
            KeyStore ks = KeyStore.getInstance("JKS", "SUN");

            try (BufferedInputStream in = new BufferedInputStream(new FileInputStream(keyStoreFile))) {
                ks.load(in, password);
            }

            Certificate cert = ks.getCertificate(alias);

            if (cert == null) {
                throw new RuntimeException("Sertifikat sa aliasom '" + alias + "' nije pronađen u keystoru.");
            }

            if (!(cert instanceof X509Certificate)) {
                throw new RuntimeException("Sertifikat sa aliasom '" + alias + "' nije X509 sertifikat.");
            }

            return (X509Certificate) cert;

        } catch (KeyStoreException | NoSuchProviderException | NoSuchAlgorithmException |
                 CertificateException | IOException e) {
            throw new RuntimeException("Greška pri čitanju X509 sertifikata iz keystora: " + e.getMessage(), e);
        }
    }
}