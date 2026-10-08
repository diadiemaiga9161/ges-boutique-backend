package com.ges.boutique.produit;

import lombok.Data;
import java.time.LocalDate;

@Data
public class ProduitRequest {
    private String nom;
    private String description;
    private Long categorieId;
    private Long fournisseurId;
    private Double prixAchat;
    private Double prixVente;
    private Integer quantite;
    private Integer seuilAlerte;
    private String codeBarre;
    private LocalDate dateCreation;
    private LocalDate datePeremption;
    private String lotNumber;
    private String conditionsStockage;
    private Double poidsVolume;
    private String uniteMesure;
    private String uniteBase;
    private boolean bio;
    private String origine;
    private String typeVente; // "DETAIL" ou "ENGROS"
    /** Création seulement : produit vendu au kg/L/m (valeurs déjà en petite unité). */
    private ModeMesure modeMesure;
}