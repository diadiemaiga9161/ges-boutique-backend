package com.ges.boutique.inventaire.comptage;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface LigneComptageRepository extends JpaRepository<LigneComptage, Long> {
    List<LigneComptage> findByComptageIdOrderByDateComptageDesc(Long comptageId);
    Optional<LigneComptage> findByComptageIdAndProduitId(Long comptageId, Long produitId);
    long countByComptageId(Long comptageId);
}
