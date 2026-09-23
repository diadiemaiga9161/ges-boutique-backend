package com.ges.boutique.securite;

import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Suivi léger "qui est en ligne" : purement en mémoire (pas de table en base, pas
 * d'appel réseau supplémentaire) — chaque requête HTTP authentifiée avec succès
 * (voir JwtFilter) met à jour la dernière activité de l'utilisateur. Un utilisateur
 * est considéré "en ligne" tant que sa dernière requête date de moins de
 * {@link #SEUIL_INACTIVITE_MINUTES} minutes.
 *
 * Volontairement pas basé sur les sessions WebSocket existantes (déjà utilisées pour
 * les notifications) : cette connexion n'est pas authentifiée côté serveur
 * (SecurityConfig autorise /ws/** sans JWT), donc on ne peut pas en déduire fiablement
 * quel utilisateur est derrière chaque session.
 */
@Service
public class PresenceService {

    private static final long SEUIL_INACTIVITE_MINUTES = 3;

    private final Map<String, Instant> derniereActivite = new ConcurrentHashMap<>();

    public void marquerActif(String username) {
        derniereActivite.put(username, Instant.now());
    }

    public List<String> obtenirUtilisateursEnLigne() {
        Instant seuil = Instant.now().minusSeconds(SEUIL_INACTIVITE_MINUTES * 60);
        return derniereActivite.entrySet().stream()
                .filter(e -> e.getValue().isAfter(seuil))
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());
    }
}
