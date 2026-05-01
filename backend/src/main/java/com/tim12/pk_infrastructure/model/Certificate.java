package com.tim12.pk_infrastructure.model;

import com.tim12.pk_infrastructure.model.enums.CertificateStatus;
import com.tim12.pk_infrastructure.model.enums.CertificateType;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.Date;

@Entity
@Table(name = "certificates")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Certificate {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false)
    private String serialNumber;

    @Column(nullable = false)
    private String alias;

    @Column(nullable = false)
    private String subjectCN;
    private String subjectO;
    private String subjectOU;
    private String subjectC;
    private String subjectEmail;

    private String issuerCN;
    private String issuerSerialNumber;

    private Date validFrom;
    private Date validTo;

    @Enumerated(EnumType.STRING)
    private CertificateType type;

    private boolean revoked;

    @Column(columnDefinition = "TEXT")
    private String certificatePem;

    @Column(columnDefinition = "TEXT")
    private String encryptedPrivateKey;

    private String revocationReason;
    private Date revokedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "owner_id")
    private User owner;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "organization_id", referencedColumnName = "id")
    private Organization issuingOrg;
}