package com.tim12.pk_infrastructure.service;

import com.tim12.pk_infrastructure.keystores.KeyStoreReader;
import com.tim12.pk_infrastructure.model.Certificate;
import com.tim12.pk_infrastructure.model.Organization;
import com.tim12.pk_infrastructure.model.User;
import com.tim12.pk_infrastructure.model.enums.CertificateType;
import com.tim12.pk_infrastructure.repository.CertificateRepository;
import com.tim12.pk_infrastructure.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.bouncycastle.openssl.jcajce.JcaPEMWriter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import java.io.ByteArrayOutputStream;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.cert.X509Certificate;

import java.io.File;
import java.io.StringWriter;
import java.util.Base64;
import java.util.List;

@Service
@RequiredArgsConstructor
public class CertificateDownloadService {

    private final CertificateRepository  certificateRepository;
    private final UserRepository         userRepository;
    private final KeyStoreReader         keyStoreReader;
    private final KeyEncryptionService   keyEncryptionService;

    @Value("${pki.keystore.dir}")
    private String keystoreDir;


    public String downloadAsPem(String serialNumber) {
        Certificate cert = loadAndAuthorize(serialNumber);
        return resolvePem(cert);
    }

    public byte[] downloadAsCer(String serialNumber) {
        Certificate cert = loadAndAuthorize(serialNumber);
        String pem = resolvePem(cert);
        return pemToDer(pem);
    }

    public List<Certificate> getDownloadableCertificates() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        User currentUser = getCurrentUser();

        if (hasRole(auth, "ROLE_ADMIN")) {
            return certificateRepository.findAll();
        }

        if (hasRole(auth, "ROLE_CA_USER")) {
            Organization org = currentUser.getOrganization();
            if (org == null) throw new SecurityException("CA user has no organization assigned.");
            return certificateRepository.findByIssuingOrg_Name(org.getName());
        }

