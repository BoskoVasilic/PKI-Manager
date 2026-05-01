package com.tim12.pk_infrastructure.model.dtos;

import com.tim12.pk_infrastructure.model.enums.CertificateType;
import lombok.Data;

import java.util.Date;

@Data
public class IssueCertificateRequest {

    private CertificateType type;

    private String issuerSerialNumber;

    private String commonName;
    private String organization;
    private String organizationUnit;
    private String country;
    private String email;

    private Date validFrom;
    private Date validTo;

    private boolean keyCertSign;
    private boolean cRLSign;
    private boolean digitalSignature;
    private boolean keyEncipherment;
    private boolean basicConstraintsCA;
    private boolean serverAuth;
}
