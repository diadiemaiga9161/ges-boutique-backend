package com.ges.boutique.vente;

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
@Table(name = "lignes_vente")
@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
public class LigneVente {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "vente_id", nullable = false)
    @JsonBackReference
    private Vente vente;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "produit_id", nullable = false)
    private Produit produit;

    @Column(nullable = false)
    private Integer quantite;

    @Column(name = "prix_unitaire", nullable = false)
    private Double prixUnitaire;

    @Column(name = "prix_original_produit")
    private Double prixOriginalProduit;  // Sauvegarde du prix original du produit

    @Column(name = "prix_achat", nullable = false)
    private Double prixAchat = 0.0;

    @Column(name = "remise_pourcentage")
    private Double remisePourcentage = 0.0;

    @Column(name = "remise_montant")
    private Double remiseMontant = 0.0;

    @Column(name = "prix_apres_remise")
    private Double prixApresRemise;

    @Column(name = "sous_total", nullable = false)
    private Double sousTotal;

    @Column(name = "benefice", nullable = false)
    private Double benefice = 0.0;

    @Column(name = "niveau_facteur")
    private Integer niveauFacteur = 1; // facteur pour déduction stock (ex: 200 si 1 Carton = 200 Pièces)

    @Column(name = "niveau_id")
    private Long niveauId; // ID du ProduitNiveau vendu (null = vente au niveau produit de base)

    /** Réduction totale de la ligne due à une offre par quantité (ex: 25 pour « 2 pour 75 »). */
    @Column(name = "remise_offre")
    private Double remiseOffre = 0.0;

    @Column(name = "offre_libelle", length = 160)
    private String offreLibelle;

    /** Paliers de l'offre le jour de la vente (« 2:75;5:150 »), pour calculer un retour. */
    @Column(name = "offre_paliers", length = 160)
    private String offrePaliers;

    @PrePersist
    @PreUpdate
    protected void calculerSousTotal() {
        if (prixUnitaire == null) prixUnitaire = 0.0;
        if (quantite == null) quantite = 0;
        if (remisePourcentage == null) remisePourcentage = 0.0;
        if (remiseMontant == null) remiseMontant = 0.0;

        // Sauvegarder le prix original du produit si non défini
        if (prixOriginalProduit == null && produit != null) {
            prixOriginalProduit = produit.getPrixVente();
        }

        // Récupérer le prix d'achat du produit
        if (prixAchat == null || prixAchat == 0.0) {
            prixAchat = produit != null ? produit.getPrixAchat() : 0.0;
        }

        Double prixBase = prixUnitaire;

        if (remisePourcentage > 0) {
            Double reduction = prixBase * (remisePourcentage / 100);
            prixApresRemise = prixBase - reduction;
        } else if (remiseMontant > 0) {
            prixApresRemise = Math.max(0, prixBase - remiseMontant);
        } else {
            prixApresRemise = prixBase;
        }

        if (prixApresRemise < 0) {
            prixApresRemise = 0.0;
        }

        double reductionOffre = remiseOffre != null && remiseOffre > 0 ? remiseOffre : 0.0;
        sousTotal = Math.max(0, prixApresRemise * quantite - reductionOffre);
        benefice = sousTotal - prixAchat * quantite;

        sousTotal = BigDecimal.valueOf(sousTotal)
                .setScale(2, RoundingMode.HALF_UP)
                .doubleValue();
        prixApresRemise = BigDecimal.valueOf(prixApresRemise)
                .setScale(2, RoundingMode.HALF_UP)
                .doubleValue();
        benefice = BigDecimal.valueOf(benefice)
                .setScale(2, RoundingMode.HALF_UP)
                .doubleValue();
    }

    /** Offre par quantité du gérant (voir OffreQuantiteService) : remise totale de la ligne. */
    public void appliquerRemiseOffre(double remise, String libelle, String paliers) {
        this.remiseOffre = remise;
        this.offreLibelle = libelle;
        this.offrePaliers = paliers;
        calculerSousTotal();
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

    public Double getMontantRemise() {
        if (prixUnitaire == null || quantite == null) return 0.0;
        Double totalSansRemise = prixUnitaire * quantite;
        Double remise = totalSansRemise - sousTotal;
        return BigDecimal.valueOf(Math.max(0, remise))
                .setScale(2, RoundingMode.HALF_UP)
                .doubleValue();
    }

    public String getProduitNom() {
        return produit != null ? produit.getNom() : null;
    }

    public Long getProduitId() {
        return produit != null ? produit.getId() : null;
    }
}