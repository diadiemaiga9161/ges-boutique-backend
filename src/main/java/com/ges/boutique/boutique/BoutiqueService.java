package com.ges.boutique.boutique;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional
public class BoutiqueService {

    private final BoutiqueRepository boutiqueRepository;

    public Boutique obtenirBoutique() {
        List<Boutique> boutiques = boutiqueRepository.findAll();

        if (boutiques.isEmpty()) {
            Boutique boutique = new Boutique();
            return boutiqueRepository.save(boutique);
        }

        return boutiques.get(0);
    }

    public Boutique modifierBoutique(Boutique boutique) {
        Boutique existingBoutique = obtenirBoutique();

        if (boutique.getNom() != null) {
            existingBoutique.setNom(boutique.getNom());
        }

        if (boutique.getAdresse() != null) {
            existingBoutique.setAdresse(boutique.getAdresse());
        }

        if (boutique.getTelephone() != null) {
            existingBoutique.setTelephone(boutique.getTelephone());
        }

        if (boutique.getEmail() != null) {
            existingBoutique.setEmail(boutique.getEmail());
        }

        if (boutique.getNumeroRc() != null) {
            existingBoutique.setNumeroRc(boutique.getNumeroRc());
        }

        if (boutique.getNumeroIfu() != null) {
            existingBoutique.setNumeroIfu(boutique.getNumeroIfu());
        }

        if (boutique.getVille() != null) {
            existingBoutique.setVille(boutique.getVille());
        }

        if (boutique.getPays() != null) {
            existingBoutique.setPays(boutique.getPays());
        }

        if (boutique.getCodePostal() != null) {
            existingBoutique.setCodePostal(boutique.getCodePostal());
        }

        if (boutique.getSiteWeb() != null) {
            existingBoutique.setSiteWeb(boutique.getSiteWeb());
        }

        if (boutique.getHorairesOuverture() != null) {
            existingBoutique.setHorairesOuverture(boutique.getHorairesOuverture());
        }

        if (boutique.getDescription() != null) {
            existingBoutique.setDescription(boutique.getDescription());
        }

        if (boutique.getActif() != null) {
            existingBoutique.setActif(boutique.getActif());
        }

        if (boutique.getCouleurPrimaire() != null) {
            existingBoutique.setCouleurPrimaire(boutique.getCouleurPrimaire());
        }

        // Les fonctionnalités activables/désactivables (feature_transferts_actif,
        // feature_vitrine_actif) ne se modifient PAS ici : cet endpoint (PUT /api/boutique)
        // est accessible à tout ADMIN de boutique, alors que ces réglages sont réservés
        // au SUPER_ADMIN — voir modifierFonctionnalites() + PUT /api/boutique/fonctionnalites.

        // Important :
        // Le logo est déjà géré par /api/boutique/upload-logo.
        // Ici on ne supprime jamais le logo existant.
        // Si le front renvoie logo/logoPath, on accepte seulement si la valeur est non vide.

        if (boutique.getLogo() != null && !boutique.getLogo().isBlank()) {
            existingBoutique.setLogo(boutique.getLogo());
        }

        return boutiqueRepository.save(existingBoutique);
    }

    public Boutique saveLogo(String base64Logo) {
        Boutique boutique = obtenirBoutique();
        boutique.setLogo(base64Logo);
        return boutiqueRepository.save(boutique);
    }

    public Boutique creerBoutique(Boutique boutique) {
        return boutiqueRepository.save(boutique);
    }

    // Réservé au SUPER_ADMIN (voir BoutiqueController) : seul endroit où
    // feature_transferts_actif / feature_vitrine_actif peuvent être modifiés.
    public Boutique modifierFonctionnalites(Boolean featureTransfertsActif, Boolean featureVitrineActif) {
        Boutique existingBoutique = obtenirBoutique();

        if (featureTransfertsActif != null) {
            existingBoutique.setFeatureTransfertsActif(featureTransfertsActif);
        }

        if (featureVitrineActif != null) {
            existingBoutique.setFeatureVitrineActif(featureVitrineActif);
        }

        return boutiqueRepository.save(existingBoutique);
    }

    // Réservé au SUPER_ADMIN (voir BoutiqueController) : seul endroit où
    // email_sauvegarde peut être modifié — un admin classique ne peut pas choisir
    // qui reçoit une copie des sauvegardes de sa boutique.
    public Boutique definirEmailSauvegarde(String emailSauvegarde) {
        Boutique existingBoutique = obtenirBoutique();
        existingBoutique.setEmailSauvegarde(emailSauvegarde != null && !emailSauvegarde.isBlank() ? emailSauvegarde.trim() : null);
        return boutiqueRepository.save(existingBoutique);
    }
}