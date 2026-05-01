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
import com.tim12.pk_infrastructure.repository.CertificateRepository;
import com.tim12.pk_infrastructure.repository.OrganizatioRepository;
import com.tim12.pk_infrastructure.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x500.X500NameBuilder;
import org.bouncycastle.asn1.x500.style.BCStyle;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.security.*;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

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

    private User getCurrentUser() {
        String email = SecurityContextHolder.getContext()
                .getAuthentication().getName();
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("Authenticated user not found"));
    }

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