package com.ges.boutique.commission;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Condition de prime posée par le gérant sur un produit (ou toute une catégorie) :
 * "si le vendeur vend ce produit, il gagne tant". Les primes ne sont jamais stockées :
 * elles sont recalculées à partir des ventes réelles (ventes annulées et retours exclus),
 * donc toujours justes même après une annulation.
 */
@Entity
@Table(name = "regle_commission")
@Getter
@Setter
@NoArgsConstructor
public class RegleCommission {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 120)
    private String nom;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, columnDefinition = "VARCHAR(20)")
    private TypeCommission type;

    /** Produit concerné, ou null si la règle vise toute une catégorie. */
    @Column(name = "produit_id")
    private Long produitId;

    @Column(name = "categorie_id")
    private Long categorieId;

    /** Montant par unité (MONTANT_FIXE), pourcentage (POURCENTAGE) ou montant de la prime (OBJECTIF). */
    @Column(nullable = false)
    private Double valeur;

    /** Quantité à atteindre, seulement pour OBJECTIF (en unité de base du produit). */
    @Column(name = "quantite_objectif")
    private Integer quantiteObjectif;

    @Column(name = "date_debut", nullable = false)
    private LocalDate dateDebut;

    /** Null = sans fin (interdit pour OBJECTIF, qui a besoin d'une période). */
    @Column(name = "date_fin")
    private LocalDate dateFin;

    /** Null = tous les vendeurs. */
    @Column(name = "vendeur_id")
    private Long vendeurId;

    @Column(name = "date_creation")
    private LocalDateTime dateCreation;

    @PrePersist
    protected void onCreate() {
        dateCreation = LocalDateTime.now();
    }
}
