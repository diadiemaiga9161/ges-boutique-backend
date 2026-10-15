package com.ges.boutique.offre;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface OffreQuantiteRepository extends JpaRepository<OffreQuantite, Long> {
    List<OffreQuantite> findByProduitIdAndActiveTrue(Long produitId);
    List<OffreQuantite> findByActiveTrue();
    List<OffreQuantite> findAllByOrderByProduitIdAscQuantiteAsc();

    /** Offres d'un produit supprimé : supprimées avec lui (sinon elles restent orphelines). */
    void deleteByProduitId(Long produitId);
}
