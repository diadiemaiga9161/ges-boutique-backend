package com.ges.boutique.devis;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface DevisRepository extends JpaRepository<Devis, Long> {
    List<Devis> findAllByOrderByDateCreationDesc();
    java.util.Optional<Devis> findTopByNumeroStartingWithOrderByNumeroDesc(String prefixe);
}
