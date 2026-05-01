package com.tim12.pk_infrastructure.dto;

import com.tim12.pk_infrastructure.model.Certificate;
import com.tim12.pk_infrastructure.model.CertificateStatus;
import com.tim12.pk_infrastructure.model.CertificateType;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class CertificateResponse {

    private Long id;
    private String serialNumber;
    private String subjectCN;
    private String subjectO;
    private String subjectOU;
    private String subjectC;
    private String subjectEmail;
    private String issuerCN;
    private String issuerSerialNumber;
    private CertificateType type;
    private CertificateStatus status;
    private LocalDateTime validFrom;
    private LocalDateTime validTo;
    private String revocationReason;

    public static CertificateResponse from(Certificate cert) {
        CertificateResponse r = new CertificateResponse();
        r.setId(cert.getId());
        r.setSerialNumber(cert.getSerialNumber());
        r.setSubjectCN(cert.getSubjectCN());
        r.setSubjectO(cert.getSubjectO());
        r.setSubjectOU(cert.getSubjectOU());
        r.setSubjectC(cert.getSubjectC());
        r.setSubjectEmail(cert.getSubjectEmail());
        r.setIssuerCN(cert.getIssuerCN());
        r.setIssuerSerialNumber(cert.getIssuerSerialNumber());
        r.setType(cert.getType());
        r.setStatus(cert.getStatus());
        r.setValidFrom(cert.getValidFrom());
        r.setValidTo(cert.getValidTo());
        r.setRevocationReason(cert.getRevocationReason());
        return r;
    }
}