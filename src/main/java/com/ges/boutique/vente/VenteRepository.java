package com.ges.boutique.vente;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface VenteRepository extends JpaRepository<Vente, Long> {

    /** Vente verrouillée jusqu'à la fin de la transaction : deux annulations simultanées de la même
     *  vente (double clic, demande renvoyée par le réseau) passent l'une après l'autre. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT v FROM Vente v WHERE v.id = :id")
    Optional<Vente> findByIdPourMiseAJour(@Param("id") Long id);

    @Query("SELECT v FROM Vente v WHERE v.dateVente BETWEEN :debut AND :fin ORDER BY v.dateVente DESC")
    List<Vente> findByDateRange(@Param("debut") LocalDateTime debut, @Param("fin") LocalDateTime fin);

    // Totaux d'une période calculés par la base (sans charger les ventes) : [nombre, CA, remises].
    @Query("SELECT COUNT(v), COALESCE(SUM(v.montantTotal), 0), COALESCE(SUM(v.montantRemiseTotal), 0) FROM Vente v " +
           "WHERE v.dateVente BETWEEN :debut AND :fin AND (v.annulee IS NULL OR v.annulee = false)")
    List<Object[]> totauxPeriode(@Param("debut") LocalDateTime debut, @Param("fin") LocalDateTime fin);

    // Répartition par mode de paiement sur une période : [modePaiement, CA, nombre de ventes].
    @Query("SELECT v.modePaiement, COALESCE(SUM(v.montantTotal), 0), COUNT(v) FROM Vente v " +
           "WHERE v.dateVente BETWEEN :debut AND :fin AND (v.annulee IS NULL OR v.annulee = false) " +
           "GROUP BY v.modePaiement")
    List<Object[]> totauxParModePaiement(@Param("debut") LocalDateTime debut, @Param("fin") LocalDateTime fin);

    // Par vendeur sur une période : [vendeurId, nomComplet, nombre de ventes, CA].
    @Query("SELECT u.id, u.nomComplet, COUNT(v), COALESCE(SUM(v.montantTotal), 0) FROM Vente v LEFT JOIN v.vendeur u " +
           "WHERE v.dateVente BETWEEN :debut AND :fin AND (v.annulee IS NULL OR v.annulee = false) " +
           "GROUP BY u.id, u.nomComplet")
    List<Object[]> totauxParVendeur(@Param("debut") LocalDateTime debut, @Param("fin") LocalDateTime fin);

    // Somme des bénéfices enregistrés sur les ventes (celles qui en ont un).
    @Query("SELECT COALESCE(SUM(v.beneficeTotal), 0) FROM Vente v " +
           "WHERE v.dateVente BETWEEN :debut AND :fin AND (v.annulee IS NULL OR v.annulee = false) " +
           "AND v.beneficeTotal IS NOT NULL AND v.beneficeTotal <> 0")
    Double sommeBeneficesEnregistres(@Param("debut") LocalDateTime debut, @Param("fin") LocalDateTime fin);

    // CA par jour sur une période : [date (yyyy-MM-dd), CA].
    @Query(value = "SELECT DATE_FORMAT(v.date_vente, '%Y-%m-%d') AS jour, COALESCE(SUM(v.montant_total), 0) AS ca FROM ventes v " +
                   "WHERE v.date_vente BETWEEN :debut AND :fin AND (v.annulee IS NULL OR v.annulee = 0) " +
                   "GROUP BY DATE_FORMAT(v.date_vente, '%Y-%m-%d') ORDER BY jour",
           nativeQuery = true)
    List<Object[]> chiffreAffaireParJour(@Param("debut") LocalDateTime debut, @Param("fin") LocalDateTime fin);

    // Ventes « client divers » d'une période (non annulées), mêmes critères que l'écran Clients.
    @Query("SELECT v FROM Vente v WHERE v.dateVente BETWEEN :debut AND :fin AND (v.annulee IS NULL OR v.annulee = false) " +
           "AND (v.clientDivers = true OR LOWER(v.clientNom) LIKE '%divers%') ORDER BY v.dateVente DESC")
    List<Vente> findClientsDiversParPeriode(@Param("debut") LocalDateTime debut, @Param("fin") LocalDateTime fin);

    @Query("SELECT v FROM Vente v WHERE v.dateVente >= :debut AND v.dateVente <= :fin ORDER BY v.dateVente DESC")
    List<Vente> findTodayVentes(@Param("debut") LocalDateTime debut, @Param("fin") LocalDateTime fin);

    @Query("SELECT v FROM Vente v WHERE v.vendeur.id = :vendeurId ORDER BY v.dateVente DESC")
    List<Vente> findByVendeurId(@Param("vendeurId") Long vendeurId);

    // Traçabilité comptable : ventes annulées dans la période, pour afficher le bénéfice
    // qu'elles représentaient et qui/pourquoi elles ont été annulées (page Bénéfices).
    @Query("SELECT v FROM Vente v WHERE v.annulee = true AND v.dateAnnulation >= :debut AND v.dateAnnulation <= :fin ORDER BY v.dateAnnulation DESC")
    List<Vente> findVentesAnnuleesByDateAnnulationRange(@Param("debut") LocalDateTime debut, @Param("fin") LocalDateTime fin);

    @Query("SELECT v FROM Vente v WHERE v.estCredit = true AND (v.annulee IS NULL OR v.annulee = false) ORDER BY v.dateVente DESC")
    List<Vente> findAllCredits();

    // Filtre sur montantRestant (source de vérité) plutôt que sur le flag creditRegle :
    // creditRegle est un simple cache de "montantRestant <= 0" qui peut rester bloqué à
    // l'ancienne valeur après un retour ou une modification de vente qui repasse le montant
    // restant au-dessus de 0 (cf. session du 2026-08-02 — vu en prod sur 2 boutiques).
    @Query("SELECT v FROM Vente v WHERE v.estCredit = true AND (v.montantRestant IS NULL OR v.montantRestant > 0.01) AND (v.annulee IS NULL OR v.annulee = false) ORDER BY v.dateEcheance ASC")
    List<Vente> findCreditsNonRegles();

    @Query("SELECT v FROM Vente v WHERE v.estCredit = true AND (v.montantRestant IS NULL OR v.montantRestant > 0.01) AND (v.annulee IS NULL OR v.annulee = false) AND v.dateEcheance < CURRENT_DATE ORDER BY v.dateEcheance ASC")
    List<Vente> findCreditsEnRetard();

    @Query("SELECT v FROM Vente v WHERE v.estCredit = true AND v.montantRestant <= 0.01 AND (v.annulee IS NULL OR v.annulee = false) ORDER BY v.dateReglement DESC")
    List<Vente> findCreditsRegles();

    @Query("SELECT v FROM Vente v WHERE v.estCredit = true AND v.clientNom LIKE %:clientNom% AND (v.annulee IS NULL OR v.annulee = false) ORDER BY v.dateVente DESC")
    List<Vente> findCreditsByClientNom(@Param("clientNom") String clientNom);

    @Query("SELECT v FROM Vente v WHERE v.estCredit = true AND v.creditRegle = true AND (v.annulee IS NULL OR v.annulee = false) AND v.dateReglement BETWEEN :debut AND :fin ORDER BY v.dateReglement DESC")
    List<Vente> findCreditsReglesByDateRange(@Param("debut") LocalDate debut, @Param("fin") LocalDate fin);

    @Query("SELECT v FROM Vente v WHERE v.annulee = true ORDER BY v.dateAnnulation DESC")
    List<Vente> findAllVentesAnnulees();

    /**
     * Crédits actifs (non annulés) triés par date de vente décroissante.
     * Endpoint dédié pour éviter le chargement de toutes les ventes côté client.
     */
    @Query("SELECT v FROM Vente v WHERE v.estCredit = true AND (v.annulee IS NULL OR v.annulee = false) ORDER BY v.dateVente DESC")
    List<Vente> findCreditsActifs();

    @Query("SELECT v FROM Vente v WHERE (v.annulee IS NULL OR v.annulee = false) ORDER BY v.dateVente DESC")
    List<Vente> findAllNonAnnulees();

    // Export de données : contrairement à findAllNonAnnulees(), inclut aussi les ventes
    // annulées. Même tri (dateVente DESC) pour garder une cohérence avec le comportement
    // par défaut de GET /api/ventes.
    @Query("SELECT v FROM Vente v ORDER BY v.dateVente DESC")
    List<Vente> findAllOrderByDateVenteDesc();

    // BUG FIX (audit comptable) : ces SUM(v.montantTotal) comptaient la marchandise
    // retournée comme si elle était toujours vendue (le retour ne mettait à jour que le
    // stock/la caisse, jamais le CA). On soustrait désormais montantMarchandiseRetournee
    // (valeur totale retournée, toute vente confondue) pour un CA net des retours.
    @Query("SELECT COALESCE(SUM(v.montantTotal - COALESCE(v.montantMarchandiseRetournee, 0)), 0) FROM Vente v WHERE v.dateVente >= :debut AND v.dateVente <= :fin AND (v.estCredit IS NULL OR v.estCredit = false) AND (v.annulee IS NULL OR v.annulee = false)")
    Double getChiffreAffaireJournalier(@Param("debut") LocalDateTime debut, @Param("fin") LocalDateTime fin);

    @Query("SELECT COALESCE(SUM(v.montantTotal - COALESCE(v.montantMarchandiseRetournee, 0)), 0) FROM Vente v WHERE v.dateVente >= :debut AND v.dateVente <= :fin AND (v.estCredit IS NULL OR v.estCredit = false) AND (v.annulee IS NULL OR v.annulee = false)")
    Double getChiffreAffaireHebdomadaire(@Param("debut") LocalDateTime debut, @Param("fin") LocalDateTime fin);

    @Query("SELECT COALESCE(SUM(v.montantTotal - COALESCE(v.montantMarchandiseRetournee, 0)), 0) FROM Vente v WHERE v.dateVente >= :debut AND v.dateVente <= :fin AND (v.estCredit IS NULL OR v.estCredit = false) AND (v.annulee IS NULL OR v.annulee = false)")
    Double getChiffreAffaireMensuel(@Param("debut") LocalDateTime debut, @Param("fin") LocalDateTime fin);

    @Query("SELECT COALESCE(SUM(v.montantRestant), 0) FROM Vente v WHERE v.estCredit = true AND (v.creditRegle IS NULL OR v.creditRegle = false) AND (v.annulee IS NULL OR v.annulee = false)")
    Double getTotalCreditsNonRegles();

    @Query("SELECT COALESCE(SUM(v.montantVerse), 0) FROM Vente v WHERE v.estCredit = true AND v.creditRegle = true AND (v.annulee IS NULL OR v.annulee = false) AND v.dateReglement BETWEEN :debutDate AND :finDate")
    Double getTotalReglementsDuJour(@Param("debutDate") LocalDate debutDate, @Param("finDate") LocalDate finDate);

    Optional<Vente> findByClientRequestId(String clientRequestId);

    @Query("SELECT COALESCE(SUM(v.beneficeTotal - COALESCE(v.beneficeRetourne, 0)), 0) FROM Vente v WHERE v.dateVente >= :debut AND v.dateVente <= :fin AND (v.annulee IS NULL OR v.annulee = false)")
    Double getBeneficeTotalByDateRange(@Param("debut") LocalDateTime debut, @Param("fin") LocalDateTime fin);

    @Query("SELECT COALESCE(SUM(v.montantTotal - COALESCE(v.montantMarchandiseRetournee, 0)), 0) FROM Vente v WHERE v.dateVente >= :debut AND v.dateVente <= :fin AND (v.annulee IS NULL OR v.annulee = false)")
    Double getCAByDateRange(@Param("debut") LocalDateTime debut, @Param("fin") LocalDateTime fin);

    @Query("SELECT COUNT(v) FROM Vente v WHERE v.dateVente >= :debut AND v.dateVente <= :fin AND (v.annulee IS NULL OR v.annulee = false)")
    Long countByDateRange(@Param("debut") LocalDateTime debut, @Param("fin") LocalDateTime fin);

    @Query("SELECT COUNT(v) FROM Vente v WHERE v.vendeur.id = :vendeurId AND v.dateVente >= :debut AND v.dateVente <= :fin AND (v.annulee IS NULL OR v.annulee = false)")
    Long countByVendeurIdAndDateRange(@Param("vendeurId") Long vendeurId, @Param("debut") LocalDateTime debut, @Param("fin") LocalDateTime fin);

    @Query("SELECT v FROM Vente v WHERE v.modePaiement = :mode AND v.dateVente >= :debut AND v.dateVente <= :fin AND (v.annulee IS NULL OR v.annulee = false) ORDER BY v.dateVente DESC")
    List<Vente> findByModePaiementAndDateRange(@Param("mode") ModePaiement mode, @Param("debut") LocalDateTime debut, @Param("fin") LocalDateTime fin);

    @Query("SELECT v FROM Vente v WHERE v.modePaiement IN :modes AND v.dateVente >= :debut AND v.dateVente <= :fin AND (v.annulee IS NULL OR v.annulee = false) ORDER BY v.dateVente DESC")
    List<Vente> findByModePaiementInAndDateRange(@Param("modes") List<ModePaiement> modes, @Param("debut") LocalDateTime debut, @Param("fin") LocalDateTime fin);

    // =========================================================
    // Requêtes IA — moteur analytique local
    // =========================================================

    /**
     * CA agrégé par jour sur une période — utilisé pour la régression linéaire et l'EMA.
     * Retourne [java.sql.Date jour, BigDecimal ca].
     */
    @Query(value = "SELECT DATE(v.date_vente) AS jour, COALESCE(SUM(v.montant_total - COALESCE(v.montant_marchandise_retournee, 0)), 0) AS ca " +
                   "FROM ventes v " +
                   "WHERE v.date_vente >= :debut AND v.date_vente <= :fin " +
                   "AND (v.annulee IS NULL OR v.annulee = 0) " +
                   "GROUP BY DATE(v.date_vente) " +
                   "ORDER BY DATE(v.date_vente) ASC",
           nativeQuery = true)
    List<Object[]> findCAParJour(@Param("debut") LocalDateTime debut, @Param("fin") LocalDateTime fin);

    /**
     * Données RFM (Recency-Frequency-Monetary) par client sur 90 jours.
     * Retourne [clientId, dernierAchat (Timestamp), nbAchats (Long), montantTotal (BigDecimal)].
     */
    @Query(value = "SELECT v.client_id, MAX(v.date_vente) AS dernierAchat, COUNT(*) AS nbAchats, " +
                   "COALESCE(SUM(v.montant_total - COALESCE(v.montant_marchandise_retournee, 0)), 0) AS montantTotal " +
                   "FROM ventes v " +
                   "WHERE v.client_id IS NOT NULL " +
                   "AND (v.annulee IS NULL OR v.annulee = 0) " +
                   "AND v.date_vente >= :debut " +
                   "GROUP BY v.client_id",
           nativeQuery = true)
    List<Object[]> findRFMData(@Param("debut") LocalDateTime debut);

    /**
     * Date du premier achat par client — pour identifier les nouveaux clients.
     * Retourne [clientId, premierAchat (Timestamp)].
     */
    @Query(value = "SELECT v.client_id, MIN(v.date_vente) AS premierAchat " +
                   "FROM ventes v " +
                   "WHERE v.client_id IS NOT NULL " +
                   "AND (v.annulee IS NULL OR v.annulee = 0) " +
                   "GROUP BY v.client_id",
           nativeQuery = true)
    List<Object[]> findPremierAchatParClient();

    /**
     * Vélocité des produits (quantités vendues) depuis une date — pour détecter sous-performants.
     * Retourne [produitId, quantiteTotaleVendue (BigDecimal)] trié par quantité décroissante.
     */
    @Query(value = "SELECT l.produit_id, COALESCE(SUM(l.quantite), 0) AS totalVendu " +
                   "FROM lignes_vente l " +
                   "JOIN ventes v ON l.vente_id = v.id " +
                   "WHERE v.date_vente >= :debut " +
                   "AND (v.annulee IS NULL OR v.annulee = 0) " +
                   "GROUP BY l.produit_id " +
                   "ORDER BY totalVendu DESC",
           nativeQuery = true)
    List<Object[]> findVelociteProduits(@Param("debut") LocalDateTime debut);

    @Query("SELECT COALESCE(SUM(v.montantTotal - COALESCE(v.montantMarchandiseRetournee, 0)), 0) FROM Vente v WHERE v.modePaiement = :mode AND v.dateVente >= :debut AND v.dateVente <= :fin AND (v.annulee IS NULL OR v.annulee = false)")
    Double getTotalByModePaiementAndDateRange(@Param("mode") ModePaiement mode, @Param("debut") LocalDateTime debut, @Param("fin") LocalDateTime fin);

    @Query("SELECT COUNT(v) FROM Vente v WHERE v.modePaiement = :mode AND v.dateVente >= :debut AND v.dateVente <= :fin AND (v.annulee IS NULL OR v.annulee = false)")
    Long countByModePaiementAndDateRange(@Param("mode") ModePaiement mode, @Param("debut") LocalDateTime debut, @Param("fin") LocalDateTime fin);

    @Query("SELECT COALESCE(SUM(v.montantTotal - COALESCE(v.montantMarchandiseRetournee, 0)), 0) FROM Vente v WHERE v.modePaiement IN :modes AND v.dateVente >= :debut AND v.dateVente <= :fin AND (v.annulee IS NULL OR v.annulee = false)")
    Double getTotalMobileMoneyByDateRange(@Param("modes") List<ModePaiement> modes, @Param("debut") LocalDateTime debut, @Param("fin") LocalDateTime fin);

    @Query("SELECT COALESCE(SUM(v.montantTotal - COALESCE(v.montantMarchandiseRetournee, 0)), 0) FROM Vente v WHERE (v.annulee IS NULL OR v.annulee = false) AND v.modePaiement NOT IN :modes")
    Double getTotalEspecesHorsMobileMoney(@Param("modes") List<ModePaiement> modes);

    @Query("SELECT COUNT(v) FROM Vente v WHERE (v.annulee IS NULL OR v.annulee = false) AND v.dateVente >= :debut AND v.dateVente <= :fin AND v.estCredit = false")
    Long countVentesComptantByDateRange(@Param("debut") LocalDateTime debut, @Param("fin") LocalDateTime fin);

    @Query("SELECT COUNT(v) FROM Vente v WHERE (v.annulee IS NULL OR v.annulee = false) AND v.dateVente >= :debut AND v.dateVente <= :fin AND v.estCredit = true")
    Long countVentesCreditByDateRange(@Param("debut") LocalDateTime debut, @Param("fin") LocalDateTime fin);

    @Query("SELECT v FROM Vente v LEFT JOIN FETCH v.lignes WHERE v.client.id = :clientId AND (v.annulee IS NULL OR v.annulee = false) ORDER BY v.dateVente DESC")
    List<Vente> findByClientId(@Param("clientId") Long clientId);

    @Query("SELECT v FROM Vente v LEFT JOIN FETCH v.lignes WHERE (v.clientNom = :nom OR v.clientTelephone = :telephone) AND (v.annulee IS NULL OR v.annulee = false) ORDER BY v.dateVente DESC")
    List<Vente> findByClientNomOrTelephone(@Param("nom") String nom, @Param("telephone") String telephone);

    // ==================== PARAMETRES — COMPTEURS STATUT ====================

    @Query("SELECT COUNT(v) FROM Vente v WHERE v.annulee = true")
    Long countVentesAnnulees();

    @Query("SELECT v FROM Vente v WHERE v.annulee = true AND v.dateAnnulation BETWEEN :debut AND :fin ORDER BY v.dateAnnulation DESC")
    List<Vente> findVentesAnnuleesByDateRange(@Param("debut") LocalDateTime debut, @Param("fin") LocalDateTime fin);

    @Query("SELECT COUNT(v) FROM Vente v WHERE v.annulee = true AND v.dateAnnulation BETWEEN :debut AND :fin")
    Long countVentesAnnuleesByDateRange(@Param("debut") LocalDateTime debut, @Param("fin") LocalDateTime fin);

    @Query("SELECT COUNT(v) FROM Vente v WHERE v.estCredit = true AND v.creditRegle = true " +
           "AND (v.annulee IS NULL OR v.annulee = false)")
    Long countCreditsRegles();

    // ==================== PARAMETRES — SUPPRESSION EN BLOC ====================

    @Modifying(clearAutomatically = true)
    @Transactional
    @Query(value = "DELETE FROM lignes_vente WHERE vente_id IN :venteIds", nativeQuery = true)
    int deleteLignesByVenteIds(@Param("venteIds") List<Long> venteIds);

    @Modifying(clearAutomatically = true)
    @Transactional
    @Query(value = "DELETE FROM ventes WHERE id IN :venteIds", nativeQuery = true)
    int deleteVentesByIds(@Param("venteIds") List<Long> venteIds);

    // ==================== GRAPHIQUES ANGULAR — RAPPORTS ====================

    /**
     * Top 10 produits les plus vendus sur une période (quantité cumulée).
     * Retourne [produitNom (String), quantiteVendue (Long)].
     */
    @Query(value = "SELECT p.nom AS produitNom, COALESCE(SUM(l.quantite), 0) AS quantiteVendue " +
                   "FROM lignes_vente l " +
                   "JOIN ventes v ON l.vente_id = v.id " +
                   "JOIN produits p ON l.produit_id = p.id " +
                   "WHERE v.date_vente >= :debut " +
                   "AND (v.annulee IS NULL OR v.annulee = 0) " +
                   "GROUP BY l.produit_id, p.nom " +
                   "ORDER BY quantiteVendue DESC " +
                   "LIMIT 10",
           nativeQuery = true)
    List<Object[]> findTopProduits(@Param("debut") LocalDateTime debut);

    /**
     * Ventes agrégées par heure pour la journée courante.
     * Retourne [heure (Integer 0-23), nbVentes (Long), ca (BigDecimal)].
     */
    @Query(value = "SELECT HOUR(v.date_vente) AS heure, COUNT(v.id) AS nbVentes, " +
                   "COALESCE(SUM(v.montant_total - COALESCE(v.montant_marchandise_retournee, 0)), 0) AS ca " +
                   "FROM ventes v " +
                   "WHERE DATE(v.date_vente) = CURDATE() " +
                   "AND (v.annulee IS NULL OR v.annulee = 0) " +
                   "GROUP BY HOUR(v.date_vente) " +
                   "ORDER BY HOUR(v.date_vente) ASC",
           nativeQuery = true)
    List<Object[]> findVentesParHeure();

    /**
     * Répartition des ventes par vendeur sur une période — pour l'assistant IA.
     * Retourne [nomComplet (String), nbVentes (Long), ca (BigDecimal)].
     */
    @Query(value = "SELECT u.nom_complet AS nom, COUNT(v.id) AS nbVentes, COALESCE(SUM(v.montant_total - COALESCE(v.montant_marchandise_retournee, 0)), 0) AS ca " +
                   "FROM ventes v JOIN utilisateurs u ON v.vendeur_id = u.id " +
                   "WHERE v.date_vente >= :debut AND v.date_vente <= :fin " +
                   "AND (v.annulee IS NULL OR v.annulee = 0) " +
                   "GROUP BY v.vendeur_id, u.nom_complet " +
                   "ORDER BY ca DESC",
           nativeQuery = true)
    List<Object[]> findVentesParVendeur(@Param("debut") LocalDateTime debut, @Param("fin") LocalDateTime fin);
}