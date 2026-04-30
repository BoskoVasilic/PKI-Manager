package com.tim12.pk_infrastructure.service;

import com.tim12.pk_infrastructure.dto.csr.CsrRequestDto;
import com.tim12.pk_infrastructure.dto.csr.CsrResponseDto;
import com.tim12.pk_infrastructure.dto.csr.CsrUploadDto;
import com.tim12.pk_infrastructure.keystores.KeyStoreReader;
import com.tim12.pk_infrastructure.keystores.KeyStoreWriter;
import com.tim12.pk_infrastructure.model.Certificate;
import com.tim12.pk_infrastructure.model.CertificateStatus;
import com.tim12.pk_infrastructure.model.CertificateType;
import com.tim12.pk_infrastructure.model.User;
import com.tim12.pk_infrastructure.repository.CertificateRepository;
import com.tim12.pk_infrastructure.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x500.X500NameBuilder;
import org.bouncycastle.asn1.x500.style.BCStyle;
import org.bouncycastle.cert.X509CertificateHolder;
import org.bouncycastle.cert.X509v3CertificateBuilder;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.openssl.PEMParser;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.bouncycastle.pkcs.PKCS10CertificationRequest;
import org.bouncycastle.util.io.pem.PemObject;
import org.bouncycastle.util.io.pem.PemWriter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.math.BigInteger;
import java.security.*;
import java.security.cert.X509Certificate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;

@Service
@RequiredArgsConstructor
public class CsrService {

    private final CertificateRepository certificateRepository;
    private final UserRepository userRepository;
    private final KeyStoreReader keyStoreReader;
    private final KeyStoreWriter keyStoreWriter;

    /**
     * Directory where per-organization .jks files are stored.
     * Set pki.keystore.dir=keystores in application.properties.
     */
    @Value("${pki.keystore.dir:keystores}")
    private String keystoreDir;

    /**
     * Master password used to protect all KeyStore files.
     * Set pki.master.password=... in application.properties.
     * Never commit this value to source control.
     */
    @Value("${pki.master.password}")
    private String masterPassword;

