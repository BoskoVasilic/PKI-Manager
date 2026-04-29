package com.tim12.pk_infrastructure.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "certificates")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Certificate {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false)
    private String serialNumber;

    // Subject polja
    private String subjectCN;
    private String subjectO;
    private String subjectOU;
    private String subjectC;
    private String subjectEmail;

    // Issuer
    private String issuerCN;
    private String issuerSerialNumber;

    private LocalDateTime validFrom;
    private LocalDateTime validTo;

    @Enumerated(EnumType.STRING)
    private CertificateType type;

    @Enumerated(EnumType.STRING)
    private CertificateStatus status;

    @Column(columnDefinition = "TEXT")
    private String certificatePem;

    // Za feature #7 (tvoj kolega) - encrypted private key
    @Column(columnDefinition = "TEXT")
    private String encryptedPrivateKey;

    // Razlog i datum povlacenja
    private String revocationReason;
    private LocalDateTime revokedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "owner_id")
    private User owner;
}