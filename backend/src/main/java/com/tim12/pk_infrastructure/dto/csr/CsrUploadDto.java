package com.tim12.pk_infrastructure.dto.csr;


import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * DTO za upload opciju: korisnik sam generiše ključeve i CSR externally
 * (npr. openssl), uploaduje PEM-enkodovan CSR.
 */
@Data
public class CsrUploadDto {

    // PEM-enkodovan CSR (-----BEGIN CERTIFICATE REQUEST-----)
    @NotBlank(message = "CSR PEM sadržaj je obavezan")
    private String csrPem;

    // Koji CA sertifikat potpisuje
    @NotBlank(message = "Serijski broj CA sertifikata je obavezan")
    private String caSerialNumber;

    // Period važenja (mora biti u okviru CA sertifikata)
    private java.time.LocalDateTime validFrom;
    private java.time.LocalDateTime validTo;
}
