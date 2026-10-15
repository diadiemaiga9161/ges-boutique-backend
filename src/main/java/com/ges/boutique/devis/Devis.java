package com.ges.boutique.devis;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Devis (facture pro forma) pour un client comme une école, une mairie ou une ONG.
 * Ne touche ni au stock ni à la caisse tant qu'il n'est pas transformé en vente.
 */
@Entity
@Table(name = "devis", uniqueConstraints = @UniqueConstraint(name = "uk_devis_numero", columnNames = "numero"))
@Data
@NoArgsConstructor
public class Devis {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 30)
    private String numero;

    @Column(name = "client_id")
    private Long clientId;

    @Column(name = "client_nom", nullable = false, length = 160)
    private String clientNom;

    @Column(name = "client_telephone", length = 40)
    private String clientTelephone;

    @Column(name = "client_adresse", length = 200)
    private String clientAdresse;

    /** Objet du devis, ex : « Fournitures pour la cantine ». */
    @Column(length = 200)
    private String objet;

    @OneToMany(mappedBy = "devis", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    @OrderBy("id ASC")
    private List<LigneDevis> lignes = new ArrayList<>();

    @Column(name = "remise", nullable = false)
    private Double remise = 0.0;

    @Column(name = "montant_total", nullable = false)
    private Double montantTotal = 0.0;

    @Column(length = 500)
    private String conditions;

    @Column(name = "date_creation", nullable = false)
    private LocalDateTime dateCreation;

    @Column(name = "date_validite", nullable = false)
    private LocalDate dateValidite;

    /** EN_ATTENTE, TRANSFORME, REFUSE (EXPIRE est calculé à partir de la date de validité). */
    @Column(nullable = false, length = 20)
    private String statut = "EN_ATTENTE";

    @Column(name = "vente_id")
    private Long venteId;

    @Column(name = "cree_par", length = 120)
    private String creePar;
}
