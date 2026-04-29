package com.tim12.pk_infrastructure.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "certificates")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Certificate {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String serialNumber;

    @Column(nullable = false)
    private String commonName;

    private String organization;
    private String organizationalUnit;
    private String country;
    private String email;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private CertificateType type;

    @Column(columnDefinition = "TEXT")
    private String certificatePem;

    @Column(columnDefinition = "TEXT")
    private String encryptedPrivateKey;

    private String issuerSerialNumber;

    private LocalDateTime validFrom;
    private LocalDateTime validTo;

    @Column(nullable = false)
    @Builder.Default
    private boolean revoked = false;

    private String revocationReason;
    private LocalDateTime revokedAt;

    private String ownerOrganization;
    private Long ownerId;
}