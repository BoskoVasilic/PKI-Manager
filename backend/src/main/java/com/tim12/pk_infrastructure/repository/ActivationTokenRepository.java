package com.tim12.pk_infrastructure.repository;

import com.tim12.pk_infrastructure.model.ActivationToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ActivationTokenRepository extends JpaRepository<ActivationToken,Long> {
    Optional<ActivationToken> findByToken(String token);
    Optional<ActivationToken> findByUserIdAndTokenTypeAndUsedFalse(Long userId, String tokenType);
}
