package com.ges.boutique.commande;

import com.fasterxml.jackson.annotation.JsonBackReference;
import com.ges.boutique.produit.Produit;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "lignes_commande")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class LigneCommande {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "commande_id", nullable = false)
    @JsonBackReference
    private Commande commande;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "produit_id", nullable = false)
    private Produit produit;

    @Column(nullable = false)
    private Integer quantite;

    @Column(name = "prix_unitaire", nullable = false)
    private Double prixUnitaire;

    @Column(name = "prix_achat")
    private Double prixAchat = 0.0;

    @Column(name = "sous_total")
    private Double sousTotal = 0.0;

    @PrePersist
    @PreUpdate
    public void calculer() {
        if (prixUnitaire == null) prixUnitaire = 0.0;
        if (quantite == null) quantite = 0;
        if (produit != null && (prixAchat == null || prixAchat == 0.0)) {
            prixAchat = produit.getPrixAchat();
        }
        sousTotal = prixUnitaire * quantite;
    }

    public String getProduitNom() {
        return produit != null ? produit.getNom() : null;
    }

    public Long getProduitId() {
        return produit != null ? produit.getId() : null;
    }

    /** Vente à la mesure : unité du produit (kg, L, m) pour afficher "1,25 kg", sinon null. */
    @com.fasterxml.jackson.annotation.JsonProperty("modeMesure")
    public com.ges.boutique.produit.ModeMesure getModeMesure() {
        return produit != null ? produit.getModeMesure() : null;
    }
}
