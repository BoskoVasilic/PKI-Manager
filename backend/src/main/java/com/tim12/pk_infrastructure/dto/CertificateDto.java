package com.tim12.pk_infrastructure.dto;

import com.tim12.pk_infrastructure.model.enums.CertificateStatus;
import com.tim12.pk_infrastructure.model.enums.CertificateType;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.Date;

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
    private Date validFrom;
    private Date validTo;
    private CertificateType type;
    private boolean revoked;
    private String revocationReason;
}