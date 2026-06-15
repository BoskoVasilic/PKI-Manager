package com.tim12.pk_infrastructure.service;

import com.tim12.pk_infrastructure.keystores.KeyStoreReader;
import com.tim12.pk_infrastructure.keystores.KeyStoreWriter;
import com.tim12.pk_infrastructure.model.Certificate;
import com.tim12.pk_infrastructure.model.Issuer;
import com.tim12.pk_infrastructure.model.Organization;
import com.tim12.pk_infrastructure.model.User;
import com.tim12.pk_infrastructure.model.dtos.CertificateDTO;
import com.tim12.pk_infrastructure.model.dtos.csr.CsrRequestDto;
import com.tim12.pk_infrastructure.model.dtos.csr.CsrResponseDto;
import com.tim12.pk_infrastructure.model.dtos.csr.CsrUploadDto;
import com.tim12.pk_infrastructure.model.enums.CertificateStatus;
import com.tim12.pk_infrastructure.model.enums.CertificateType;
import com.tim12.pk_infrastructure.repository.CertificateRepository;
import com.tim12.pk_infrastructure.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.bouncycastle.asn1.ASN1ObjectIdentifier;
import org.bouncycastle.asn1.x500.RDN;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x500.X500NameBuilder;
import org.bouncycastle.asn1.x500.style.BCStyle;
import org.bouncycastle.asn1.x509.BasicConstraints;
import org.bouncycastle.asn1.x509.Extension;
import org.bouncycastle.asn1.x509.KeyUsage;
import org.bouncycastle.cert.X509CertificateHolder;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509ExtensionUtils;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.openssl.PEMParser;
import org.bouncycastle.openssl.jcajce.JcaPEMKeyConverter;
import org.bouncycastle.openssl.jcajce.JcaPEMWriter;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.bouncycastle.pkcs.PKCS10CertificationRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.io.File;
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

@Service
@RequiredArgsConstructor
public class CsrService {

    private final CertificateRepository  certificateRepository;
    private final UserRepository         userRepository;
    private final KeyStoreReader         keyStoreReader;
    private final KeyStoreWriter         keyStoreWriter;
    private final KeyEncryptionService   keyEncryptionService;

    @Value("${pki.keystore.dir}")
    private String keystoreDir;

