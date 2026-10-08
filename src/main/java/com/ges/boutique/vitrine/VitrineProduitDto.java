package com.ges.boutique.vitrine;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO public et minimal pour la vitrine (mini-site vitrine automatique).
 * ATTENTION SÉCURITÉ : ce DTO est exposé SANS authentification.
 * N'y ajouter QUE des champs destinés à être visibles par n'importe quel visiteur
 * (jamais prixAchat, fournisseur, quantité exacte, seuilAlerte, codeBarre, lotNumber...).
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class VitrineProduitDto {

    private Long id;
    private String nom;
    private String categorieNom;
    private Double prixVente;
    /** Vente à la mesure : "kg", "L" ou "m" (prix alors exprimé par cette unité), sinon null. */
    private String unite;
    private boolean disponible;

    /** DISPONIBLE / STOCK_FAIBLE / RUPTURE — statut affiché au client, jamais la quantité exacte. */
    private String statutStock;

    private boolean enPromotion;
    private String promotionTitre;
    private String promotionReduction;
    /** Version de la photo (null = pas de photo, ou photos désactivées) : l'image se lit sur
     *  /api/public/produits/{id}/image?taille=mini&v=... (publique, cache longue durée). */
    private Long imageVersion;
}
