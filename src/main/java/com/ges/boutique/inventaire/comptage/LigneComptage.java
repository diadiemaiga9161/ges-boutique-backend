package com.ges.boutique.inventaire.comptage;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * Un produit compté. On garde le stock enregistré AU MOMENT où il a été compté : à la
 * validation on applique seulement l'écart (compté − stock à ce moment), ce qui laisse
 * intactes les ventes faites pendant le comptage (boutique ouverte).
 * Quantités dans l'unité de stock (pièces, ou g/ml/cm pour la vente à la mesure).
 */
@Entity
@Table(name = "ligne_comptage",
        uniqueConstraints = @UniqueConstraint(columnNames = {"comptage_id", "produit_id"}))
@Getter
@Setter
@NoArgsConstructor
public class LigneComptage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "comptage_id", nullable = false)
    private Long comptageId;

    @Column(name = "produit_id", nullable = false)
    private Long produitId;

    @Column(name = "produit_nom", length = 200)
    private String produitNom;

    @Column(name = "quantite_comptee", nullable = false)
    private Integer quantiteComptee;

    @Column(name = "stock_au_comptage", nullable = false)
    private Integer stockAuComptage;

    /** Prix d'achat unitaire au moment du comptage (valeur de l'écart en FCFA). */
    @Column(name = "prix_achat")
    private Double prixAchat;

    @Column(name = "compte_par", length = 150)
    private String comptePar;

    @Column(name = "date_comptage", nullable = false)
    private LocalDateTime dateComptage;

    @Transient
    public int getEcart() {
        return (quantiteComptee == null ? 0 : quantiteComptee) - (stockAuComptage == null ? 0 : stockAuComptage);
    }
}
