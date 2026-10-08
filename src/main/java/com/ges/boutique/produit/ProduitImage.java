package com.ges.boutique.produit;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Photo d'un produit (fonctionnalité IMAGES_PRODUITS), stockée à part de la table produits
 * pour que les listes de produits restent légères. Deux tailles, déjà compressées en JPEG
 * à l'enregistrement : une miniature pour les listes et une image moyenne pour la fiche.
 * En base (et pas sur disque) : rien à installer sur les serveurs et les photos suivent
 * automatiquement les sauvegardes.
 */
@Entity
@Table(name = "produit_image")
@Getter
@Setter
@NoArgsConstructor
public class ProduitImage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "produit_id", nullable = false, unique = true)
    private Long produitId;

    @Lob
    @Basic(fetch = FetchType.LAZY)
    @Column(name = "miniature", columnDefinition = "MEDIUMBLOB")
    private byte[] miniature;

    @Lob
    @Basic(fetch = FetchType.LAZY)
    @Column(name = "image", columnDefinition = "MEDIUMBLOB")
    private byte[] image;

    @Column(name = "version", nullable = false)
    private Long version;
}
