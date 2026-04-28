package com.tim12.pk_infrastructure.repository;

import com.tim12.pk_infrastructure.model.ActivationToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ActivationTokenRepository extends JpaRepository<ActivationToken,Long> {
}
