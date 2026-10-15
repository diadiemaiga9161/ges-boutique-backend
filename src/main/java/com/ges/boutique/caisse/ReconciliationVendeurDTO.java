package com.ges.boutique.caisse;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO en lecture seule pour le rapport "Réconciliation caisse par vendeur".
 * Un item par vendeur ayant eu de l'activité (vente ou règlement de crédit)
 * sur la date demandée. Aucune nouvelle table : calcul à la volée à partir
 * des Vente et OperationCaisse déjà enregistrées.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ReconciliationVendeurDTO {

    private Long vendeurId;
    private String vendeurNom;

    /** Nombre de ventes comptant (mode de règlement != crédit) faites ce jour par ce vendeur. */
    private Long nombreVentes;

    /** Somme des ventes comptant réglées en ESPECES par ce vendeur, ce jour. */
    private Double totalVentesEspeces;

    /** Somme du montant total des ventes à crédit enregistrées par ce vendeur ce jour (même si pas encore payées). */
    private Double totalVentesCredit;

    /** Somme des règlements de crédits encaissés en ESPECES par ce vendeur ce jour. */
    private Double totalReglementsCreditEspeces;

    /** totalVentesEspeces + totalReglementsCreditEspeces : ce que le vendeur doit remettre en espèces en fin de journée. */
    private Double totalAiRemettre;

    /** Argent rendu aux clients depuis le tiroir par cette personne pour des retours, ce jour. */
    private Double totalRetoursRembourses = 0.0;

    /**
     * Autres mouvements du tiroir faits par cette personne autour des ventes (acomptes de crédit,
     * annulations de ventes, compléments ou remboursements après modification d'une vente).
     */
    private Double totalAutresMouvementsVentes = 0.0;

    public ReconciliationVendeurDTO(Long vendeurId, String vendeurNom, Long nombreVentes, Double totalVentesEspeces,
                                    Double totalVentesCredit, Double totalReglementsCreditEspeces, Double totalAiRemettre) {
        this(vendeurId, vendeurNom, nombreVentes, totalVentesEspeces, totalVentesCredit, totalReglementsCreditEspeces,
                totalAiRemettre, 0.0, 0.0);
    }
}
