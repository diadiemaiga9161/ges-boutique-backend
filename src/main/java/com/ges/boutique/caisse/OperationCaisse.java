package com.ges.boutique.caisse;

import com.ges.boutique.utilisateur.Utilisateur;
import com.ges.boutique.vente.Vente;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

@Entity
@Table(name = "operations_caisse")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class OperationCaisse {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "caisse_id")
    private Caisse caisse;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, columnDefinition = "VARCHAR(50)")
    private TypeOperationCaisse type;

    @Column(nullable = false)
    private Double montant;

    @Column(nullable = false)
    private Double soldeAvant;

    @Column(nullable = false)
    private Double soldeApres;

    private String motif;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "utilisateur_id")
    private Utilisateur utilisateur;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "vente_id")
    private Vente vente;

    // columnDefinition forcé en VARCHAR : même précaution que Vente.modePaiement (voir
    // migration V107) — sans ça, Hibernate crée un ENUM MySQL natif figé qui plante dès
    // qu'une nouvelle valeur (ex: WAVE_MONEY) est ajoutée à ModePaiementCaisse.
    @Enumerated(EnumType.STRING)
    @Column(name = "mode_paiement", columnDefinition = "VARCHAR(20)")
    private ModePaiementCaisse modePaiement;

    @Column(name = "reference_paiement")
    private String referencePaiement;

    @Column(name = "client_nom")
    private String clientNom;

    @Column(name = "client_telephone")
    private String clientTelephone;

    @Column(name = "date_operation")
    private LocalDateTime dateOperation;

    @Column(name = "est_reglee", nullable = false)
    private boolean estReglee = true;

    @Column(name = "date_echeance")
    private LocalDateTime dateEcheance;

    @Column(name = "montant_verse")
    private Double montantVerse = 0.0;

    @Column(name = "montant_restant")
    private Double montantRestant = 0.0;

    @Column(name = "vente_credit_id")
    private Long venteCreditId;

    @Column(name = "numero_credit")
    private String numeroCredit;

    @Column(name = "periode")
    private String periode;

    // NOUVEAU FLAG POUR INDIQUER QUE LA VENTE ASSOCIÉE EST ANNULÉE
    @Column(name = "vente_annulee")
    private Boolean venteAnnulee = false;

    @Column(name = "reference_groupe")
    private String referenceGroupe;

    @Column(name = "annule", nullable = false, columnDefinition = "BOOLEAN DEFAULT false")
    private boolean annule = false;

    @Column(name = "date_annulation_reglement")
    private LocalDateTime dateAnnulationReglement;

    @PrePersist
    protected void onCreate() {
        dateOperation = LocalDateTime.now();
        // Auteur = la personne connectée, jamais l'identifiant envoyé par l'appli (souvent vide ou
        // faux) : la réconciliation de la caisse sait ainsi qui a sorti l'argent. Exception : une
        // vente reste au nom du vendeur de la vente, même enregistrée par le gérant.
        Utilisateur connecte = com.ges.boutique.securite.UtilisateurConnecte.actuel();
        boolean vente = type == TypeOperationCaisse.VENTE_COMPTANT || type == TypeOperationCaisse.VENTE_CREDIT;
        if (connecte != null && !(vente && utilisateur != null)) {
            utilisateur = connecte;
        }
        if (type == TypeOperationCaisse.VENTE_CREDIT) {
            if (montantVerse == null) {
                montantVerse = 0.0;
            }
            if (montantRestant == null || montantRestant <= 0) {
                montantRestant = Math.max(0.0, montant - montantVerse);
            }
        } else if (type == TypeOperationCaisse.REGLEMENT_CREDIT) {
            if (montantVerse == null) {
                montantVerse = montant;
            }
            if (montantRestant == null) {
                montantRestant = 0.0;
            }
        }
        if (venteAnnulee == null) {
            venteAnnulee = false;
        }
    }
}