package com.ges.boutique.commission;

import lombok.Data;

import java.time.LocalDate;

@Data
public class RegleCommissionRequest {
    private String nom;
    private TypeCommission type;
    private Long produitId;
    private Long categorieId;
    private Double valeur;
    private Integer quantiteObjectif;
    private LocalDate dateDebut;
    private LocalDate dateFin;
    private Long vendeurId;
}