    static {
        if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
            Security.addProvider(new BouncyCastleProvider());
        }
    }

    public List<CertificateDTO> listAvailableCas() {
        Date now = new Date();
        return certificateRepository
                .findByTypeInAndRevokedFalse(List.of(CertificateType.ROOT, CertificateType.INTERMEDIATE))
                .stream()
                .filter(c -> c.getStatus() == CertificateStatus.ACTIVE)
                .filter(c -> !now.before(c.getValidFrom()) && !now.after(c.getValidTo()))
                .map(this::toDto)
                .toList();
    }

    @Transactional
    public CsrResponseDto autogenerate(CsrRequestDto request) throws Exception {
        User        currentUser = getCurrentUser();
        Certificate ca          = loadAndValidateCa(request.getCaSerialNumber());

        Date validFrom = toDate(request.getValidFrom() != null ? request.getValidFrom() : LocalDateTime.now());
        Date validTo   = toDate(request.getValidTo());
        validateValidityPeriod(validFrom, validTo, ca);

        // Generate RSA 2048 key pair
        KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA", BouncyCastleProvider.PROVIDER_NAME);
        kpg.initialize(2048, new SecureRandom());
        KeyPair keyPair = kpg.generateKeyPair();

        X500Name subjectName = buildX500Name(
                request.getCn(), request.getOrganization(),
                request.getOrganizationUnit(), request.getCountry(), request.getEmail());

        Issuer issuer = loadCaIssuer(ca);

        BigInteger serial    = newSerial();
        String     serialHex = serial.toString(16);
        String     alias     = "cert-" + serialHex;

        X509Certificate x509    = buildAndSign(issuer, serial, validFrom, validTo, subjectName, keyPair.getPublic());
        String          certPem = toCertPem(x509);

        // Build an in-memory JKS containing the cert + private key.
        // The private key is NOT written to any persistent store – it lives only
        // in this response.  The user must download it immediately.
        String ksPassword = generateKeystorePassword();
        byte[] jksBytes   = buildInMemoryJks(alias, keyPair.getPrivate(), x509, ksPassword);
        String jksBase64  = Base64.getEncoder().encodeToString(jksBytes);
        byte[] p12Bytes   = buildInMemoryP12(alias, keyPair.getPrivate(), x509, ksPassword);
        String p12Base64  = Base64.getEncoder().encodeToString(p12Bytes);

        Organization caOrg = ca.getIssuingOrg();

        Certificate entity = Certificate.builder()
                .serialNumber(serialHex)
                .alias(alias)
                .subjectCN(request.getCn())
                .subjectO(request.getOrganization())
                .subjectOU(request.getOrganizationUnit())
                .subjectC(request.getCountry())
                .subjectEmail(request.getEmail())
                .issuerCN(ca.getSubjectCN())
                .issuerSerialNumber(ca.getSerialNumber())
                .validFrom(validFrom)
                .validTo(validTo)
                .type(CertificateType.END_ENTITY)
                .status(CertificateStatus.ACTIVE)
                .revoked(false)
                .certificatePem(certPem)
                .encryptedPrivateKey(null)
                .owner(currentUser)
                .issuingOrg(caOrg)
                .privateKeyAvailable(false)
                .build();

        certificateRepository.save(entity);

        return CsrResponseDto.builder()
                .serialNumber(serialHex)
                .certificatePem(certPem)
                .keystoreBase64(jksBase64)
                .p12Base64(p12Base64)
                .keystorePassword(ksPassword)
                .message("Certificate generated. Download the JKS or P12 keystore now – the private key will not be available again.")
                .build();
    }

    @Transactional
    public CsrResponseDto uploadCsr(CsrUploadDto request) throws Exception {
        User        currentUser = getCurrentUser();
        Certificate ca          = loadAndValidateCa(request.getCaSerialNumber());

        Date validFrom = toDate(request.getValidFrom() != null ? request.getValidFrom() : LocalDateTime.now());
        Date validTo;
        if (request.getValidTo() == null) {
            throw new IllegalArgumentException("validTo is required.");
        }
        validTo = toDate(request.getValidTo());
        validateValidityPeriod(validFrom, validTo, ca);

        // Parse the PKCS#10 CSR
        PKCS10CertificationRequest csr = parseCsr(request.getCsrPem());

        PublicKey subjectKey  = new JcaPEMKeyConverter()
                .setProvider(BouncyCastleProvider.PROVIDER_NAME)
                .getPublicKey(csr.getSubjectPublicKeyInfo());
        X500Name  subjectName = csr.getSubject();

        // Extract subject fields for DB persistence
        String cn    = extractRdn(subjectName, BCStyle.CN);
        String o     = extractRdn(subjectName, BCStyle.O);
        String ou    = extractRdn(subjectName, BCStyle.OU);
        String c     = extractRdn(subjectName, BCStyle.C);
        String email = extractRdn(subjectName, BCStyle.EmailAddress);
        if (email == null) email = extractRdn(subjectName, BCStyle.E);

        Issuer issuer = loadCaIssuer(ca);

        BigInteger serial    = newSerial();
        String     serialHex = serial.toString(16);
        String     alias     = "cert-" + serialHex;

        X509Certificate x509    = buildAndSign(issuer, serial, validFrom, validTo, subjectName, subjectKey);
        String          certPem = toCertPem(x509);

        Certificate entity = Certificate.builder()
                .serialNumber(serialHex)
                .alias(alias)
                .subjectCN(cn)
                .subjectO(o)
                .subjectOU(ou)
                .subjectC(c)
                .subjectEmail(email)
                .issuerCN(ca.getSubjectCN())
                .issuerSerialNumber(ca.getSerialNumber())
                .validFrom(validFrom)
                .validTo(validTo)
                .type(CertificateType.END_ENTITY)
                .status(CertificateStatus.ACTIVE)
                .revoked(false)
                .certificatePem(certPem)
                .encryptedPrivateKey(null)
                .owner(currentUser)
                .issuingOrg(ca.getIssuingOrg())
                .privateKeyAvailable(false)
                .build();

        certificateRepository.save(entity);

        return CsrResponseDto.builder()
                .serialNumber(serialHex)
                .certificatePem(certPem)
                .message("CSR signed successfully. Download the certificate below.")
                .build();
    }

    // ─── Private helpers ──────────────────────────────────────────────────────

    private User getCurrentUser() {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("Authenticated user not found."));
    }

    private Certificate loadAndValidateCa(String caSerialNumber) {
        Certificate ca = certificateRepository.findBySerialNumber(caSerialNumber)
                .orElseThrow(() -> new IllegalArgumentException(
                        "CA certificate not found: " + caSerialNumber));

        if (ca.isRevoked() || ca.getStatus() == CertificateStatus.REVOKED) {
            throw new IllegalArgumentException(
                    "CA certificate '" + ca.getSubjectCN() + "' has been revoked.");
        }
        if (ca.getType() == CertificateType.END_ENTITY) {
            throw new IllegalArgumentException(
                    "An End-Entity certificate cannot sign other certificates.");
        }

        Date now = new Date();
        if (now.before(ca.getValidFrom())) {
            throw new IllegalArgumentException(
                    "CA certificate '" + ca.getSubjectCN() + "' is not yet valid.");
        }
        if (now.after(ca.getValidTo())) {
            throw new IllegalArgumentException(
                    "CA certificate '" + ca.getSubjectCN() + "' has expired.");
        }

        // Recursively validate the chain
        if (ca.getIssuerSerialNumber() != null) {
            loadAndValidateCa(ca.getIssuerSerialNumber());
        }

        return ca;
    }

    private void validateValidityPeriod(Date validFrom, Date validTo, Certificate ca) {
        if (!validFrom.before(validTo)) {
            throw new IllegalArgumentException("validFrom must be before validTo.");
        }
        if (validFrom.before(ca.getValidFrom())) {
            throw new IllegalArgumentException(
                    "Certificate validity cannot start before the CA's validity period (" + ca.getValidFrom() + ").");
        }
        if (validTo.after(ca.getValidTo())) {
            throw new IllegalArgumentException(
                    "Certificate validity cannot expire after the CA's validity period (" + ca.getValidTo() + ").");
        }
    }

    private Issuer loadCaIssuer(Certificate ca) {
        Organization org = ca.getIssuingOrg();
        if (org == null) {
            throw new RuntimeException(
                    "CA certificate '" + ca.getSubjectCN() + "' has no associated organization.");
        }
        String ksPath = resolveKeyStorePath(org);
        char[] ksPass = resolveOrgKeyStorePassword(org);

        Issuer issuer = keyStoreReader.readIssuerFromStore(ksPath, ca.getAlias(), ksPass, ksPass);
        if (issuer == null) {
            throw new RuntimeException(
                    "Cannot load CA private key from keystore (path: " + ksPath + ", alias: " + ca.getAlias() + ").");
        }
        return issuer;
    }

    private X509Certificate buildAndSign(Issuer issuer, BigInteger serial,
                                         Date notBefore, Date notAfter,
                                         X500Name subjectName, PublicKey subjectKey) throws Exception {

        JcaX509v3CertificateBuilder builder = new JcaX509v3CertificateBuilder(
                issuer.getX500Name(), serial, notBefore, notAfter, subjectName, subjectKey);

        JcaX509ExtensionUtils ext = new JcaX509ExtensionUtils();
        builder.addExtension(Extension.subjectKeyIdentifier,   false,
                ext.createSubjectKeyIdentifier(subjectKey));
        builder.addExtension(Extension.authorityKeyIdentifier, false,
                ext.createAuthorityKeyIdentifier(issuer.getPublicKey()));
        builder.addExtension(Extension.basicConstraints, true,
                new BasicConstraints(false)); // EE cert
        builder.addExtension(Extension.keyUsage, true,
                new KeyUsage(KeyUsage.digitalSignature | KeyUsage.keyEncipherment));

        ContentSigner signer = new JcaContentSignerBuilder("SHA256WithRSA")
                .setProvider(BouncyCastleProvider.PROVIDER_NAME)
                .build(issuer.getPrivateKey());

        X509CertificateHolder holder = builder.build(signer);
        return new JcaX509CertificateConverter()
                .setProvider(BouncyCastleProvider.PROVIDER_NAME)
                .getCertificate(holder);
    }

    private PKCS10CertificationRequest parseCsr(String pem) {
        try (PEMParser parser = new PEMParser(new StringReader(pem))) {
            Object obj = parser.readObject();
            if (!(obj instanceof PKCS10CertificationRequest csr)) {
                throw new IllegalArgumentException(
                        "Invalid format. Expected a PEM-encoded PKCS#10 CSR (-----BEGIN CERTIFICATE REQUEST-----).");
            }
            return csr;
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalArgumentException("Failed to parse CSR: " + e.getMessage(), e);
        }
    }

    private X500Name buildX500Name(String cn, String o, String ou, String c, String email) {
        X500NameBuilder b = new X500NameBuilder(BCStyle.INSTANCE);
        if (cn    != null && !cn.isBlank())    b.addRDN(BCStyle.CN, cn);
        if (o     != null && !o.isBlank())     b.addRDN(BCStyle.O,  o);
        if (ou    != null && !ou.isBlank())    b.addRDN(BCStyle.OU, ou);
        if (c     != null && !c.isBlank())     b.addRDN(BCStyle.C,  c);
        if (email != null && !email.isBlank()) b.addRDN(BCStyle.E,  email);
        return b.build();
    }

    private String extractRdn(X500Name name, ASN1ObjectIdentifier oid) {
        RDN[] rdns = name.getRDNs(oid);
        if (rdns == null || rdns.length == 0) return null;
        return rdns[0].getFirst().getValue().toString();
    }

    private String resolveKeyStorePath(Organization org) {
        if (org.getKeyStoreFileName() == null) {
            throw new RuntimeException(
                    "Organization '" + org.getName() + "' has no keystore file name set.");
        }
        return new File(keystoreDir, org.getKeyStoreFileName()).getPath();
    }

    private char[] resolveOrgKeyStorePassword(Organization org) {
        if (org.getKeyStorePassword() == null) {
            throw new RuntimeException(
                    "Organization '" + org.getName() + "' has no keystore password set.");
        }
        return keyEncryptionService.decrypt(org.getKeyStorePassword()).toCharArray();
    }

    private static Date toDate(LocalDateTime ldt) {
        return Date.from(ldt.atZone(ZoneId.systemDefault()).toInstant());
    }

    private static BigInteger newSerial() {
        return new BigInteger(UUID.randomUUID().toString().replace("-", ""), 16).abs();
    }

    private String toCertPem(X509Certificate cert) throws Exception {
        StringWriter sw = new StringWriter();
        try (JcaPEMWriter w = new JcaPEMWriter(sw)) { w.writeObject(cert); }
        return sw.toString();
    }

    private byte[] buildInMemoryJks(String alias, PrivateKey privateKey,
                                    X509Certificate cert, String password) throws Exception {
        KeyStore ks = KeyStore.getInstance("JKS", "SUN");
        ks.load(null, password.toCharArray());
        ks.setKeyEntry(alias, privateKey, password.toCharArray(),
                new java.security.cert.Certificate[]{cert});
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ks.store(baos, password.toCharArray());
        return baos.toByteArray();
    }

    private byte[] buildInMemoryP12(String alias, PrivateKey privateKey,
                                    X509Certificate cert, String password) throws Exception {
        KeyStore ks = KeyStore.getInstance("PKCS12");
        ks.load(null, password.toCharArray());
        ks.setKeyEntry(alias, privateKey, password.toCharArray(),
                new java.security.cert.Certificate[]{cert});
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ks.store(baos, password.toCharArray());
        return baos.toByteArray();
    }

    private String generateKeystorePassword() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 16);
    }

    private String toKeyPem(PrivateKey key) throws Exception {
        StringWriter sw = new StringWriter();
        try (JcaPEMWriter w = new JcaPEMWriter(sw)) { w.writeObject(key); }
        return sw.toString();
    }

    private CertificateDTO toDto(Certificate c) {
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
                .privateKeyAvailable(c.isPrivateKeyAvailable())
                .build();
    }
}