package com.tim12.pk_infrastructure.service;

import com.tim12.pk_infrastructure.keystores.KeyStoreReader;
import com.tim12.pk_infrastructure.model.Certificate;
import com.tim12.pk_infrastructure.repository.CertificateRepository;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x509.CRLReason;
import org.bouncycastle.cert.X509CRLHolder;
import org.bouncycastle.cert.X509v2CRLBuilder;
import org.bouncycastle.cert.jcajce.JcaX509CRLConverter;
import org.bouncycastle.cert.jcajce.JcaX509CertificateHolder;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import lombok.RequiredArgsConstructor;

import java.math.BigInteger;
import java.security.PrivateKey;
import java.security.Security;
import java.security.cert.X509CRL;
import java.security.cert.X509Certificate;
import java.util.Date;
import java.util.List;

@Service
@RequiredArgsConstructor
public class CrlService {

    private final CertificateRepository certRepo;
    private final KeyStoreReader keyStoreReader;
    private final KeyEncryptionService keyEncryptionService;

    @Value("${keystore.path:keystores/}")
    private String keystorePath;

    @Value("${app.base-url:https://localhost:8443}")
    private String baseUrl;

    static {
        Security.addProvider(new BouncyCastleProvider());
    }


    public String getCrlDistributionPointUrl(String issuerSerialNumber) {
        return baseUrl + "/api/crl/" + issuerSerialNumber + "/crl.crl";
    }


    public byte[] generateCrl(String issuerSerialNumber) {
        try {
        Certificate issuerData = certRepo.findBySerialNumber(issuerSerialNumber)
                .orElseThrow(() -> new RuntimeException("Issuer not found: " + issuerSerialNumber));

        String keystoreFilepath = keystorePath + issuerData.getIssuingOrg().getKeyStoreFileName();
        char[] keystorePassword = keyEncryptionService.decrypt(issuerData.getIssuingOrg().getKeyStorePassword()).toCharArray();

        X509Certificate issuerCert = (X509Certificate) keyStoreReader.readCertificate(
                keystoreFilepath, issuerData.getAlias(), keystorePassword
        );

        boolean[] keyUsage = issuerCert.getKeyUsage();

        if (keyUsage == null || keyUsage.length < 7 || !keyUsage[6]) {
            throw new SecurityException(
                    "Certificate does not have the cRLSign bit set in its KeyUsage extension. " +
                            "Its private key cannot be used to sign a CRL."
            );
        }

        PrivateKey issuerKey = keyStoreReader.readPrivateKey(
                keystoreFilepath, issuerData.getAlias(), keystorePassword, keystorePassword
        );

        X500Name issuerName = new JcaX509CertificateHolder(issuerCert).getSubject();

        Date now = new Date();
        Date nextUpdate = new Date(now.getTime() + 24L * 60 * 60 * 1000);

        X509v2CRLBuilder crlBuilder = new X509v2CRLBuilder(issuerName, now);
        crlBuilder.setNextUpdate(nextUpdate);

        List<Certificate> revokedCerts = certRepo.findByIssuerSerialNumberAndRevokedTrue(issuerSerialNumber);

        for (Certificate revoked : revokedCerts) {
            int reasonCode = mapRevocationReason(revoked.getRevocationReason());
            crlBuilder.addCRLEntry(
                    new BigInteger(revoked.getSerialNumber()),
                    revoked.getRevokedAt() != null ? revoked.getRevokedAt() : now,
                    reasonCode
            );
        }

        ContentSigner signer = new JcaContentSignerBuilder("SHA256WithRSAEncryption")
                .setProvider("BC")
                .build(issuerKey);

        X509CRLHolder crlHolder = crlBuilder.build(signer);
        X509CRL crl = new JcaX509CRLConverter().setProvider("BC").getCRL(crlHolder);

        return crl.getEncoded();

        } catch (SecurityException e) {
            throw new SecurityException(e.getMessage(), e);
        } catch (Exception e) {
            throw new RuntimeException("Failed to generate CRL: " + e.getMessage(), e);
        }
    }

    public int mapRevocationReason(String reason) {
        if (reason == null) return CRLReason.unspecified;
        return switch (reason.toUpperCase()) {
            case "KEY_COMPROMISE"          -> CRLReason.keyCompromise;
            case "CA_COMPROMISE"           -> CRLReason.cACompromise;
            case "AFFILIATION_CHANGED"     -> CRLReason.affiliationChanged;
            case "SUPERSEDED"              -> CRLReason.superseded;
            case "CESSATION_OF_OPERATION"  -> CRLReason.cessationOfOperation;
            case "PRIVILEGE_WITHDRAWN"     -> CRLReason.privilegeWithdrawn;
            case "CERTIFICATE_HOLD"        -> CRLReason.certificateHold;
            default                        -> CRLReason.unspecified;
        };
    }
}
