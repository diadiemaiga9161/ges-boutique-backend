package com.ges.boutique.rapport;

import com.ges.boutique.feature.CleFonctionnalite;
import com.ges.boutique.feature.RequireFeature;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/** Bons clients qui ne viennent plus (CLIENTS_ABSENTS, désactivé par défaut) : réservé au gérant. */
@RestController
@RequestMapping("/api/rapports")
@RequiredArgsConstructor
@RequireFeature(CleFonctionnalite.CLIENTS_ABSENTS)
public class ClientsAbsentsController {

    private final ClientsAbsentsService service;

    @GetMapping("/clients-absents")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, Object>> clientsAbsents(
            @RequestParam(defaultValue = "" + ClientsAbsentsService.JOURS_DEFAUT) int jours,
            @RequestParam(defaultValue = "" + ClientsAbsentsService.MIN_ACHATS_DEFAUT) int minAchats,
            @RequestParam(defaultValue = "" + ClientsAbsentsService.PERIODE_DEFAUT) int periodeJours) {
        int j = Math.max(3, Math.min(jours, 365));
        int min = Math.max(2, Math.min(minAchats, 100));
        int periode = Math.max(j + 7, Math.min(periodeJours, 730));
        return ResponseEntity.ok(service.lister(j, min, periode));
    }
}
