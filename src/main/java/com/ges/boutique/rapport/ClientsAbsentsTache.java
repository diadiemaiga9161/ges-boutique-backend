package com.ges.boutique.rapport;

import com.ges.boutique.feature.CleFonctionnalite;
import com.ges.boutique.feature.FeatureToggleService;
import com.ges.boutique.notification.NotificationPersistanceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Chaque lundi à 9 h (Bamako) : rappelle au gérant les bons clients qu'on n'a pas revus depuis
 * deux semaines. Rien n'est envoyé si la fonctionnalité est coupée ou s'il n'y en a aucun.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ClientsAbsentsTache {

    private final ClientsAbsentsService service;
    private final FeatureToggleService featureToggleService;
    private final NotificationPersistanceService notificationPersistance;

    @Scheduled(cron = "0 0 9 * * MON", zone = "Africa/Bamako")
    public void rappeler() {
        try {
            if (!featureToggleService.estActive(CleFonctionnalite.CLIENTS_ABSENTS)) return;
            Object total = service.lister(ClientsAbsentsService.JOURS_DEFAUT, ClientsAbsentsService.MIN_ACHATS_DEFAUT,
                    ClientsAbsentsService.PERIODE_DEFAUT).get("total");
            int nb = total instanceof Number n ? n.intValue() : 0;
            if (nb == 0) return;
            String message = nb + (nb > 1 ? " bons clients ne sont pas venus" : " bon client n'est pas venu")
                    + " depuis 2 semaines. Appelez-les avant de les perdre.";
            notificationPersistance.creer("CLIENTS_ABSENTS", "Clients à rappeler", message, "/clients-absents");
        } catch (Exception e) {
            log.warn("Rappel des clients absents non envoyé : {}", e.getMessage());
        }
    }
}
