package com.tim12.pk_infrastructure.service;

import com.tim12.pk_infrastructure.certificates.CertificateGenerator;
import com.tim12.pk_infrastructure.dto.CertificateDto;
import com.tim12.pk_infrastructure.keystores.KeyStoreReader;
import com.tim12.pk_infrastructure.keystores.KeyStoreWriter;
import com.tim12.pk_infrastructure.model.*;
import com.tim12.pk_infrastructure.model.Certificate;
import com.tim12.pk_infrastructure.model.dtos.CertificateDTO;
import com.tim12.pk_infrastructure.model.dtos.IssueCertificateRequest;
import com.tim12.pk_infrastructure.model.enums.CertificateType;
import com.tim12.pk_infrastructure.model.CertificateStatus;
import com.tim12.pk_infrastructure.repository.CertificateRepository;
import com.tim12.pk_infrastructure.repository.OrganizatioRepository;
import com.tim12.pk_infrastructure.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x500.X500NameBuilder;
import org.bouncycastle.asn1.x500.style.BCStyle;

import java.security.*;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import org.bouncycastle.asn1.x509.*;
import org.bouncycastle.cert.X509CertificateHolder;
import org.bouncycastle.cert.X509v3CertificateBuilder;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509ExtensionUtils;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.openssl.PEMParser;
import org.bouncycastle.openssl.jcajce.JcaPEMKeyConverter;
import org.bouncycastle.openssl.jcajce.JcaPEMWriter;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.io.StringReader;
import java.io.StringWriter;
import java.math.BigInteger;
import java.time.ZoneId;
import java.util.Date;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CertificateService {

    private final CertificateRepository certificateRepository;
    private final UserRepository userRepository;
    private static final String keystorePath = "src/main/resources/keystores/keystore.jks";
    private static final String keystorePass = "keystorepass";
    private final OrganizatioRepository orgRepo;
    private final KeyStoreReader keyStoreReader;
    private final KeyStoreWriter keyStoreWriter;

    // ------------------------------------------------------------------ //
    //  Auth helper
    // ------------------------------------------------------------------ //

    private User getCurrentUser() {
        String email = SecurityContextHolder.getContext()
                .getAuthentication().getName();
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("Authenticated user not found"));
    }

    // ------------------------------------------------------------------ //
    //  End-user: view own certificates
    // ------------------------------------------------------------------ //

    public List<CertificateDto> getMyEndEntityCertificates() {
        User currentUser = getCurrentUser();
        return certificateRepository
                .findByOwnerAndType(currentUser, CertificateType.END_ENTITY)
                .stream()
                .map(this::toDto)
                .toList();
    }

    public CertificateDto getMyCertificateBySerial(String serialNumber) {
        User currentUser = getCurrentUser();
        Certificate cert = certificateRepository
                .findBySerialNumberAndOwner(serialNumber, currentUser)
                .orElseThrow(() -> new RuntimeException("Certificate not found"));
        return toDto(cert);
    }

    // ------------------------------------------------------------------ //
    //  CA user: issue certificates
    // ------------------------------------------------------------------ //

    /**
     * Issues a new certificate signed by the specified CA cert.
     * CA users can only issue INTERMEDIATE or END_ENTITY.
     */
    public CertificateDto issueCertificate(IssueCertificateRequest req) throws Exception {

        User caller = getCurrentUser();

        // 1. Validate requested type — nobody can issue ROOT through this endpoint
        CertificateType requestedType = CertificateType.valueOf(req.getType());
        if (requestedType == CertificateType.ROOT) {
            throw new IllegalArgumentException("ROOT certificates cannot be issued through this endpoint.");
        }

        // 2. Load and validate the issuer certificate
        Certificate issuerRecord = certificateRepository
                .findBySerialNumber(req.getIssuerSerialNumber())
                .orElseThrow(() -> new IllegalArgumentException("Issuer certificate not found."));

        validateIssuer(issuerRecord, caller);

        // 3. Parse issuer cert and private key from PEM
        X509Certificate issuerX509 = parseCertificatePem(issuerRecord.getCertificatePem());
        PrivateKey issuerPrivateKey = parsePrivateKeyPem(issuerRecord.getEncryptedPrivateKey());

        // 4. Generate new key pair for the subject
        KeyPairGenerator keyGen = KeyPairGenerator.getInstance("RSA");
        keyGen.initialize(2048);
        KeyPair subjectKeyPair = keyGen.generateKeyPair();

        // 5. Build X500Name from request fields
        X500Name subjectName = new X500Name(buildDn(req));
        X500Name issuerName  = new X500Name(issuerX509.getSubjectX500Principal().getName());

        // 6. Validity dates
        Date from = Date.from(req.getValidFrom().atZone(ZoneId.systemDefault()).toInstant());
        Date to   = Date.from(req.getValidTo().atZone(ZoneId.systemDefault()).toInstant());

        if (to.after(issuerX509.getNotAfter())) {
            throw new IllegalArgumentException("Certificate validity cannot exceed issuer's validity period.");
        }

        // 7. Serial number
        BigInteger serial = new BigInteger(UUID.randomUUID().toString().replace("-", ""), 16).abs();

        // 8. Build certificate
        JcaX509v3CertificateBuilder certBuilder = new JcaX509v3CertificateBuilder(
                issuerName, serial, from, to, subjectName, subjectKeyPair.getPublic()
        );

        // 9. Extensions
        JcaX509ExtensionUtils extUtils = new JcaX509ExtensionUtils();

        certBuilder.addExtension(Extension.subjectKeyIdentifier, false,
                extUtils.createSubjectKeyIdentifier(subjectKeyPair.getPublic()));

        certBuilder.addExtension(Extension.authorityKeyIdentifier, false,
                extUtils.createAuthorityKeyIdentifier(issuerX509));

        boolean isCa = requestedType == CertificateType.INTERMEDIATE || req.isCa();
        if (isCa) {
            int pathLen = req.getPathLengthConstraint();
            certBuilder.addExtension(Extension.basicConstraints, true,
                    pathLen >= 0 ? new BasicConstraints(pathLen) : new BasicConstraints(true));
        } else {
            certBuilder.addExtension(Extension.basicConstraints, true, new BasicConstraints(false));
        }

        if (req.getKeyUsages() != null && !req.getKeyUsages().isEmpty()) {
            certBuilder.addExtension(Extension.keyUsage, true,
                    new KeyUsage(buildKeyUsageBits(req.getKeyUsages())));
        }

        // 10. Sign
        ContentSigner signer = new JcaContentSignerBuilder("SHA256WithRSA").build(issuerPrivateKey);
        X509CertificateHolder holder = certBuilder.build(signer);
        X509Certificate signedCert = new JcaX509CertificateConverter().getCertificate(holder);

        // 11. Verify
        signedCert.verify(issuerX509.getPublicKey());

        // 12. Persist — map to the actual Certificate model fields
        Certificate saved = Certificate.builder()
                .serialNumber(serial.toString(16))
                .subjectCN(req.getCommonName())
                .subjectO(req.getOrganization())
                .subjectOU(req.getOrganizationalUnit())
                .subjectC(req.getCountry())
                .subjectEmail(req.getEmail())
                .issuerCN(issuerX509.getSubjectX500Principal().getName())
                .issuerSerialNumber(req.getIssuerSerialNumber())
                .validFrom(req.getValidFrom())
                .validTo(req.getValidTo())
                .type(requestedType)
                .status(CertificateStatus.ACTIVE)
                .certificatePem(toPem(signedCert))
                .encryptedPrivateKey(privateKeyToPem(subjectKeyPair.getPrivate())) // TODO feature #7: encrypt before storing
                .owner(caller)
                .build();

        return toDto(certificateRepository.save(saved));
    }

    /**
     * Returns CA certs the current user owns and can use as issuers.
     */
    public List<CertificateDto> getAvailableIssuers() {
        User caller = getCurrentUser();
        return certificateRepository
                .findByOwnerAndType(caller, CertificateType.ROOT)
                .stream()
                .filter(c -> c.getStatus() == CertificateStatus.ACTIVE)
                .map(this::toDto)
                .toList();
        // NOTE: If intermediate certs should also appear here, add a second
        // findByOwnerAndType call for INTERMEDIATE and combine the lists.
    }

    // ------------------------------------------------------------------ //
    //  Private helpers
    // ------------------------------------------------------------------ //

    private void validateIssuer(Certificate issuer, User caller) throws Exception {
        if (!issuer.getOwner().getId().equals(caller.getId())) {
            throw new SecurityException("You can only use CA certificates you own.");
        }
        if (issuer.getStatus() == CertificateStatus.REVOKED) {
            throw new IllegalArgumentException("Issuer certificate has been revoked.");
        }
        if (issuer.getType() == CertificateType.END_ENTITY) {
            throw new IllegalArgumentException("End-entity certificates cannot sign other certificates.");
        }
        parseCertificatePem(issuer.getCertificatePem()).checkValidity();
    }

    private String buildDn(IssueCertificateRequest req) {
        StringBuilder sb = new StringBuilder();
        if (req.getCommonName() != null)        sb.append("CN=").append(req.getCommonName()).append(",");
        if (req.getOrganization() != null)       sb.append("O=").append(req.getOrganization()).append(",");
        if (req.getOrganizationalUnit() != null) sb.append("OU=").append(req.getOrganizationalUnit()).append(",");
        if (req.getCountry() != null)            sb.append("C=").append(req.getCountry()).append(",");
        if (req.getEmail() != null)              sb.append("E=").append(req.getEmail()).append(",");
        String dn = sb.toString();
        return dn.endsWith(",") ? dn.substring(0, dn.length() - 1) : dn;
    }

    private int buildKeyUsageBits(List<String> usages) {
        int bits = 0;
        for (String usage : usages) {
            switch (usage.toUpperCase()) {
                case "DIGITAL_SIGNATURE" -> bits |= KeyUsage.digitalSignature;
                case "NON_REPUDIATION"   -> bits |= KeyUsage.nonRepudiation;
                case "KEY_ENCIPHERMENT"  -> bits |= KeyUsage.keyEncipherment;
                case "DATA_ENCIPHERMENT" -> bits |= KeyUsage.dataEncipherment;
                case "KEY_AGREEMENT"     -> bits |= KeyUsage.keyAgreement;
                case "KEY_CERT_SIGN"     -> bits |= KeyUsage.keyCertSign;
                case "CRL_SIGN"          -> bits |= KeyUsage.cRLSign;
            }
        }
        return bits;
    }

    private X509Certificate parseCertificatePem(String pem) throws Exception {
        try (PEMParser parser = new PEMParser(new StringReader(pem))) {
            return new JcaX509CertificateConverter()
                    .getCertificate((X509CertificateHolder) parser.readObject());
        }
    }

    private PrivateKey parsePrivateKeyPem(String pem) throws Exception {
        try (PEMParser parser = new PEMParser(new StringReader(pem))) {
            return new JcaPEMKeyConverter()
                    .getPrivateKey((org.bouncycastle.asn1.pkcs.PrivateKeyInfo) parser.readObject());
        }
    }

    private String toPem(X509Certificate cert) throws Exception {
        StringWriter sw = new StringWriter();
        try (JcaPEMWriter w = new JcaPEMWriter(sw)) { w.writeObject(cert); }
        return sw.toString();
    }

    private String privateKeyToPem(PrivateKey key) throws Exception {
        StringWriter sw = new StringWriter();
        try (JcaPEMWriter w = new JcaPEMWriter(sw)) { w.writeObject(key); }
        return sw.toString();
    }

    private CertificateDto toDto(Certificate c) {
        return CertificateDto.builder()
                .serialNumber(c.getSerialNumber())
                .subjectCN(c.getSubjectCN())
                .subjectO(c.getSubjectO())
                .subjectOU(c.getSubjectOU())
                .subjectC(c.getSubjectC())
                .subjectEmail(c.getSubjectEmail())
                .issuerCN(c.getIssuerCN())
                .validFrom(c.getValidFrom())
                .validTo(c.getValidTo())
                .type(c.getType())
                .revoked(c.isRevoked())
                .revocationReason(c.getRevocationReason())
                .build();
    }

    private CertificateDTO populateDto(Certificate c) {
        return CertificateDTO.builder()
                .serialNumber(c.getSerialNumber())
                .commonName(c.getSubjectCN())
                .organization(c.getSubjectO())
                .organizationUnit(c.getSubjectOU())
                .country(c.getSubjectC())
                .email(c.getSubjectEmail())
                .validFrom(String.valueOf(c.getValidFrom()))
                .validTo(String.valueOf(c.getValidTo()))
                .type(c.getType())
                .revoked(c.isRevoked())
                .build();
    }

    public Certificate issueCertificate(IssueCertificateRequest req) {

        Issuer issuer;
        KeyPair subjectKeyPair;

        if (req.getType() == CertificateType.ROOT) {
            subjectKeyPair = generateKeyPair();
            X500Name name = buildX500Name(req);

            issuer = new Issuer(subjectKeyPair.getPrivate(), subjectKeyPair.getPublic(), name);

        } else {
            Certificate issuerData = certificateRepository.findBySerialNumber(req.getIssuerSerialNumber())
                    .orElseThrow(() -> new RuntimeException(
                            "Issuer sertifikat nije pronađen: " + req.getIssuerSerialNumber()));

            validateIssuerCertificate(issuerData);
            validateValidityPeriod(req, issuerData);

            issuer = keyStoreReader.readIssuerFromStore(
                    keystorePath,
                    issuerData.getAlias(),
                    keystorePass.toCharArray(),
                    keystorePass.toCharArray()
            );

            if (issuer == null) {
                throw new RuntimeException("Nije moguće učitati issuera iz keystora. Proverite alias: "
                        + issuerData.getAlias());
            }

            subjectKeyPair = generateKeyPair();
        }

        Subject subject;
        if (req.getType() == CertificateType.ROOT) {
            subject = new Subject(issuer.getPublicKey(), issuer.getX500Name());
        } else {
            subject = new Subject(subjectKeyPair.getPublic(), buildX500Name(req));
        }

        String serialNumber = String.valueOf(System.currentTimeMillis());
        String alias = "cert-" + serialNumber;

        X509Certificate x509Cert = CertificateGenerator.generateCertificate(
                subject,
                issuer,
                req.getValidFrom(),
                req.getValidTo(),
                serialNumber,
                req.isBasicConstraintsCA() || req.getType() != CertificateType.END_ENTITY,
                req.isKeyCertSign(),
                req.isCRLSign(),
                req.isDigitalSignature(),
                req.isKeyEncipherment(),
                req.isServerAuth()
        );

        if (x509Cert == null) {
            throw new RuntimeException("Generisanje sertifikata nije uspelo.");
        }

        // Sačuvaj
        PrivateKey privateKeyToStore = (req.getType() == CertificateType.ROOT)
                ? issuer.getPrivateKey()
                : subjectKeyPair.getPrivate();

        keyStoreWriter.loadKeyStore(keystorePath, keystorePass.toCharArray());
        keyStoreWriter.write(alias, privateKeyToStore, keystorePass.toCharArray(), x509Cert);
        keyStoreWriter.saveKeyStore(keystorePath, keystorePass.toCharArray());

        Organization org = null;
        if (req.getOrganization() != null && !req.getOrganization().isBlank()) {
            org = orgRepo.findByName(req.getOrganization())
                    .orElseGet(() -> orgRepo.save(new Organization(null, req.getOrganization())));
        }

        Certificate certData = Certificate.builder()
                .serialNumber(serialNumber)
                .alias(alias)
                .type(req.getType())
                .subjectCN(req.getCommonName())
                .subjectO(req.getOrganization())
                .subjectOU(req.getOrganizationUnit())
                .subjectC(req.getCountry())
                .subjectEmail(req.getEmail())
                .validFrom(req.getValidFrom())
                .validTo(req.getValidTo())
                .issuerSerialNumber(req.getType() == CertificateType.ROOT ? null : req.getIssuerSerialNumber())
                .revoked(false)
                .issuingOrg(org)
                .build();

        return certificateRepository.save(certData);
    }

    public void validateIssuerCertificate(Certificate issuerData) {
        Date now = new Date();

        if (now.before(issuerData.getValidFrom())) {
            throw new RuntimeException(
                    "Issuer sertifikat '" + issuerData.getSubjectCN() + "' još uvek nije počeo da važi.");
        }
        if (now.after(issuerData.getValidTo())) {
            throw new RuntimeException(
                    "Issuer sertifikat '" + issuerData.getSubjectCN() + "' je istekao.");
        }

        if (issuerData.isRevoked()) {
            throw new RuntimeException(
                    "Issuer sertifikat '" + issuerData.getSubjectCN() + "' je povučen (razlog: "
                            + issuerData.getRevocationReason() + ").");
        }

        if (issuerData.getType() == CertificateType.END_ENTITY) {
            throw new RuntimeException(
                    "End-Entity sertifikat ne može biti issuer.");
        }

        if (issuerData.getIssuerSerialNumber() != null) {
            Certificate parentIssuer = certificateRepository
                    .findBySerialNumber(issuerData.getIssuerSerialNumber())
                    .orElseThrow(() -> new RuntimeException(
                            "Issuer lanca nije pronađen u bazi: " + issuerData.getIssuerSerialNumber()));
            validateIssuerCertificate(parentIssuer);
        }
    }

    private void validateValidityPeriod(IssueCertificateRequest req, Certificate issuerData) {
        if (req.getValidFrom().before(issuerData.getValidFrom())) {
            throw new RuntimeException(
                    "Datum početka ne može biti pre početka važenja issuera ("
                            + issuerData.getValidFrom() + ").");
        }
        if (req.getValidTo().after(issuerData.getValidTo())) {
            throw new RuntimeException(
                    "Datum isteka ne može biti posle isteka issuera ("
                            + issuerData.getValidTo() + ").");
        }
    }

    public List<CertificateDTO> getAllCertificates() {
        List<Certificate> certs =  certificateRepository.findAll();
        List<CertificateDTO> dtos = new ArrayList<>();
        for (Certificate cert : certs) {
            dtos.add(populateDto(cert));
        }
        return dtos;
    }

    public CertificateDTO getCertificateBySerial(String serialNumber) {
        return populateDto(certificateRepository.findBySerialNumber(serialNumber)
                .orElseThrow(() -> new RuntimeException("Sertifikat nije pronađen: " + serialNumber)));
    }


    public List<CertificateDTO> getAvailableIssuers() {
        List<Certificate> certs =  certificateRepository.findByTypeInAndRevokedFalse(
                List.of(CertificateType.ROOT, CertificateType.INTERMEDIATE)
        );
        List<CertificateDTO> dtos = new ArrayList<>();
        for (Certificate cert : certs) {
            dtos.add(populateDto(cert));
        }
        return dtos;
    }

    private X500Name buildX500Name(IssueCertificateRequest req) {
        X500NameBuilder builder = new X500NameBuilder(BCStyle.INSTANCE);

        if (req.getCommonName() != null && !req.getCommonName().isBlank())
            builder.addRDN(BCStyle.CN, req.getCommonName());

        if (req.getOrganization() != null && !req.getOrganization().isBlank())
            builder.addRDN(BCStyle.O, req.getOrganization());

        if (req.getOrganizationUnit() != null && !req.getOrganizationUnit().isBlank())
            builder.addRDN(BCStyle.OU, req.getOrganizationUnit());

        if (req.getCountry() != null && !req.getCountry().isBlank())
            builder.addRDN(BCStyle.C, req.getCountry());

        if (req.getEmail() != null && !req.getEmail().isBlank())
            builder.addRDN(BCStyle.E, req.getEmail());

        return builder.build();
    }

    private KeyPair generateKeyPair() {
        try {
            KeyPairGenerator keyGen = KeyPairGenerator.getInstance("RSA");
            SecureRandom random = SecureRandom.getInstance("SHA1PRNG", "SUN");
            keyGen.initialize(2048, random);
            return keyGen.generateKeyPair();
        } catch (NoSuchAlgorithmException | NoSuchProviderException e) {
            throw new RuntimeException("Greška pri generisanju ključeva: " + e.getMessage(), e);
        }
    }
}