package com.ges.boutique.notification;

import com.ges.boutique.produit.Produit;
import com.ges.boutique.produit.ProduitRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Chaque matin à 8 h (heure de Bamako) : une seule alerte groupée pour les produits encore en
 * stock qui périment dans les 7 jours ou qui sont déjà périmés (à retirer des rayons).
 * Rien n'est envoyé un jour sans produit concerné. Les produits sans date ne sont pas suivis.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AlertePeremptionTache {

    private static final ZoneId BAMAKO = ZoneId.of("Africa/Bamako");
    static final int JOURS_AVANT = 7;
    private static final int NOMS_MAX = 4;

    private final ProduitRepository produitRepository;
    private final NotificationPersistanceService notificationPersistance;

    @Scheduled(cron = "${alerte.peremption.cron:0 0 8 * * *}", zone = "Africa/Bamako")
    public void verifier() {
        try {
            LocalDate aujourdhui = LocalDate.now(BAMAKO);
            List<Produit> bientot = produitRepository.findByDatePeremptionBetween(aujourdhui, aujourdhui.plusDays(JOURS_AVANT))
                    .stream().filter(AlertePeremptionTache::enStock)
                    .sorted(Comparator.comparing(Produit::getDatePeremption)).toList();
            List<Produit> perimes = produitRepository.findByDatePeremptionBefore(aujourdhui)
                    .stream().filter(AlertePeremptionTache::enStock)
                    .sorted(Comparator.comparing(Produit::getDatePeremption)).toList();
            if (bientot.isEmpty() && perimes.isEmpty()) return;

            StringBuilder message = new StringBuilder();
            if (!bientot.isEmpty()) {
                message.append(bientot.size()).append(bientot.size() > 1 ? " produits périment" : " produit périme")
                        .append(" bientôt : ")
                        .append(liste(bientot, p -> p.getNom() + " (" + delai(aujourdhui, p.getDatePeremption()) + ")"));
            }
            if (!perimes.isEmpty()) {
                if (message.length() > 0) message.append(" · ");
                message.append(perimes.size()).append(perimes.size() > 1 ? " produits périmés" : " produit périmé")
                        .append(" encore en stock : ").append(liste(perimes, Produit::getNom));
            }
            String texte = message.length() > 480 ? message.substring(0, 477) + "…" : message.toString();
            notificationPersistance.creer("PEREMPTION", "Dates de péremption", texte, "/pages/produit");
        } catch (Exception e) {
            log.warn("Alerte de péremption non envoyée : {}", e.getMessage());
        }
    }

    private static boolean enStock(Produit p) {
        return p.getQuantite() != null && p.getQuantite() > 0;
    }

    private static String liste(List<Produit> produits, java.util.function.Function<Produit, String> texte) {
        String debut = produits.stream().limit(NOMS_MAX).map(texte).collect(Collectors.joining(", "));
        return produits.size() > NOMS_MAX ? debut + " et " + (produits.size() - NOMS_MAX) + " autre(s)" : debut;
    }

    private static String delai(LocalDate aujourdhui, LocalDate date) {
        long jours = ChronoUnit.DAYS.between(aujourdhui, date);
        if (jours <= 0) return "aujourd'hui";
        if (jours == 1) return "demain";
        return jours + " jours";
    }
}
