package com.tim12.pk_infrastructure.dto;

import com.tim12.pk_infrastructure.model.CertificateStatus;
import com.tim12.pk_infrastructure.model.CertificateType;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class CertificateDto {
    private String serialNumber;
    private String subjectCN;
    private String subjectO;
    private String subjectOU;
    private String subjectC;
    private String subjectEmail;
    private String issuerCN;
    private LocalDateTime validFrom;
    private LocalDateTime validTo;
    private CertificateType type;
    private CertificateStatus status;
    private String revocationReason;
}