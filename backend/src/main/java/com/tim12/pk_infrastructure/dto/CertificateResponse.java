package com.tim12.pk_infrastructure.dto;

import com.tim12.pk_infrastructure.model.Certificate;
import com.tim12.pk_infrastructure.model.CertificateType;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class CertificateResponse {

    private Long id;
    private String serialNumber;
    private String commonName;
    private String organization;
    private String organizationalUnit;
    private String country;
    private String email;
    private CertificateType type;
    private String issuerSerialNumber;
    private LocalDateTime validFrom;
    private LocalDateTime validTo;
    private boolean revoked;
    private String revocationReason;

    public static CertificateResponse from(Certificate cert) {
        CertificateResponse r = new CertificateResponse();
        r.setId(cert.getId());
        r.setSerialNumber(cert.getSerialNumber());
        r.setCommonName(cert.getCommonName());
        r.setOrganization(cert.getOrganization());
        r.setOrganizationalUnit(cert.getOrganizationalUnit());
        r.setCountry(cert.getCountry());
        r.setEmail(cert.getEmail());
        r.setType(cert.getType());
        r.setIssuerSerialNumber(cert.getIssuerSerialNumber());
        r.setValidFrom(cert.getValidFrom());
        r.setValidTo(cert.getValidTo());
        r.setRevoked(cert.isRevoked());
        r.setRevocationReason(cert.getRevocationReason());
        return r;
    }
}