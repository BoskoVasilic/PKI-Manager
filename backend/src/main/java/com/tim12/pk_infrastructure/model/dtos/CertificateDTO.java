package com.tim12.pk_infrastructure.model.dtos;

import com.tim12.pk_infrastructure.model.enums.CertificateType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class CertificateDTO {
    private long id;
    private String serialNumber;
    private String alias;
    private String commonName;
    private String organization;
    private String organizationUnit;
    private String country;
    private String email;
    private String validFrom;
    private String validTo;
    private CertificateType type;
    private String issuerSerialNumber;
    private boolean revoked;
    private Date revokedAt;
    private String revocationReason;
    private boolean privateKeyAvailable;
}
