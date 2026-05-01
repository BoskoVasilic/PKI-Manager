package com.tim12.pk_infrastructure.model.dtos.csr;


import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * DTO za autogenerate opciju: PKI sistem generiše par ključeva,
 * potpisuje sertifikat izabranim CA, privatni ključ se NE čuva na serveru.
 */
@Data
public class CsrRequestDto {

    // X500Name polja
    @NotBlank(message = "Common Name (CN) je obavezan")
    private String cn;

    private String organization;     // O
    private String organizationUnit; // OU
    private String country;          // C (ISO 3166 dvoslovna oznaka, npr. RS)
    private String email;            // E

    // Koji CA sertifikat potpisuje
    @NotBlank(message = "Serijski broj CA sertifikata je obavezan")
    private String caSerialNumber;

    // Period važenja
    @NotNull(message = "Datum početka važenja je obavezan")
    private LocalDateTime validFrom;

    @NotNull(message = "Datum kraja važenja je obavezan")
    private LocalDateTime validTo;
}