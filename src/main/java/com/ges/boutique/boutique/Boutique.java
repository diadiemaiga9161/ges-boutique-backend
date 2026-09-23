package com.ges.boutique.boutique;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "boutique")
@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class Boutique {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String nom = "Boutique Alimentaire";

    @Column(nullable = false)
    private String adresse = "Adresse de la boutique";

    @Column(nullable = false)
    private String telephone = "+223 XX XX XX XX";

    @Column
    private String email = "contact@boutique.com";

    @Column
    private String description;

    @Column(name = "site_web")
    private String siteWeb;

    @Column(name = "horaires_ouverture")
    private String horairesOuverture;

    @Column
    private Boolean actif = true;

    @Column(name = "numero_rc")
    private String numeroRc = "RC-XXXX";

    @Column(name = "numero_ifu")
    private String numeroIfu = "IFU-XXXX";

    @Column
    private String ville = "Bamako";

    @Column
    private String pays = "Mali";

    @Column(name = "code_postal")
    private String codePostal = "00000";

    @Column(name = "logo", columnDefinition = "MEDIUMTEXT")
    private String logo;

    // Couleur principale du thème, choisie dans Paramètres Boutique — appliquée à toute l'appli Angular.
    @Column(name = "couleur_primaire")
    private String couleurPrimaire = "#2a63ff";

    @Column(name = "date_creation")
    private LocalDateTime dateCreation;

    @Column(name = "date_modification")
    private LocalDateTime dateModification;

    public enum ModeOuverture { MANUEL, AUTO }

    @Column(name = "mode_ouverture")
    @Enumerated(EnumType.STRING)
    private ModeOuverture modeOuverture = ModeOuverture.MANUEL;

    // ── Fonctionnalités activables/désactivables (super admin) ────────────
    // Vérifiées côté serveur (TransfertService, CommandeServiceImpl) — masquer
    // le menu ne suffit jamais seul, c'est ce booléen qui bloque vraiment.
    @Column(name = "feature_transferts_actif", columnDefinition = "TINYINT(1) DEFAULT 1")
    private Boolean featureTransfertsActif = true;

    @Column(name = "feature_vitrine_actif", columnDefinition = "TINYINT(1) DEFAULT 1")
    private Boolean featureVitrineActif = true;

    // ── Programme de fidélité (voir feature.CleFonctionnalite.PROGRAMME_FIDELITE) ──
    // Taux configurables par l'admin de la boutique (Paramètres). Valeurs par défaut
    // choisies pour être neutres si jamais activé sans configuration explicite.
    @Column(name = "fidelite_montant_par_point", columnDefinition = "DOUBLE DEFAULT 100")
    private Double fideliteMontantParPoint = 100.0;

    @Column(name = "fidelite_point_valeur", columnDefinition = "DOUBLE DEFAULT 10")
    private Double fidelitePointValeur = 10.0;

    // Adresse email qui reçoit une copie des sauvegardes automatiques — définie
    // uniquement par le super admin (voir BoutiqueController#definirEmailSauvegarde),
    // envoi actif seulement si feature.CleFonctionnalite.ENVOI_SAUVEGARDES_EMAIL est
    // activée pour cette boutique (voir BackupServiceImpl).
    @Column(name = "email_sauvegarde")
    private String emailSauvegarde;

    @PrePersist
    protected void onCreate() {
        dateCreation = LocalDateTime.now();
        dateModification = LocalDateTime.now();

        if (actif == null) {
            actif = true;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        dateModification = LocalDateTime.now();
    }

    // =====================================================
    // COMPATIBILITÉ FRONTEND
    // =====================================================
    // Le front utilise logoPath.
    // La base garde le vrai champ logo.
    // Cette méthode évite l'erreur "Unrecognized field logoPath"
    // sans changer Angular.

    @JsonProperty("logoPath")
    public String getLogoPath() {
        return this.logo;
    }

    @JsonProperty("logoPath")
    public void setLogoPath(String logoPath) {
        // Ne pas écraser le logo avec une valeur vide.
        if (logoPath != null && !logoPath.isBlank()) {
            this.logo = logoPath;
        }
    }

    // =====================================================
    // MÉTHODES UTILITAIRES POUR LES FACTURES
    // =====================================================

    public String getAdresseComplete() {
        return adresse + ", " + ville + " " + codePostal + ", " + pays;
    }

    public String getInformationsLegales() {
        return "RC: " + numeroRc + " - IFU: " + numeroIfu;
    }
}