        return certificateRepository.findByOwnerAndType(currentUser, CertificateType.END_ENTITY);
    }

    private Certificate loadAndAuthorize(String serialNumber) {
        var auth = SecurityContextHolder.getContext().getAuthentication();

        boolean isAdmin  = hasRole(auth, "ROLE_ADMIN");
        boolean isCaUser = hasRole(auth, "ROLE_CA_USER");

        Certificate cert = certificateRepository
                .findBySerialNumber(serialNumber)
                .orElseThrow(() -> new RuntimeException("Certificate not found: " + serialNumber));

        if (isAdmin) {
            return cert; // admin sees everything
        }

        User currentUser = getCurrentUser();

        if (isCaUser) {
            // CA user may download any cert belonging to their organization
            Organization userOrg = currentUser.getOrganization();
            if (userOrg == null) {
                throw new SecurityException("CA user has no organization assigned.");
            }
            Organization certOrg = cert.getIssuingOrg();
            if (certOrg == null || !certOrg.getId().equals(userOrg.getId())) {
                throw new SecurityException("Access denied: certificate does not belong to your organization.");
            }
            return cert;
        }

        // Regular user: only their own END_ENTITY certificates
        if (cert.getType() != CertificateType.END_ENTITY) {
            throw new SecurityException("Access denied: you can only download end-entity certificates.");
        }
        if (cert.getOwner() == null || !cert.getOwner().getId().equals(currentUser.getId())) {
            throw new SecurityException("Access denied: certificate does not belong to you.");
        }
        return cert;
    }

    private String resolvePem(Certificate cert) {
        // Fast path – PEM already stored in the database
        if (cert.getCertificatePem() != null && !cert.getCertificatePem().isBlank()) {
            return cert.getCertificatePem();
        }

        // Fallback – reconstruct PEM from the JKS keystore
        Organization org = cert.getIssuingOrg();
        if (org == null) {
            throw new RuntimeException(
                    "Cannot retrieve certificate PEM: no organization linked to certificate '"
                            + cert.getSerialNumber() + "'.");
        }

        String  ksPath = resolveKeyStorePath(org);
        char[]  ksPass = resolveOrgKeyStorePassword(org);

        X509Certificate x509 = keyStoreReader.readX509Certificate(ksPath, cert.getAlias(), ksPass);
        return x509ToPem(x509);
    }

    private String x509ToPem(X509Certificate cert) {
        try {
            StringWriter sw = new StringWriter();
            try (JcaPEMWriter writer = new JcaPEMWriter(sw)) {
                writer.writeObject(cert);
            }
            return sw.toString();
        } catch (Exception e) {
            throw new RuntimeException("Failed to serialize certificate to PEM: " + e.getMessage(), e);
        }
    }

    private byte[] pemToDer(String pem) {
        if (pem == null || pem.isBlank()) {
            throw new RuntimeException("Cannot convert to DER: PEM content is null or empty.");
        }
        String base64 = pem
                .replace("-----BEGIN CERTIFICATE-----", "")
                .replace("-----END CERTIFICATE-----", "")
                .replaceAll("[\\r\\n\\s]+", "")  // handles \r\n, \n, spaces
                .trim();

        if (base64.isEmpty()) {
            throw new RuntimeException("Cannot convert to DER: PEM base64 body is empty after stripping headers.");
        }

        try {
            return Base64.getDecoder().decode(base64);
        } catch (IllegalArgumentException e) {
            throw new RuntimeException("Failed to decode PEM to DER: " + e.getMessage(), e);
        }
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

    private User getCurrentUser() {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("Authenticated user not found: " + email));
    }

    private boolean hasRole(
            org.springframework.security.core.Authentication auth,
            String role) {
        return auth.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals(role));
    }

    public byte[] downloadAsP12(String serialNumber, char[] exportPassword) {
        Certificate cert = loadAndAuthorize(serialNumber);
        return buildKeyStore("PKCS12", serialNumber, cert, exportPassword);
    }

    public byte[] downloadAsJks(String serialNumber, char[] exportPassword) {
        Certificate cert = loadAndAuthorize(serialNumber);
        return buildKeyStore("JKS", serialNumber, cert, exportPassword);
    }

    private void checkPrivateKeyAvailable(Certificate cert, String ksPath, char[] ksPass) {
        if (!cert.isPrivateKeyAvailable()) {
            throw new IllegalStateException(
                    "Private key is not available for certificate '" + cert.getSerialNumber() + "'. " +
                            "It was either never generated (CSR upload) or has already been downloaded once. " +
                            "Download in .pem or .cer format instead."
            );
        }

        // Defensive check: confirm the key actually exists in the keystore
        PrivateKey pk;
        try {
            pk = keyStoreReader.readPrivateKey(ksPath, cert.getAlias(), ksPass, ksPass);
        } catch (Exception e) {
            throw new RuntimeException(
                    "Failed to read private key from keystore for certificate '" + cert.getSerialNumber() + "'.", e);
        }

        if (pk == null) {
            throw new RuntimeException(
                    "Inconsistent state: certificate '" + cert.getSerialNumber() +
                            "' is marked privateKeyAvailable=true but no key entry exists in the keystore.");
        }
    }

    private byte[] buildKeyStore(String ksType, String serialNumber,
                                 Certificate cert, char[] exportPassword) {
        Organization org = cert.getIssuingOrg();
        if (org == null) {
            throw new RuntimeException("No organization linked to certificate: " + serialNumber);
        }

        String ksPath = resolveKeyStorePath(org);
        char[] ksPass = resolveOrgKeyStorePassword(org);

        checkPrivateKeyAvailable(cert, ksPath, ksPass);

        X509Certificate x509 = keyStoreReader.readX509Certificate(ksPath, cert.getAlias(), ksPass);
        PrivateKey privateKey = keyStoreReader.readPrivateKey(ksPath, cert.getAlias(), ksPass, ksPass);

        try {
            KeyStore exportKs = KeyStore.getInstance(ksType);
            exportKs.load(null, exportPassword);
            exportKs.setKeyEntry(
                    cert.getAlias(),
                    privateKey,
                    exportPassword,
                    new java.security.cert.Certificate[]{x509}
            );

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            exportKs.store(baos, exportPassword);
            return baos.toByteArray();

        } catch (Exception e) {
            throw new RuntimeException("Failed to build export keystore: " + e.getMessage(), e);
        }
    }
}