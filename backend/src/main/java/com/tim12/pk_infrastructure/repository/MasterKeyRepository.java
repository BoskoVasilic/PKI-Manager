package com.tim12.pk_infrastructure.repository;

import com.tim12.pk_infrastructure.model.MasterKey;
import com.tim12.pk_infrastructure.model.enums.MasterKeyStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.Optional;

@Repository
public interface MasterKeyRepository extends JpaRepository<MasterKey, Long> {

    Optional<MasterKey> findByStatus(MasterKeyStatus status);
}