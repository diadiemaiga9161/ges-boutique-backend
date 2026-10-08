package com.ges.boutique.produit;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface ProduitRepository extends JpaRepository<Produit, Long> {

    List<Produit> findByCategorieId(Long categorieId);

    /** Sans passer par l'entité : ne touche pas au verrou optimiste du stock (@Version).
     *  flushAutomatically : écrit d'abord la photo modifiée/retirée (ProduitImageService),
     *  sinon le clear qui suit l'effaçait avant son écriture (le remplacement restait sans effet). */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("UPDATE Produit p SET p.imageVersion = :version WHERE p.id = :id")
    int definirImageVersion(@Param("id") Long id, @Param("version") Long version);

    List<Produit> findByFournisseurId(Long fournisseurId);

    List<Produit> findByNomContainingIgnoreCase(String nom);

    List<Produit> findByCodeBarre(String codeBarre);

    @Query("SELECT p FROM Produit p WHERE p.quantite <= p.seuilAlerte")
    List<Produit> trouverProduitsStockFaible();

    @Query("SELECT p FROM Produit p WHERE p.quantite = 0")
    List<Produit> trouverProduitsEnRupture();

    @Query("SELECT COUNT(p) FROM Produit p WHERE p.quantite <= p.seuilAlerte")
    Long compterProduitsStockFaible();

    @Query("SELECT SUM(p.prixAchat * p.quantite) FROM Produit p")
    Double getValeurTotaleStock();

    boolean existsByNomAndCategorieId(String nom, Long categorieId);

    List<Produit> findByDatePeremptionBefore(LocalDate date);

    List<Produit> findByDatePeremptionBetween(LocalDate startDate, LocalDate endDate);

    @Query("SELECT p FROM Produit p WHERE p.datePeremption <= :dateAlerte AND p.datePeremption >= CURRENT_DATE")
    List<Produit> trouverProduitsProchePeremption(@Param("dateAlerte") LocalDate dateAlerte);

    @Query("SELECT p FROM Produit p WHERE p.bio = true")
    List<Produit> trouverProduitsBio();

    List<Produit> findByOrigine(String origine);

    @Query("SELECT p FROM Produit p WHERE p.dateCreation >= :dateDebut")
    List<Produit> trouverProduitsRecents(@Param("dateDebut") LocalDate dateDebut);

    @Query("SELECT COUNT(l) FROM LigneVente l WHERE l.produit.id = :produitId")
    long countLignesVenteByProduitId(@Param("produitId") Long produitId);

    long countByFournisseurId(Long fournisseurId);

    List<Produit> findByNomIgnoreCase(String nom);

}
