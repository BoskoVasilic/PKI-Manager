package com.tim12.pk_infrastructure.service;


import com.tim12.pk_infrastructure.dto.IssueCertificateRequest;
import com.tim12.pk_infrastructure.model.Certificate;
import com.tim12.pk_infrastructure.model.CertificateType;
import com.tim12.pk_infrastructure.repository.CertificateRepository;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x509.*;
import org.bouncycastle.cert.X509CertificateHolder;
import org.bouncycastle.cert.X509v3CertificateBuilder;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509ExtensionUtils;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.springframework.stereotype.Service;

import java.io.StringReader;
import java.io.StringWriter;
import java.math.BigInteger;
import java.security.*;
import java.security.cert.X509Certificate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Base64;
import java.util.Date;
import java.util.List;
import java.util.UUID;

import org.bouncycastle.openssl.PEMParser;
import org.bouncycastle.openssl.jcajce.JcaPEMKeyConverter;
import org.bouncycastle.openssl.jcajce.JcaPEMWriter;
import org.bouncycastle.util.io.pem.PemObject;

@Service
public class CertificateService {

    private final CertificateRepository certificateRepository;

    public CertificateService(CertificateRepository certificateRepository) {
        this.certificateRepository = certificateRepository;
    }

    /**
     * Issues a new certificate signed by the specified CA cert.
     * CA users can only issue INTERMEDIATE or END_ENTITY.
     */
    public Certificate issueCertificate(IssueCertificateRequest req, String callerOrganization) throws Exception {

        // 1. Validate requested type — CA users cannot issue ROOT
        CertificateType requestedType = CertificateType.valueOf(req.getType());
        if (requestedType == CertificateType.ROOT) {
            throw new IllegalArgumentException("CA users cannot issue ROOT certificates.");
        }

        // 2. Load and validate the issuer certificate
        Certificate issuerRecord = certificateRepository
                .findBySerialNumber(req.getIssuerSerialNumber())
                .orElseThrow(() -> new IllegalArgumentException("Issuer certificate not found."));

        validateIssuer(issuerRecord, callerOrganization);

        // 3. Parse issuer cert and private key from PEM
        X509Certificate issuerX509 = parseCertificatePem(issuerRecord.getCertificatePem());
        PrivateKey issuerPrivateKey = parsePrivateKeyPem(issuerRecord.getEncryptedPrivateKey());

        // 4. Generate new key pair for the subject
        KeyPairGenerator keyGen = KeyPairGenerator.getInstance("RSA");
        keyGen.initialize(2048);
        KeyPair subjectKeyPair = keyGen.generateKeyPair();

        // 5. Build X500Name from request fields
        String dn = buildDn(req);
        X500Name subjectName = new X500Name(dn);
        X500Name issuerName = new X500Name(issuerX509.getSubjectX500Principal().getName());

        // 6. Convert validity dates
        Date from = Date.from(req.getValidFrom().atZone(ZoneId.systemDefault()).toInstant());
        Date to   = Date.from(req.getValidTo().atZone(ZoneId.systemDefault()).toInstant());

        // Issued cert cannot outlive the issuer
        if (to.after(issuerX509.getNotAfter())) {
            throw new IllegalArgumentException("Certificate validity cannot exceed issuer's validity period.");
        }

        // 7. Serial number
        BigInteger serial = new BigInteger(UUID.randomUUID().toString().replace("-", ""), 16).abs();

        // 8. Build certificate
        JcaX509v3CertificateBuilder certBuilder = new JcaX509v3CertificateBuilder(
                issuerName, serial, from, to, subjectName, subjectKeyPair.getPublic()
        );

        // 9. Add extensions
        JcaX509ExtensionUtils extUtils = new JcaX509ExtensionUtils();

        // SubjectKeyIdentifier — always added
        certBuilder.addExtension(Extension.subjectKeyIdentifier, false,
                extUtils.createSubjectKeyIdentifier(subjectKeyPair.getPublic()));

        // AuthorityKeyIdentifier — links to issuer
        certBuilder.addExtension(Extension.authorityKeyIdentifier, false,
                extUtils.createAuthorityKeyIdentifier(issuerX509));

        // BasicConstraints — CA:true for intermediate, CA:false for EE
        boolean isCa = requestedType == CertificateType.INTERMEDIATE || req.isCa();
        if (isCa) {
            int pathLen = req.getPathLengthConstraint();
            certBuilder.addExtension(Extension.basicConstraints, true,
                    pathLen >= 0 ? new BasicConstraints(pathLen) : new BasicConstraints(true));
        } else {
            certBuilder.addExtension(Extension.basicConstraints, true, new BasicConstraints(false));
        }

        // KeyUsage — from request list
        if (req.getKeyUsages() != null && !req.getKeyUsages().isEmpty()) {
            int keyUsageBits = buildKeyUsageBits(req.getKeyUsages());
            certBuilder.addExtension(Extension.keyUsage, true, new KeyUsage(keyUsageBits));
        }

        // 10. Sign
        ContentSigner signer = new JcaContentSignerBuilder("SHA256WithRSA")
                .build(issuerPrivateKey);

        X509CertificateHolder holder = certBuilder.build(signer);
        X509Certificate signedCert = new JcaX509CertificateConverter().getCertificate(holder);

        // 11. Verify the signature is correct
        signedCert.verify(issuerX509.getPublicKey());

        // 12. Persist
        Certificate saved = new Certificate();
        saved.setSerialNumber(serial.toString(16));
        saved.setCommonName(req.getCommonName());
        saved.setOrganization(req.getOrganization());
        saved.setOrganizationalUnit(req.getOrganizationalUnit());
        saved.setCountry(req.getCountry());
        saved.setEmail(req.getEmail());
        saved.setType(requestedType);
        saved.setIssuerSerialNumber(req.getIssuerSerialNumber());
        saved.setValidFrom(req.getValidFrom());
        saved.setValidTo(req.getValidTo());
        saved.setCertificatePem(toPem(signedCert));
        saved.setEncryptedPrivateKey(privateKeyToPem(subjectKeyPair.getPrivate())); // func 6 should encrypt this
        saved.setOwnerOrganization(callerOrganization);
        saved.setRevoked(false);

        return certificateRepository.save(saved);
    }

