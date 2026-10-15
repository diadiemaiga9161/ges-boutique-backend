package com.ges.boutique.securite;

import com.ges.boutique.utilisateur.Utilisateur;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * Auteur d'une action (retour, annulation, sortie de caisse…) : toujours la personne connectée,
 * jamais un identifiant envoyé par l'appli, qui peut manquer ou être faux. L'identifiant envoyé
 * ne sert qu'en secours quand aucune personne n'est connectée (tâches internes).
 */
public final class UtilisateurConnecte {

    private UtilisateurConnecte() {}

    /** La personne connectée, ou null (tâche planifiée, démarrage…). */
    public static Utilisateur actuel() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null && auth.getPrincipal() instanceof Utilisateur u && u.getId() != null ? u : null;
    }

    public static Long idOu(Long fourniParAppli) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof Utilisateur u && u.getId() != null) {
            return u.getId();
        }
        return fourniParAppli;
    }
}
