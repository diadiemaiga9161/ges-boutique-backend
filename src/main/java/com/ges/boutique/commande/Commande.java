package com.ges.boutique.commande;

import com.fasterxml.jackson.annotation.JsonManagedReference;
import com.ges.boutique.client.Client;
import com.ges.boutique.utilisateur.Utilisateur;
import com.ges.boutique.vente.ModePaiement;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "commandes")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Commande {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String numeroCommande;

    // Nullable : une commande venue de la vitrine publique n'a pas encore de vendeur
    // au moment de la création — il n'est assigné qu'à la validation (cf. valider()).
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "vendeur_id")
    private Utilisateur vendeur;

    // columnDefinition avec DEFAULT : ddl-auto=update va ajouter cette colonne NOT NULL sur
    // des tables "commandes" qui ont déjà des lignes existantes (boutiques en prod) — sans
    // valeur par défaut au niveau SQL, cet ALTER TABLE échouerait au démarrage.
    @Enumerated(EnumType.STRING)
    @Column(name = "origine", nullable = false, columnDefinition = "VARCHAR(20) DEFAULT 'MAGASIN'")
    private OrigineCommande origine = OrigineCommande.MAGASIN;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "client_id")
    private Client client;

    @Column(name = "client_nom")
    private String clientNom;

    @Column(name = "client_prenom")
    private String clientPrenom;

    @Column(name = "client_telephone")
    private String clientTelephone;

    @OneToMany(mappedBy = "commande", cascade = CascadeType.ALL, fetch = FetchType.EAGER, orphanRemoval = true)
    @JsonManagedReference
    private List<LigneCommande> lignes = new ArrayList<>();

    @Column(name = "montant_total")
    private Double montantTotal = 0.0;

    @Enumerated(EnumType.STRING)
    @Column(name = "mode_paiement")
    private ModePaiement modePaiement;

    @Column(name = "reference_paiement")
    private String referencePaiement;

    @Column(name = "est_credit")
    private Boolean estCredit = false;

    @Column(name = "montant_verse")
    private Double montantVerse = 0.0;

    @Column(name = "montant_restant")
    private Double montantRestant = 0.0;

    @Column(name = "date_echeance")
    private LocalDate dateEcheance;

    @Enumerated(EnumType.STRING)
    @Column(name = "statut", nullable = false)
    private StatutCommande statut = StatutCommande.BROUILLON;

    @Column(name = "date_commande")
    private LocalDateTime dateCommande;

    @Column(name = "date_validation")
    private LocalDateTime dateValidation;

    @Column(name = "vente_id")
    private Long venteId;

    @Column(name = "notes", length = 500)
    private String notes;

    // Livraison — renseignés au moment de valider() une commande (surtout utile pour
    // celles venues de la vitrine, à livrer). Jamais obligatoires : une commande retirée
    // en magasin n'a besoin d'aucun des quatre champs ci-dessous.
    @Column(name = "adresse_livraison", length = 500)
    private String adresseLivraison;

    @Column(name = "frais_livraison")
    private Double fraisLivraison;

    @Column(name = "chauffeur_nom")
    private String chauffeurNom;

    @Column(name = "chauffeur_telephone")
    private String chauffeurTelephone;

    /** Après confirmation : Prête puis Livrée (voir EtapeLivraison). null = pas encore prête. */
    @Enumerated(EnumType.STRING)
    @Column(name = "etape_livraison", length = 20)
    private EtapeLivraison etapeLivraison;

    @Column(name = "date_etape_livraison")
    private LocalDateTime dateEtapeLivraison;

    @PrePersist
    protected void onCreate() {
        dateCommande = LocalDateTime.now();
        if (numeroCommande == null) {
            numeroCommande = "CMD-" + System.currentTimeMillis();
        }
        recalculer();
    }

    @PreUpdate
    protected void onUpdate() {
        recalculer();
    }

    public void recalculer() {
        montantTotal = lignes == null ? 0.0 :
                lignes.stream().mapToDouble(l -> l.getSousTotal() != null ? l.getSousTotal() : 0.0).sum();
        if (Boolean.TRUE.equals(estCredit)) {
            montantRestant = montantTotal - (montantVerse != null ? montantVerse : 0.0);
        } else {
            montantRestant = 0.0;
        }
    }

    public Long getVendeurId() {
        return vendeur != null ? vendeur.getId() : null;
    }

    public Long getClientId() {
        return client != null ? client.getId() : null;
    }
}