    /**
     * Returns CA certs available for issuance in the caller's organization.
     * These are non-revoked INTERMEDIATE or ROOT certs.
     */
    public List<Certificate> getAvailableIssuers(String callerOrganization) {
        return certificateRepository.findByOwnerOrganizationAndTypeInAndRevokedFalse(
                callerOrganization,
                List.of(CertificateType.ROOT, CertificateType.INTERMEDIATE)
        );
    }


    private void validateIssuer(Certificate issuer, String callerOrganization) throws Exception {
        // Must belong to caller's org
        if (!issuer.getOwnerOrganization().equals(callerOrganization)) {
            throw new SecurityException("You can only use CA certificates from your own organization.");
        }
        // Must not be revoked
        if (issuer.isRevoked()) {
            throw new IllegalArgumentException("Issuer certificate has been revoked.");
        }
        // Must be a CA type
        if (issuer.getType() == CertificateType.END_ENTITY) {
            throw new IllegalArgumentException("End-entity certificates cannot sign other certificates.");
        }
        // Must be within validity period
        X509Certificate x509 = parseCertificatePem(issuer.getCertificatePem());
        x509.checkValidity(); // throws CertificateExpiredException or CertificateNotYetValidException
    }


    private String buildDn(IssueCertificateRequest req) {
        StringBuilder sb = new StringBuilder();
        if (req.getCommonName() != null)         sb.append("CN=").append(req.getCommonName()).append(",");
        if (req.getOrganization() != null)        sb.append("O=").append(req.getOrganization()).append(",");
        if (req.getOrganizationalUnit() != null)  sb.append("OU=").append(req.getOrganizationalUnit()).append(",");
        if (req.getCountry() != null)             sb.append("C=").append(req.getCountry()).append(",");
        if (req.getEmail() != null)               sb.append("E=").append(req.getEmail()).append(",");
        String dn = sb.toString();
        return dn.endsWith(",") ? dn.substring(0, dn.length() - 1) : dn;
    }

    private int buildKeyUsageBits(List<String> usages) {
        int bits = 0;
        for (String usage : usages) {
            switch (usage.toUpperCase()) {
                case "DIGITAL_SIGNATURE"  -> bits |= KeyUsage.digitalSignature;
                case "NON_REPUDIATION"    -> bits |= KeyUsage.nonRepudiation;
                case "KEY_ENCIPHERMENT"   -> bits |= KeyUsage.keyEncipherment;
                case "DATA_ENCIPHERMENT"  -> bits |= KeyUsage.dataEncipherment;
                case "KEY_AGREEMENT"      -> bits |= KeyUsage.keyAgreement;
                case "KEY_CERT_SIGN"      -> bits |= KeyUsage.keyCertSign;
                case "CRL_SIGN"           -> bits |= KeyUsage.cRLSign;
            }
        }
        return bits;
    }

    private X509Certificate parseCertificatePem(String pem) throws Exception {
        PEMParser parser = new PEMParser(new StringReader(pem));
        Object obj = parser.readObject();
        parser.close();
        return new JcaX509CertificateConverter().getCertificate((X509CertificateHolder) obj);
    }

    private PrivateKey parsePrivateKeyPem(String pem) throws Exception {
        PEMParser parser = new PEMParser(new StringReader(pem));
        Object obj = parser.readObject();
        parser.close();
        return new JcaPEMKeyConverter().getPrivateKey((org.bouncycastle.asn1.pkcs.PrivateKeyInfo) obj);
    }

    private String toPem(X509Certificate cert) throws Exception {
        StringWriter sw = new StringWriter();
        try (JcaPEMWriter writer = new JcaPEMWriter(sw)) {
            writer.writeObject(cert);
        }
        return sw.toString();
    }

    private String privateKeyToPem(PrivateKey key) throws Exception {
        StringWriter sw = new StringWriter();
        try (JcaPEMWriter writer = new JcaPEMWriter(sw)) {
            writer.writeObject(key);
        }
        return sw.toString();
        // TO-DO functionality 6: should encrypt this before storing
    }
}