package com.ges.boutique.produit;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ProduitImageRepository extends JpaRepository<ProduitImage, Long> {
    Optional<ProduitImage> findByProduitId(Long produitId);
    void deleteByProduitId(Long produitId);
}
