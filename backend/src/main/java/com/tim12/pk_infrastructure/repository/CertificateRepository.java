package com.tim12.pk_infrastructure.repository;

import com.tim12.pk_infrastructure.model.Certificate;
import com.tim12.pk_infrastructure.model.enums.CertificateType;
import com.tim12.pk_infrastructure.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CertificateRepository extends JpaRepository<Certificate, Long> {
    List<Certificate> findByOwnerAndType(User owner, CertificateType type);
    Optional<Certificate> findBySerialNumber(String serialNumber);
    Optional<Certificate> findBySerialNumberAndOwner(String serialNumber, User owner);
    List<Certificate> findByTypeInAndRevokedFalse(List<CertificateType> types);
    List<Certificate> findByIssuingOrg_Name(String orgName);
}