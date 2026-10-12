package com.ges.boutique.push;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

public interface PushAbonnementRepository extends JpaRepository<PushAbonnement, Long> {

    Optional<PushAbonnement> findByEndpoint(String endpoint);

    @Transactional
    long deleteByEndpoint(String endpoint);
}
