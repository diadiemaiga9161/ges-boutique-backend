package com.ges.boutique.produit;

import lombok.Data;
import java.time.LocalDate;
import java.util.List;

@Data
public class ProduitDto {
    private Long id;
    private String nom;
    private String description;
    private Long categorieId;
    private String categorieNom;
    private Long fournisseurId;
    private String fournisseurNom;
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
    private boolean bio;
    private String origine;
    private String typeVente;
    private Long imageVersion;
    private ModeMesure modeMesure;
    private boolean stockFaible;
    private boolean perime;
    private boolean prochePeremption;
    private Double marge;
    private Double tauxMarge;
    private Long joursAvantPeremption;
    private List<ProduitNiveauDto> niveaux;
}