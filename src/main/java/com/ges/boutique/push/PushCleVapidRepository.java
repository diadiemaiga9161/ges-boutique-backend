package com.ges.boutique.push;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PushCleVapidRepository extends JpaRepository<PushCleVapid, Long> {

    Optional<PushCleVapid> findFirstByOrderByIdAsc();
}
