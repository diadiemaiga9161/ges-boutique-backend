package com.ges.boutique.notification;

import com.ges.boutique.caisse.CaisseRepository;
import com.ges.boutique.vente.VenteRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;

/**
 * Résumé de la journée envoyé chaque soir à 22 h (heure de Bamako) à tous les téléphones abonnés,
 * et gardé dans la liste des notifications. Rien n'est envoyé un jour sans vente.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ResumeDuSoirTache {

    private static final ZoneId BAMAKO = ZoneId.of("Africa/Bamako");

    private final VenteRepository venteRepository;
    private final CaisseRepository caisseRepository;
    private final NotificationPersistanceService notificationPersistance;

    @Scheduled(cron = "0 0 22 * * *", zone = "Africa/Bamako")
    public void envoyerResume() {
        try {
            // Journée de Bamako, exprimée à l'heure du serveur (celle des dates enregistrées).
            LocalDate jour = LocalDate.now(BAMAKO);
            LocalDateTime debut = jour.atStartOfDay(BAMAKO).withZoneSameInstant(ZoneId.systemDefault()).toLocalDateTime();
            LocalDateTime fin = jour.plusDays(1).atStartOfDay(BAMAKO).withZoneSameInstant(ZoneId.systemDefault())
                    .toLocalDateTime().minusNanos(1);

            Long nb = venteRepository.countByDateRange(debut, fin);
            if (nb == null || nb == 0) return;
            Double ca = venteRepository.getCAByDateRange(debut, fin);
            Double caisse = caisseRepository.findCaisseOuverte().map(c -> c.getSoldeActuel()).orElse(null);

            String message = nb + (nb > 1 ? " ventes" : " vente") + " pour " + montant(ca) + " FCFA."
                    + (caisse != null ? " Argent dans la caisse : " + montant(caisse) + " FCFA." : "");
            notificationPersistance.creer("RESUME_JOUR", "Résumé de la journée", message, "/pages/caisse");
        } catch (Exception e) {
            log.warn("Résumé du soir non envoyé : {}", e.getMessage());
        }
    }

    private static String montant(Double v) {
        return String.format("%,d", Math.round(v != null ? v : 0.0)).replace(',', ' ');
    }
}
