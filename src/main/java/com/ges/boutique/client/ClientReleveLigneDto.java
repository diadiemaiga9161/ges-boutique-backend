package com.ges.boutique.client;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Une ligne d'affichage du relevé client (situation client) — contrat JSON partagé par
 * Angular, Ionic et React Native (voir GET /api/clients/{id}/releve).
 *
 * Types possibles :
 * - "VENTE" : une ligne par produit de la vente. Seule la PREMIÈRE ligne produit d'une même
 *   vente porte montantVente et resteAPayerApres (les lignes suivantes de la même vente les
 *   laissent à null pour ne pas compter plusieurs fois le montant/le reliquat de cette vente).
 * - "VERSEMENT" : un règlement de crédit (acompte initial ou versement ultérieur). Un paiement
 *   groupé (plusieurs crédits réglés en une fois) reste UNE seule ligne ; ventesReglees détaille
 *   alors les ventes concernées, leurs produits et la part du versement reçue par chacune.
 * - "RETOUR" : un retour d'articles sur une vente à crédit, traité comme une réduction du
 *   reliquat au même titre qu'un versement.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ClientReleveLigneDto {

    private LocalDateTime date;
    private String type; // VENTE | VERSEMENT | RETOUR

    private String referenceVente;      // numéro de vente (contexte, présent sur les 3 types)
    private String referenceReglement;  // "REG-{id}" pour un versement, numéroRetour pour un retour
    private Long venteId;

    private String produitNom;  // uniquement pour VENTE
    private Integer quantite;   // uniquement pour VENTE
    private Double prixUnitaire; // uniquement pour VENTE

    private Double montantVente;       // uniquement sur la 1ère ligne produit d'une vente
    private Double montantVersement;   // versement ou retour
    private Double resteAPayerApres;   // reliquat cumulé après ce mouvement (null si ligne produit "secondaire")

    private String modePaiement; // uniquement pour VERSEMENT
    private String utilisateurNom;

    private List<VenteReglee> ventesReglees; // uniquement pour VERSEMENT (1 élément = versement simple)

    /** Vente réglée (en tout ou partie) par un versement. */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class VenteReglee {
        private Long venteId;
        private String numeroVente;
        private LocalDateTime dateVente;
        private Double montantApplique; // part du versement affectée à cette vente
        private List<ProduitVendu> produits;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ProduitVendu {
        private String produitNom;
        private Integer quantite;
        private Double prixUnitaire;
    }
}
