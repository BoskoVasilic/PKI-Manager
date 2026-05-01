package com.tim12.pk_infrastructure.repository;

import com.tim12.pk_infrastructure.model.Certificate;
import com.tim12.pk_infrastructure.model.enums.CertificateType;
import com.tim12.pk_infrastructure.model.User;
import com.tim12.pk_infrastructure.model.enums.CertificateStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import com.tim12.pk_infrastructure.model.User;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CertificateRepository extends JpaRepository<Certificate, Long> {

    Optional<Certificate> findBySerialNumber(String serialNumber);

    // CA user: only see certs in their org
    List<Certificate> findByOwner(User owner);

    List<Certificate> findByOwnerAndTypeInAndStatus(
            User owner, List<CertificateType> types, CertificateStatus status);

    // Regular user: only their own certs
    List<Certificate> findByOwnerId(Long ownerId);

    List<Certificate> findByOwnerAndType(User owner, CertificateType type);
    Optional<Certificate> findBySerialNumberAndOwner(String serialNumber, User owner);
    List<Certificate> findByTypeInAndRevokedFalse(List<CertificateType> types);
    List<Certificate> findByIssuingOrg_Name(String orgName);
}
