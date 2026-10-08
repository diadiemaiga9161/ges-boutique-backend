package com.ges.boutique.vente;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface LigneVenteRepository extends JpaRepository<LigneVente, Long> {

    @Modifying
    @Transactional
    @Query("DELETE FROM LigneVente l WHERE l.vente.id = :venteId")
    void deleteAllByVenteId(@Param("venteId") Long venteId);

    // Quantités et CA vendus par produit sur une période (ventes non annulées) :
    // [produitId, produitNom, modeMesure, quantité, CA (sous-totaux), bénéfice estimé, catégorie,
    // CA (quantité × prix)]. Prix = prix après remise, sinon prix unitaire ; bénéfice estimé =
    // (prix − prix d'achat actuel du produit) × quantité, formule des rapports des applis.
    @Query("SELECT p.id, p.nom, p.modeMesure, COALESCE(SUM(l.quantite), 0), COALESCE(SUM(l.sousTotal), 0), " +
           "COALESCE(SUM(((CASE WHEN l.prixApresRemise IS NULL OR l.prixApresRemise = 0 THEN COALESCE(l.prixUnitaire, 0) ELSE l.prixApresRemise END) " +
           "- COALESCE(p.prixAchat, 0)) * l.quantite), 0), " +
           "c.nom, " +
           "COALESCE(SUM((CASE WHEN l.prixApresRemise IS NULL OR l.prixApresRemise = 0 THEN COALESCE(l.prixUnitaire, 0) ELSE l.prixApresRemise END) * l.quantite), 0) " +
           "FROM LigneVente l JOIN l.vente v LEFT JOIN l.produit p LEFT JOIN p.categorie c " +
           "WHERE v.dateVente BETWEEN :debut AND :fin AND (v.annulee IS NULL OR v.annulee = false) " +
           "GROUP BY p.id, p.nom, p.modeMesure, c.nom")
    List<Object[]> totauxParProduit(@Param("debut") LocalDateTime debut, @Param("fin") LocalDateTime fin);

    // Bénéfice des lignes des ventes sans bénéfice enregistré (formule du site : prix d'achat
    // actuel du produit, sinon celui de la ligne).
    @Query("SELECT COALESCE(SUM(((CASE WHEN l.prixApresRemise IS NULL OR l.prixApresRemise = 0 THEN COALESCE(l.prixUnitaire, 0) ELSE l.prixApresRemise END) " +
           "- (CASE WHEN p.prixAchat IS NULL OR p.prixAchat = 0 THEN COALESCE(l.prixAchat, 0) ELSE p.prixAchat END)) * l.quantite), 0) " +
           "FROM LigneVente l JOIN l.vente v LEFT JOIN l.produit p " +
           "WHERE v.dateVente BETWEEN :debut AND :fin AND (v.annulee IS NULL OR v.annulee = false) " +
           "AND (v.beneficeTotal IS NULL OR v.beneficeTotal = 0)")
    Double beneficeLignesSansBeneficeVente(@Param("debut") LocalDateTime debut, @Param("fin") LocalDateTime fin);
}