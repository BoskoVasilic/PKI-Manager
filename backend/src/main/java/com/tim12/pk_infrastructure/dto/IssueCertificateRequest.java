package com.tim12.pk_infrastructure.dto;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
public class IssueCertificateRequest {

    // X500Name fields
    private String commonName;
    private String organization;
    private String organizationalUnit;
    private String country;
    private String email;

    // Validity
    private LocalDateTime validFrom;
    private LocalDateTime validTo;

    // Which CA cert signs this (serial number)
    private String issuerSerialNumber;

    // Type: INTERMEDIATE or END_ENTITY
    private String type;

    // Extensions e.g. "KEY_CERT_SIGN", "DIGITAL_SIGNATURE"
    private List<String> keyUsages;

    // true = adds BasicConstraints CA:true
    private boolean isCa;

    // -1 = no limit
    private int pathLengthConstraint = -1;
}