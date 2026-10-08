package com.ges.boutique.inventaire.comptage;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * Comptage d'inventaire : on compte les produits (tous, ou une catégorie), l'appli montre
 * les écarts avec le stock enregistré, puis la validation corrige le stock d'un coup.
 * Un seul comptage EN_COURS à la fois.
 */
@Entity
@Table(name = "comptage_inventaire")
@Getter
@Setter
@NoArgsConstructor
public class ComptageInventaire {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "statut", nullable = false, length = 20)
    private StatutComptage statut = StatutComptage.EN_COURS;

    /** null = tous les produits. */
    @Column(name = "categorie_id")
    private Long categorieId;

    @Column(name = "categorie_nom", length = 120)
    private String categorieNom;

    @Column(name = "commence_par", length = 150)
    private String commencePar;

    @Column(name = "date_debut", nullable = false)
    private LocalDateTime dateDebut;

    @Column(name = "termine_par", length = 150)
    private String terminePar;

    @Column(name = "date_fin")
    private LocalDateTime dateFin;
}
