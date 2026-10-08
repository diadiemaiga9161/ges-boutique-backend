package com.ges.boutique.inventaire.comptage;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ComptageInventaireRepository extends JpaRepository<ComptageInventaire, Long> {
    Optional<ComptageInventaire> findFirstByStatut(StatutComptage statut);
    List<ComptageInventaire> findTop20ByOrderByDateDebutDesc();
}
