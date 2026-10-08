package com.ges.boutique.fournisseur;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

// Les écrans peuvent joindre des infos d'affichage (ex : modeMesure) : on les ignore.
@JsonIgnoreProperties(ignoreUnknown = true)
@Data
public class LigneAchatRequest {
    // Pour produit existant
    private Long produitId;

    // Pour nouveau produit
    private String nouveauProduitNom;
    private Long nouvelleCategorieId;
    private Double prixVente; // facultatif, sinon calcul auto
    private String description;
    private String codeBarre;
    private Integer seuilAlerte;
    private String uniteMesure;
    private boolean bio;
    private String origine;
    private String typeVente;

    // Champs communs obligatoires
    private Integer quantite;
    private Double prixAchatUnitaire;

    // Optionnel : nouveau prix d'achat recalculé (coût moyen pondéré / CUMP) côté front, déjà confirmé par l'utilisateur.
    // Ne s'applique que pour un produit EXISTANT (produitId != null). Si null/absent, aucun changement.
    private Double nouveauPrixAchat;
}