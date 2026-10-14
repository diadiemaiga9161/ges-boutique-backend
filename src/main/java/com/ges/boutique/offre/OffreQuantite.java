package com.ges.boutique.offre;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Offre par quantité réglée par le gérant : « 2 pour 75 F ». Un produit peut avoir
 * plusieurs paliers (2 pour 75 F, 5 pour 150 F), appliqués automatiquement à la vente.
 */
@Entity
@Table(name = "offres_quantite", indexes = @Index(name = "idx_offre_produit", columnList = "produit_id"))
@Data
@NoArgsConstructor
public class OffreQuantite {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "produit_id", nullable = false)
    private Long produitId;

    /** Nombre d'articles du lot (2 ou plus). */
    @Column(nullable = false)
    private Integer quantite;

    /** Prix du lot entier. */
    @Column(nullable = false)
    private Double prix;

    @Column(name = "date_debut")
    private LocalDate dateDebut;

    @Column(name = "date_fin")
    private LocalDate dateFin;

    @Column(nullable = false)
    private Boolean active = true;

    @Column(name = "cree_par", length = 120)
    private String creePar;

    @Column(name = "date_creation")
    private LocalDateTime dateCreation;

    public boolean estValable(LocalDate jour) {
        return Boolean.TRUE.equals(active)
                && (dateDebut == null || !jour.isBefore(dateDebut))
                && (dateFin == null || !jour.isAfter(dateFin));
    }
}
