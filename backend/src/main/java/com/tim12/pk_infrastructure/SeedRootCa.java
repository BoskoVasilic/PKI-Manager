package com.tim12.pk_infrastructure;

import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x500.X500NameBuilder;
import org.bouncycastle.asn1.x500.style.BCStyle;
import org.bouncycastle.cert.X509CertificateHolder;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.bouncycastle.util.io.pem.PemObject;
import org.bouncycastle.util.io.pem.PemWriter;

import java.io.File;
import java.io.FileOutputStream;
import java.io.StringWriter;
import java.math.BigInteger;
import java.security.*;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.util.Date;

public class SeedRootCa {

    public static void main(String[] args) throws Exception {
        Security.addProvider(new BouncyCastleProvider());

        // 1. Generate RSA 2048-bit key pair
        KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA", "BC");
        kpg.initialize(2048, new SecureRandom());
        KeyPair kp = kpg.generateKeyPair();

        // 2. Build subject/issuer (same — self-signed root)
        X500Name name = new X500NameBuilder(BCStyle.INSTANCE)
                .addRDN(BCStyle.CN, "Test Root CA")
                .addRDN(BCStyle.O, "TestOrg")
                .addRDN(BCStyle.C, "RS")
                .build();

        BigInteger serial = new BigInteger(64, new SecureRandom());
        Date now   = new Date();
        Date later = new Date(now.getTime() + 10L * 365 * 24 * 3600 * 1000); // 10 years

        // 3. Sign with its own private key (self-signed)
        ContentSigner signer = new JcaContentSignerBuilder("SHA256WithRSAEncryption")
                .setProvider("BC")
                .build(kp.getPrivate());

        X509CertificateHolder holder = new JcaX509v3CertificateBuilder(
                name, serial, now, later, name, kp.getPublic()
        ).build(signer);

        X509Certificate cert = new JcaX509CertificateConverter()
                .setProvider("BC")
                .getCertificate(holder);

        // 4. Build the alias (same format your CsrService uses)
        String alias = serial.toString(16).toUpperCase();

        // 5. Save private key + cert into keystores/TestOrg.jks
        char[] ksPassword = "rq9hKknQzGvk5mLoRBnAXu7tUQKUU/ZpaYekYb7uV78=".toCharArray(); // must match PKI_MASTER_KEY in your .env
        KeyStore ks = KeyStore.getInstance("JKS", "SUN");
        File ksFile = new File("keystores/TestOrg.jks");
        ksFile.getParentFile().mkdirs();
        ks.load(null, ksPassword);
        ks.setKeyEntry(alias, kp.getPrivate(), ksPassword, new Certificate[]{cert});
        try (FileOutputStream fos = new FileOutputStream(ksFile)) {
            ks.store(fos, ksPassword);
        }

        // 6. Convert cert to PEM
        StringWriter sw = new StringWriter();
        try (PemWriter pw = new PemWriter(sw)) {
            pw.writeObject(new PemObject("CERTIFICATE", cert.getEncoded()));
        }
        String pem = sw.toString().replace("'", "''"); // escape single quotes for SQL

        // 7. Print everything you need
        System.out.println("=== KeyStore written to: " + ksFile.getAbsolutePath());
        System.out.println("=== Alias (serial): " + alias);
        System.out.println();
        System.out.println("=== Run this SQL in your database ===");
        System.out.println();
        System.out.printf("""
                INSERT INTO certificates (
                    serial_number,
                    subject_cn,
                    subject_o,
                    subject_ou,
                    subject_c,
                    subject_email,
                    issuer_cn,
                    issuer_serial_number,
                    valid_from,
                    valid_to,
                    type,
                    status,
                    certificate_pem,
                    encrypted_private_key,
                    revocation_reason,
                    revoked_at,
                    owner_id
                ) VALUES (
                    '%s',
                    'Test Root CA',
                    'TestOrg',
                    NULL,
                    'RS',
                    NULL,
                    'Test Root CA',
                    '%s',
                    NOW(),
                    NOW() + INTERVAL '10 years',
                    'ROOT',
                    'ACTIVE',
                    '%s',
                    NULL,
                    NULL,
                    NULL,
                    NULL
                );
                %n""", alias, alias, pem);
    }
}