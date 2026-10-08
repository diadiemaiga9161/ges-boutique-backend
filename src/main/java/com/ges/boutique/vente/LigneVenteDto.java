package com.ges.boutique.vente;

import lombok.Data;

@Data
public class LigneVenteDto {
    private Long produitId;
    private String produitNom;
    private Integer quantite;
    /** Vente à la mesure : KG/L/M (quantite en g/ml/cm, prix par g/ml/cm), sinon null. */
    private com.ges.boutique.produit.ModeMesure modeMesure;
    private Double prixUnitaire;
    private Double remisePourcentage;
    private Double remiseMontant;
    private Double prixApresRemise;
    private Double sousTotal;
    private Double montantRemise;
}