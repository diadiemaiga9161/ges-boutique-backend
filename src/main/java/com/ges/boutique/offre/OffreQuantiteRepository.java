package com.ges.boutique.offre;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface OffreQuantiteRepository extends JpaRepository<OffreQuantite, Long> {
    List<OffreQuantite> findByProduitIdAndActiveTrue(Long produitId);
    List<OffreQuantite> findByActiveTrue();
    List<OffreQuantite> findAllByOrderByProduitIdAscQuantiteAsc();
}
