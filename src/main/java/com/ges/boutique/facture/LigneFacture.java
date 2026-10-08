package com.ges.boutique.facture;

import com.fasterxml.jackson.annotation.JsonBackReference;
import com.ges.boutique.produit.Produit;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Entity
@Table(name = "lignes_facture")
@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
public class LigneFacture {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "facture_id", nullable = false)
    @JsonBackReference
    private Facture facture;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "produit_id", nullable = true)
    private Produit produit;

    @Column(nullable = false)
    private Integer quantite;

    @Column(name = "prix_unitaire", nullable = false)
    private Double prixUnitaire;

    @Column(name = "prix_original_produit")
    private Double prixOriginalProduit;

    @Column(name = "prix_achat", nullable = false)
    private Double prixAchat = 0.0;

    @Column(name = "designation")
    private String designation;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "remise_pourcentage")
    private Double remisePourcentage = 0.0;

    @Column(name = "remise_montant")
    private Double remiseMontant = 0.0;

    @Column(name = "prix_apres_remise")
    private Double prixApresRemise;

    @Column(name = "sous_total", nullable = false)
    private Double sousTotal;

    @Column(name = "montant_remise")
    private Double montantRemise = 0.0;

    @Column(name = "benefice", nullable = false)
    private Double benefice = 0.0;

    @PrePersist
    @PreUpdate
    public void calculerSousTotal() {
        if (prixUnitaire == null) prixUnitaire = 0.0;
        if (quantite == null) quantite = 0;
        if (remisePourcentage == null) remisePourcentage = 0.0;
        if (remiseMontant == null) remiseMontant = 0.0;

        if (prixOriginalProduit == null && produit != null) {
            prixOriginalProduit = produit.getPrixVente();
        }

        if (prixAchat == null || prixAchat == 0.0) {
            prixAchat = produit != null ? produit.getPrixAchat() : 0.0;
        }

        if (designation == null && produit != null) {
            designation = produit.getNom();
        }

        Double prixBase = prixUnitaire;

        if (remisePourcentage > 0) {
            Double reduction = prixBase * (remisePourcentage / 100);
            prixApresRemise = prixBase - reduction;
            montantRemise = reduction * quantite;
        } else if (remiseMontant > 0) {
            prixApresRemise = Math.max(0, prixBase - remiseMontant);
            montantRemise = Math.min(remiseMontant, prixBase) * quantite;
        } else {
            prixApresRemise = prixBase;
            montantRemise = 0.0;
        }

        if (prixApresRemise < 0) {
            prixApresRemise = 0.0;
        }

        sousTotal = prixApresRemise * quantite;
        benefice = (prixApresRemise - prixAchat) * quantite;

        sousTotal = BigDecimal.valueOf(sousTotal).setScale(2, RoundingMode.HALF_UP).doubleValue();
        montantRemise = BigDecimal.valueOf(montantRemise).setScale(2, RoundingMode.HALF_UP).doubleValue();
        benefice = BigDecimal.valueOf(benefice).setScale(2, RoundingMode.HALF_UP).doubleValue();
        prixApresRemise = BigDecimal.valueOf(prixApresRemise).setScale(2, RoundingMode.HALF_UP).doubleValue();
    }

    public void appliquerRemisePourcentage(Double pourcentage) {
        if (pourcentage != null && pourcentage >= 0 && pourcentage <= 100) {
            this.remisePourcentage = pourcentage;
            this.remiseMontant = 0.0;
            calculerSousTotal();
        }
    }

    public void appliquerRemiseMontant(Double montant) {
        if (montant != null && montant >= 0) {
            this.remiseMontant = montant;
            this.remisePourcentage = 0.0;
            calculerSousTotal();
        }
    }

    public void modifierPrixUnitaire(Double nouveauPrix) {
        if (nouveauPrix != null && nouveauPrix >= 0) {
            this.prixUnitaire = nouveauPrix;
            calculerSousTotal();
        }
    }

    public void reinitialiserPrixOriginal() {
        if (prixOriginalProduit != null && prixOriginalProduit > 0) {
            this.prixUnitaire = prixOriginalProduit;
            calculerSousTotal();
        }
    }

    /** Vente à la mesure : unité du produit (kg, L, m) pour afficher "1,25 kg", sinon null. */
    @com.fasterxml.jackson.annotation.JsonProperty("modeMesure")
    public com.ges.boutique.produit.ModeMesure getModeMesure() {
        return produit != null ? produit.getModeMesure() : null;
    }
}