    static {
        if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
            Security.addProvider(new BouncyCastleProvider());
        }
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private User getCurrentUser() {
        String email = SecurityContextHolder.getContext()
                .getAuthentication().getName();
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("Autentifikovani korisnik nije pronađen"));
    }

    private Certificate loadAndValidateCa(String caSerialNumber) {
        Certificate ca = certificateRepository.findBySerialNumber(caSerialNumber)
                .orElseThrow(() -> new RuntimeException("CA sertifikat nije pronađen: " + caSerialNumber));
        if (ca.getStatus() == CertificateStatus.REVOKED) {
            throw new RuntimeException("CA sertifikat je povučen i ne može se koristiti za izdavanje");
        }
        if (ca.getType() == CertificateType.END_ENTITY) {
            throw new RuntimeException("End-entity sertifikat ne može biti CA za izdavanje");
        }
        LocalDateTime now = LocalDateTime.now();
        if (now.isBefore(ca.getValidFrom()) || now.isAfter(ca.getValidTo())) {
            throw new RuntimeException("CA sertifikat nije u periodu važenja");
        }
        return ca;
    }

    private void validateValidityPeriod(LocalDateTime validFrom, LocalDateTime validTo, Certificate ca) {
        if (validFrom == null || validTo == null) {
            throw new RuntimeException("Period važenja je obavezan");
        }
        if (!validFrom.isBefore(validTo)) {
            throw new RuntimeException("Datum početka mora biti pre datuma kraja važenja");
        }
        if (validFrom.isBefore(ca.getValidFrom())) {
            throw new RuntimeException("Period važenja EE sertifikata ne može početi pre CA sertifikata");
        }
        if (validTo.isAfter(ca.getValidTo())) {
            throw new RuntimeException("Period važenja EE sertifikata ne sme preći period važenja CA sertifikata");
        }
    }

    /**
     * Resolves the organization name used as the KeyStore file name.
     * Priority: subjectO field → email domain of the user.
     */
    private String resolveOrgName(String subjectO, User user) {
        if (subjectO != null && !subjectO.isBlank()) {
            return subjectO.replaceAll("[^a-zA-Z0-9._-]", "_");
        }
        String email = user.getEmail();
        if (email != null && email.contains("@")) {
            return email.substring(email.indexOf('@') + 1).replaceAll("[^a-zA-Z0-9._-]", "_");
        }
        return "default";
    }

    /** Returns the full path to the .jks file for a given organization. */
    private String keystorePath(String orgName) {
        return keystoreDir + "/" + orgName + ".jks";
    }

    /** KeyStore password — master password protects all keystores. */
    private char[] keystorePassword() {
        return masterPassword.toCharArray();
    }

    /**
     * Loads the CA's private key from the organization's JKS KeyStore file.
     * Alias in the KeyStore = certificate serial number.
     */
    private PrivateKey loadCaPrivateKey(Certificate ca) {
        String orgName = resolveOrgName(ca.getSubjectO(), ca.getOwner());
        String filePath = keystorePath(orgName);
        char[] password = keystorePassword();

        PrivateKey pk = keyStoreReader.readPrivateKey(filePath, ca.getSerialNumber(), password, password);
        if (pk == null) {
            throw new RuntimeException(
                    "Privatni ključ CA sertifikata nije pronađen u KeyStore-u. " +
                            "Fajl: " + filePath + ", alias: " + ca.getSerialNumber()
            );
        }
        return pk;
    }

    private String convertCertToPem(X509Certificate cert) {
        try {
            StringWriter sw = new StringWriter();
            try (PemWriter pw = new PemWriter(sw)) {
                pw.writeObject(new PemObject("CERTIFICATE", cert.getEncoded()));
            }
            return sw.toString();
        } catch (Exception e) {
            throw new RuntimeException("Greška pri konverziji sertifikata u PEM: " + e.getMessage(), e);
        }
    }

    private String convertPrivateKeyToPem(PrivateKey key) {
        try {
            StringWriter sw = new StringWriter();
            try (PemWriter pw = new PemWriter(sw)) {
                pw.writeObject(new PemObject("PRIVATE KEY", key.getEncoded()));
            }
            return sw.toString();
        } catch (IOException e) {
            throw new RuntimeException("Greška pri konverziji privatnog ključa u PEM: " + e.getMessage(), e);
        }
    }

    private X500Name buildX500Name(String cn, String o, String ou, String c, String email) {
        X500NameBuilder builder = new X500NameBuilder(BCStyle.INSTANCE);
        if (cn    != null && !cn.isBlank())    builder.addRDN(BCStyle.CN, cn);
        if (o     != null && !o.isBlank())     builder.addRDN(BCStyle.O,  o);
        if (ou    != null && !ou.isBlank())    builder.addRDN(BCStyle.OU, ou);
        if (c     != null && !c.isBlank())     builder.addRDN(BCStyle.C,  c);
        if (email != null && !email.isBlank()) builder.addRDN(BCStyle.E,  email);
        return builder.build();
    }

    private X509Certificate signCertificate(
            X500Name subjectX500Name,
            PublicKey subjectPublicKey,
            Certificate ca,
            PrivateKey caPrivateKey,
            LocalDateTime validFrom,
            LocalDateTime validTo) throws Exception {

        X500Name issuerX500Name = buildX500Name(
                ca.getSubjectCN(), ca.getSubjectO(), ca.getSubjectOU(),
                ca.getSubjectC(), ca.getSubjectEmail());

        Date notBefore = Date.from(validFrom.atZone(ZoneId.systemDefault()).toInstant());
        Date notAfter  = Date.from(validTo.atZone(ZoneId.systemDefault()).toInstant());
        BigInteger serialNumber = new BigInteger(64, new SecureRandom());

        X509v3CertificateBuilder certBuilder = new JcaX509v3CertificateBuilder(
                issuerX500Name, serialNumber, notBefore, notAfter,
                subjectX500Name, subjectPublicKey);

        ContentSigner signer = new JcaContentSignerBuilder("SHA256WithRSAEncryption")
                .setProvider(BouncyCastleProvider.PROVIDER_NAME)
                .build(caPrivateKey);

        X509CertificateHolder holder = certBuilder.build(signer);
        return new JcaX509CertificateConverter()
                .setProvider(BouncyCastleProvider.PROVIDER_NAME)
                .getCertificate(holder);
    }

    private Certificate persistCertificate(
            X509Certificate x509,
            String subjectCN, String subjectO, String subjectOU,
            String subjectC, String subjectEmail,
            Certificate ca,
            LocalDateTime validFrom,
            LocalDateTime validTo,
            User owner,
            String certificatePem) {

        Certificate cert = Certificate.builder()
                .serialNumber(x509.getSerialNumber().toString(16).toUpperCase())
                .subjectCN(subjectCN)
                .subjectO(subjectO)
                .subjectOU(subjectOU)
                .subjectC(subjectC)
                .subjectEmail(subjectEmail)
                .issuerCN(ca.getSubjectCN())
                .issuerSerialNumber(ca.getSerialNumber())
                .validFrom(validFrom)
                .validTo(validTo)
                .type(CertificateType.END_ENTITY)
                .status(CertificateStatus.ACTIVE)
                .certificatePem(certificatePem)
                // Private key lives in the org's JKS KeyStore file, not in the DB.
                .encryptedPrivateKey(null)
                .owner(owner)
                .build();

        return certificateRepository.save(cert);
    }

    // -------------------------------------------------------------------------
    // Public methods
    // -------------------------------------------------------------------------

    /**
     * Autogenerate: server generates RSA 2048-bit key pair, signs EE cert with chosen CA.
     *
     * The generated private key is:
     *   1. Returned to the user ONCE in the response (they must save it).
     *   2. Stored in the organization's JKS KeyStore file (Feature 6 — confidentiality).
     */
    @Transactional
    public CsrResponseDto autogenerate(CsrRequestDto request) throws Exception {
        User currentUser = getCurrentUser();
        Certificate ca = loadAndValidateCa(request.getCaSerialNumber());
        validateValidityPeriod(request.getValidFrom(), request.getValidTo(), ca);

        // Generate RSA 2048-bit key pair
        KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA", BouncyCastleProvider.PROVIDER_NAME);
        kpg.initialize(2048, new SecureRandom());
        KeyPair keyPair = kpg.generateKeyPair();

        X500Name subjectX500Name = buildX500Name(
                request.getCn(), request.getOrganization(),
                request.getOrganizationUnit(), request.getCountry(), request.getEmail());

        PrivateKey caPrivateKey = loadCaPrivateKey(ca);

        X509Certificate x509 = signCertificate(
                subjectX500Name, keyPair.getPublic(), ca, caPrivateKey,
                request.getValidFrom(), request.getValidTo());

        String certificatePem = convertCertToPem(x509);
        String privateKeyPem  = convertPrivateKeyToPem(keyPair.getPrivate());

        Certificate saved = persistCertificate(
                x509,
                request.getCn(), request.getOrganization(),
                request.getOrganizationUnit(), request.getCountry(), request.getEmail(),
                ca, request.getValidFrom(), request.getValidTo(),
                currentUser, certificatePem);

        // Store the private key in the organization's JKS KeyStore (Feature 6).
        // Alias = serial number of the newly issued certificate.
        String orgName  = resolveOrgName(request.getOrganization(), currentUser);
        String filePath = keystorePath(orgName);
        char[] password = keystorePassword();
        keyStoreWriter.write(filePath, saved.getSerialNumber(), keyPair.getPrivate(), password, x509);

        return CsrResponseDto.builder()
                .serialNumber(saved.getSerialNumber())
                .certificatePem(certificatePem)
                .privateKeyPem(privateKeyPem) // Returned ONCE – user must save this
                .message("Sertifikat je uspešno generisan. Sačuvajte privatni ključ – neće biti dostupan ponovo.")
                .build();
    }

    /**
     * Upload CSR: user supplies their own PEM-encoded CSR.
     * Server parses it, signs with chosen CA. User's private key never reaches the server.
     */
    @Transactional
    public CsrResponseDto uploadCsr(CsrUploadDto request) throws Exception {
        User currentUser = getCurrentUser();
        Certificate ca = loadAndValidateCa(request.getCaSerialNumber());

        LocalDateTime validFrom = request.getValidFrom() != null ? request.getValidFrom() : LocalDateTime.now();
        LocalDateTime validTo   = request.getValidTo();
        if (validTo == null) throw new RuntimeException("Datum kraja važenja je obavezan");
        validateValidityPeriod(validFrom, validTo, ca);

        // Parse the PEM CSR
        PKCS10CertificationRequest csr;
        try (PEMParser pemParser = new PEMParser(new StringReader(request.getCsrPem()))) {
            Object obj = pemParser.readObject();
            if (!(obj instanceof PKCS10CertificationRequest)) {
                throw new RuntimeException("Nevažeći CSR format. Očekuje se PEM-enkodovan PKCS#10 CSR.");
            }
            csr = (PKCS10CertificationRequest) obj;
        } catch (IOException e) {
            throw new RuntimeException("Greška pri parsovanju CSR-a: " + e.getMessage(), e);
        }

        PublicKey subjectPublicKey;
        try {
            subjectPublicKey = new org.bouncycastle.openssl.jcajce.JcaPEMKeyConverter()
                    .setProvider(BouncyCastleProvider.PROVIDER_NAME)
                    .getPublicKey(csr.getSubjectPublicKeyInfo());
        } catch (Exception e) {
            throw new RuntimeException("Nije moguće izvući javni ključ iz CSR-a: " + e.getMessage(), e);
        }

        X500Name subjectX500Name = csr.getSubject();
        String cn    = extractRdn(subjectX500Name, BCStyle.CN);
        String o     = extractRdn(subjectX500Name, BCStyle.O);
        String ou    = extractRdn(subjectX500Name, BCStyle.OU);
        String c     = extractRdn(subjectX500Name, BCStyle.C);
        String email = extractRdn(subjectX500Name, BCStyle.E);

        PrivateKey caPrivateKey = loadCaPrivateKey(ca);

        X509Certificate x509 = signCertificate(
                subjectX500Name, subjectPublicKey, ca, caPrivateKey, validFrom, validTo);

        String certificatePem = convertCertToPem(x509);

        persistCertificate(x509, cn, o, ou, c, email,
                ca, validFrom, validTo, currentUser, certificatePem);

        // No private key to store – the user generated it themselves.
        return CsrResponseDto.builder()
                .serialNumber(x509.getSerialNumber().toString(16).toUpperCase())
                .certificatePem(certificatePem)
                .message("CSR je uspešno potpisan. Preuzmite sertifikat u PEM formatu.")
                .build();
    }

    private String extractRdn(X500Name name, org.bouncycastle.asn1.ASN1ObjectIdentifier oid) {
        org.bouncycastle.asn1.x500.RDN[] rdns = name.getRDNs(oid);
        if (rdns == null || rdns.length == 0) return null;
        return rdns[0].getFirst().getValue().toString();
    }
}