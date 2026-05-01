package com.tim12.pk_infrastructure.repository;

import com.tim12.pk_infrastructure.model.Organization;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface OrganizatioRepository extends JpaRepository<Organization, Long> {

    Optional<Organization> findByName(String name);
}
