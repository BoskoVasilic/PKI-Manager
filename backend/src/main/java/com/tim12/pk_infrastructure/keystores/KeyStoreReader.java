package com.tim12.pk_infrastructure.keystores;

import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.cert.jcajce.JcaX509CertificateHolder;
import org.springframework.stereotype.Component;

import java.io.*;
import java.security.*;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;

/**
 * Reads private keys and certificates from organization JKS KeyStore files.
 * Each organization has its own .jks file stored in the keystores/ directory.
 */
@Component
public class KeyStoreReader {

    /**
     * Reads the private key for a given alias from an organization's KeyStore file.
     *
     * @param keyStoreFile  path to the .jks file (e.g. "keystores/MyOrg.jks")
     * @param alias         alias of the entry (certificate serial number)
     * @param password      password to open the KeyStore
     * @param keyPass       password to extract the private key (same as password in our setup)
     * @return the PrivateKey, or null if not found
     */
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

    /**
     * Reads the certificate for a given alias from an organization's KeyStore file.
     *
     * @param keyStoreFile  path to the .jks file
     * @param alias         alias of the entry (certificate serial number)
     * @param password      password to open the KeyStore
     * @return the Certificate, or null if not found
     */
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
}