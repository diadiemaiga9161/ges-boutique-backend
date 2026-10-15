package com.ges.boutique.devis;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Une ligne d'un devis : le prix est figé au moment du devis. */
@Entity
@Table(name = "lignes_devis")
@Getter
@Setter
@NoArgsConstructor
public class LigneDevis {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "devis_id")
    private Devis devis;

    @Column(name = "produit_id", nullable = false)
    private Long produitId;

    @Column(nullable = false, length = 200)
    private String designation;

    @Column(nullable = false)
    private Integer quantite;

    @Column(name = "prix_unitaire", nullable = false)
    private Double prixUnitaire;

    @Column(name = "sous_total", nullable = false)
    private Double sousTotal;
}
