package com.tim12.pk_infrastructure.service;

import com.tim12.pk_infrastructure.certificates.CertificateGenerator;
import com.tim12.pk_infrastructure.keystores.KeyStoreReader;
import com.tim12.pk_infrastructure.keystores.KeyStoreWriter;
import com.tim12.pk_infrastructure.model.*;
import com.tim12.pk_infrastructure.model.Certificate;
import com.tim12.pk_infrastructure.model.dtos.CertificateDTO;
import com.tim12.pk_infrastructure.model.dtos.IssueCertificateRequest;
import com.tim12.pk_infrastructure.model.dtos.IssueCertificateRequestCA;
import com.tim12.pk_infrastructure.model.enums.CertificateStatus;
import com.tim12.pk_infrastructure.model.enums.CertificateType;
import com.tim12.pk_infrastructure.repository.CertificateRepository;
import com.tim12.pk_infrastructure.repository.OrganizatioRepository;
import com.tim12.pk_infrastructure.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x500.X500NameBuilder;
import org.bouncycastle.asn1.x500.style.BCStyle;
import org.bouncycastle.asn1.x509.*;
import org.bouncycastle.cert.X509CertificateHolder;
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
import org.springframework.beans.factory.annotation.Value;
import java.io.*;
import java.math.BigInteger;
import java.security.*;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CertificateService {

    private final CertificateRepository   certificateRepository;
    private final UserRepository          userRepository;
    private final OrganizatioRepository   orgRepo;
    private final KeyStoreReader          keyStoreReader;
    private final KeyStoreWriter          keyStoreWriter;
    private final KeyEncryptionService    keyEncryptionService;   // NEW

    @Value("${pki.keystore.dir}")
    private String keystoreDir;

    private User getCurrentUser() {
        String email = SecurityContextHolder.getContext()
                .getAuthentication().getName();
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("Authenticated user not found"));
    }

    public List<CertificateDTO> getMyEndEntityCertificates() {
        User currentUser = getCurrentUser();
        return certificateRepository
                .findByOwnerAndType(currentUser, CertificateType.END_ENTITY)
                .stream()
                .map(this::toDto)
                .toList();
    }

    public CertificateDTO getMyCertificateBySerial(String serialNumber) {
        User currentUser = getCurrentUser();
        Certificate cert = certificateRepository
                .findBySerialNumberAndOwner(serialNumber, currentUser)
                .orElseThrow(() -> new RuntimeException("Certificate not found"));
        return toDto(cert);
    }

    public CertificateDTO issueCertificate(IssueCertificateRequestCA req) throws Exception {

        User caller = getCurrentUser();

        CertificateType requestedType = CertificateType.valueOf(req.getType());
        if (requestedType == CertificateType.ROOT) {
            throw new IllegalArgumentException("ROOT certificates cannot be issued through this endpoint.");
        }

        Certificate issuerRecord = certificateRepository
                .findBySerialNumber(req.getIssuerSerialNumber())
                .orElseThrow(() -> new IllegalArgumentException("Issuer certificate not found."));

        validateIssuer(issuerRecord, caller);

        X509Certificate issuerX509     = parseCertificatePem(issuerRecord.getCertificatePem());
        PrivateKey      issuerPrivKey  = parsePrivateKeyPem(issuerRecord.getEncryptedPrivateKey());

        KeyPairGenerator keyGen = KeyPairGenerator.getInstance("RSA");
        keyGen.initialize(2048);
        KeyPair subjectKeyPair = keyGen.generateKeyPair();

        X500Name subjectName = new X500Name(buildDn(req));
        X500Name issuerName  = new X500Name(issuerX509.getSubjectX500Principal().getName());

        Date from = req.getValidFrom();
        Date to   = req.getValidTo();
        if (to.after(issuerX509.getNotAfter())) {
            throw new IllegalArgumentException("Certificate validity cannot exceed issuer's validity period.");
        }

        BigInteger serial = new BigInteger(UUID.randomUUID().toString().replace("-", ""), 16).abs();

        JcaX509v3CertificateBuilder certBuilder = new JcaX509v3CertificateBuilder(
                issuerName, serial, from, to, subjectName, subjectKeyPair.getPublic());

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

        ContentSigner signer = new JcaContentSignerBuilder("SHA256WithRSA").build(issuerPrivKey);
        X509CertificateHolder holder = certBuilder.build(signer);
        X509Certificate signedCert   = new JcaX509CertificateConverter().getCertificate(holder);
        signedCert.verify(issuerX509.getPublicKey());

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
                .encryptedPrivateKey(privateKeyToPem(subjectKeyPair.getPrivate())) // TODO: encrypt before storing
                .owner(caller)
                .build();

        return toDto(certificateRepository.save(saved));
    }

    public List<CertificateDTO> getMyAvailableIssuers() {
        User caller = getCurrentUser();
        return certificateRepository
                .findByOwnerAndType(caller, CertificateType.ROOT)
                .stream()
                .filter(c -> c.getStatus() == CertificateStatus.ACTIVE)
                .map(this::toDto)
                .toList();
    }

    public Certificate issueCertificate(IssueCertificateRequest req) {

        Organization subjectOrg = null;
        if (req.getOrganization() != null && !req.getOrganization().isBlank()) {
            subjectOrg = getOrCreateOrg(req.getOrganization());
        }

        Issuer   issuer;
        KeyPair  subjectKeyPair;

        if (req.getType() == CertificateType.ROOT) {
            subjectKeyPair = generateKeyPair();
            X500Name name  = buildX500Name(req);
            issuer = new Issuer(subjectKeyPair.getPrivate(), subjectKeyPair.getPublic(), name);

        } else {
            Certificate issuerData = certificateRepository
                    .findBySerialNumber(req.getIssuerSerialNumber())
                    .orElseThrow(() -> new RuntimeException(
                            "Issuer sertifikat nije pronađen: " + req.getIssuerSerialNumber()));

            validateIssuerCertificate(issuerData);
            validateValidityPeriod(req, issuerData);

            Organization issuerOrg = issuerData.getIssuingOrg();
            if (issuerOrg == null) {
                throw new RuntimeException(
                        "Issuer sertifikat nema organizaciju – ne mogu pronaći keystore.");
            }

            String   issuerKsPath = resolveKeyStorePath(issuerOrg);
            char[]   issuerKsPass = resolveOrgKeyStorePassword(issuerOrg);

            issuer = keyStoreReader.readIssuerFromStore(
                    issuerKsPath,
                    issuerData.getAlias(),
                    issuerKsPass,
                    issuerKsPass
            );

            if (issuer == null) {
                throw new RuntimeException(
                        "Nije moguće učitati issuera iz keystora. Proverite alias: " + issuerData.getAlias());
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
        String alias        = "cert-" + serialNumber;

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

        if (subjectOrg == null) {
            throw new RuntimeException(
                    "Organizacija mora biti navedena kako bi se sertifikat sačuvao u keystore-u.");
        }

        PrivateKey privateKeyToStore = (req.getType() == CertificateType.ROOT)
                ? issuer.getPrivateKey()
                : subjectKeyPair.getPrivate();

        String subjectKsPath = resolveKeyStorePath(subjectOrg);
        char[] subjectKsPass = resolveOrgKeyStorePassword(subjectOrg);

        keyStoreWriter.write(subjectKsPath, alias, privateKeyToStore, subjectKsPass, x509Cert);

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
                .status(CertificateStatus.ACTIVE)
                .issuingOrg(subjectOrg)
                .build();

        return certificateRepository.save(certData);
    }

    public List<CertificateDTO> getAllCertificates() {
        List<Certificate>    certs = certificateRepository.findAll();
        List<CertificateDTO> dtos  = new ArrayList<>();
        for (Certificate cert : certs) dtos.add(populateDto(cert));
        return dtos;
    }

    public CertificateDTO getCertificateBySerial(String serialNumber) {
        return populateDto(certificateRepository.findBySerialNumber(serialNumber)
                .orElseThrow(() -> new RuntimeException("Sertifikat nije pronađen: " + serialNumber)));
    }

    public List<CertificateDTO> getAvailableIssuers() {
        List<Certificate> certs = certificateRepository.findByTypeInAndRevokedFalse(
                List.of(CertificateType.ROOT, CertificateType.INTERMEDIATE));
        List<CertificateDTO> dtos = new ArrayList<>();
        for (Certificate cert : certs) dtos.add(populateDto(cert));
        return dtos;
    }

    public Organization getOrCreateOrg(String orgName) {
        return orgRepo.findByName(orgName).orElseGet(() -> {

            String rawPassword       = keyEncryptionService.generateRandomPassword();
            String encryptedPassword = keyEncryptionService.encrypt(rawPassword);

            String safeName = orgName.replaceAll("[^a-zA-Z0-9_\\-]", "_");

            Organization newOrg = new Organization();
            newOrg.setName(orgName);
            newOrg.setKeyStoreFileName(safeName + ".jks");
            newOrg.setKeyStorePassword(encryptedPassword);
            Organization saved = orgRepo.save(newOrg);

            initOrgKeyStore(saved, rawPassword);

            return saved;
        });
    }

    private void initOrgKeyStore(Organization org, String rawPassword) {
        String path = resolveKeyStorePath(org);
        File   file = new File(path);

        if (file.exists()) return;

        try {
            file.getParentFile().mkdirs();
            KeyStore ks = KeyStore.getInstance("JKS", "SUN");
            ks.load(null, rawPassword.toCharArray());
            try (FileOutputStream fos = new FileOutputStream(file)) {
                ks.store(fos, rawPassword.toCharArray());
            }
        } catch (Exception e) {
            throw new RuntimeException(
                    "Failed to initialise keystore for org '" + org.getName() + "': " + e.getMessage(), e);
        }
    }


    private String resolveKeyStorePath(Organization org) {
        if (org.getKeyStoreFileName() == null) {
            throw new RuntimeException(
                    "Organization '" + org.getName() + "' has no keystore file name set.");
        }
        return keystoreDir + org.getKeyStoreFileName();
    }

    private char[] resolveOrgKeyStorePassword(Organization org) {
        if (org.getKeyStorePassword() == null) {
            throw new RuntimeException(
                    "Organization '" + org.getName() + "' has no keystore password set.");
        }
        return keyEncryptionService.decrypt(org.getKeyStorePassword()).toCharArray();
    }

    public void validateIssuerCertificate(Certificate issuerData) {
        Date now = new Date();

        if (now.before(issuerData.getValidFrom()))
            throw new RuntimeException("Issuer sertifikat '" + issuerData.getSubjectCN() + "' još uvek nije počeo da važi.");
        if (now.after(issuerData.getValidTo()))
            throw new RuntimeException("Issuer sertifikat '" + issuerData.getSubjectCN() + "' je istekao.");
        if (issuerData.isRevoked())
            throw new RuntimeException("Issuer sertifikat '" + issuerData.getSubjectCN() + "' je povučen (razlog: " + issuerData.getRevocationReason() + ").");
        if (issuerData.getType() == CertificateType.END_ENTITY)
            throw new RuntimeException("End-Entity sertifikat ne može biti issuer.");

        if (issuerData.getIssuerSerialNumber() != null) {
            Certificate parent = certificateRepository
                    .findBySerialNumber(issuerData.getIssuerSerialNumber())
                    .orElseThrow(() -> new RuntimeException(
                            "Issuer lanca nije pronađen u bazi: " + issuerData.getIssuerSerialNumber()));
            validateIssuerCertificate(parent);
        }
    }

    private void validateIssuer(Certificate issuer, User caller) throws Exception {
        if (!issuer.getOwner().getId().equals(caller.getId()))
            throw new SecurityException("You can only use CA certificates you own.");
        if (issuer.getStatus() == CertificateStatus.REVOKED)
            throw new IllegalArgumentException("Issuer certificate has been revoked.");
        if (issuer.getType() == CertificateType.END_ENTITY)
            throw new IllegalArgumentException("End-entity certificates cannot sign other certificates.");
        parseCertificatePem(issuer.getCertificatePem()).checkValidity();
    }

    private void validateValidityPeriod(IssueCertificateRequest req, Certificate issuerData) {
        if (req.getValidFrom().before(issuerData.getValidFrom()))
            throw new RuntimeException("Datum početka ne može biti pre početka važenja issuera (" + issuerData.getValidFrom() + ").");
        if (req.getValidTo().after(issuerData.getValidTo()))
            throw new RuntimeException("Datum isteka ne može biti posle isteka issuera (" + issuerData.getValidTo() + ").");
    }

    private String buildDn(IssueCertificateRequestCA req) {
        StringBuilder sb = new StringBuilder();
        if (req.getCommonName()        != null) sb.append("CN=").append(req.getCommonName()).append(",");
        if (req.getOrganization()      != null) sb.append("O=").append(req.getOrganization()).append(",");
        if (req.getOrganizationalUnit()!= null) sb.append("OU=").append(req.getOrganizationalUnit()).append(",");
        if (req.getCountry()           != null) sb.append("C=").append(req.getCountry()).append(",");
        if (req.getEmail()             != null) sb.append("E=").append(req.getEmail()).append(",");
        String dn = sb.toString();
        return dn.endsWith(",") ? dn.substring(0, dn.length() - 1) : dn;
    }

    private X500Name buildX500Name(IssueCertificateRequest req) {
        X500NameBuilder b = new X500NameBuilder(BCStyle.INSTANCE);
        if (req.getCommonName()       != null && !req.getCommonName().isBlank())       b.addRDN(BCStyle.CN, req.getCommonName());
        if (req.getOrganization()     != null && !req.getOrganization().isBlank())     b.addRDN(BCStyle.O,  req.getOrganization());
        if (req.getOrganizationUnit() != null && !req.getOrganizationUnit().isBlank()) b.addRDN(BCStyle.OU, req.getOrganizationUnit());
        if (req.getCountry()          != null && !req.getCountry().isBlank())          b.addRDN(BCStyle.C,  req.getCountry());
        if (req.getEmail()            != null && !req.getEmail().isBlank())             b.addRDN(BCStyle.E,  req.getEmail());
        return b.build();
    }

    private int buildKeyUsageBits(List<String> usages) {
        int bits = 0;
        for (String u : usages) {
            switch (u.toUpperCase()) {
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

    private KeyPair generateKeyPair() {
        try {
            KeyPairGenerator kg = KeyPairGenerator.getInstance("RSA");
            SecureRandom rng    = SecureRandom.getInstance("SHA1PRNG", "SUN");
            kg.initialize(2048, rng);
            return kg.generateKeyPair();
        } catch (NoSuchAlgorithmException | NoSuchProviderException e) {
            throw new RuntimeException("Greška pri generisanju ključeva: " + e.getMessage(), e);
        }
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

    private CertificateDTO toDto(Certificate c) {
        return populateDto(c);
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
                .issuerSerialNumber(c.getIssuerSerialNumber())
                .build();
    }
}