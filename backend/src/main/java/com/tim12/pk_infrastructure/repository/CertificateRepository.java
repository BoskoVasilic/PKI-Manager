package com.tim12.pk_infrastructure.repository;

import com.tim12.pk_infrastructure.model.Certificate;
import com.tim12.pk_infrastructure.model.CertificateType;
import org.springframework.data.jpa.repository.JpaRepository;
import com.tim12.pk_infrastructure.model.User;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CertificateRepository extends JpaRepository<Certificate, Long> {

    Optional<Certificate> findBySerialNumber(String serialNumber);

    // CA user: only see certs in their org
    List<Certificate> findByOwnerOrganization(String organization);

    // Regular user: only their own certs
    List<Certificate> findByOwnerId(Long ownerId);

    // Find available CA certs for a given org (not revoked, right type)
    List<Certificate> findByOwnerOrganizationAndTypeInAndRevokedFalse(
            String organization, List<CertificateType> types
    );

    List<Certificate> findByOwnerAndType(User owner, CertificateType type);
    Optional<Certificate> findBySerialNumberAndOwner(String serialNumber, User owner);
}

