package com.tim12.pk_infrastructure.model;

import com.tim12.pk_infrastructure.model.enums.Role;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "users")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(nullable = false)
    private String password;

    @Column(nullable = false)
    private String firstName;

    @Column(nullable = false)
    private String lastName;

    @ManyToOne
    @JoinColumn(name = "organization_id")
    private Organization organization;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Role role;

    @Column(nullable = false)
    @Builder.Default
    private boolean enabled = false;

    // Stored public key (PEM format) used for identity verification during registration/password recovery
    @Column(columnDefinition = "TEXT")
    private String publicKey;

    // Random string encrypted with the user's public key, used during account activation
    @Column(columnDefinition = "TEXT")
    private String activationChallenge;
}