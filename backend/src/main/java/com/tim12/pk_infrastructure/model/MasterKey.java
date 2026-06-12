package com.tim12.pk_infrastructure.model;

import com.tim12.pk_infrastructure.model.enums.MasterKeyStatus;
import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "master_keys")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MasterKey {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private Integer version;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String keyBase64; // the raw AES key, stored encrypted by the env-level secret (or in a vault)

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private MasterKeyStatus status; // ACTIVE, PENDING, RETIRED

    private LocalDateTime createdAt;
    private LocalDateTime activatedAt;
    private LocalDateTime retiredAt;
}