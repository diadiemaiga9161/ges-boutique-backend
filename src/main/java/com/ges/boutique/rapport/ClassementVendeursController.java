package com.ges.boutique.rapport;

import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.Map;

/** Classement des vendeurs : réservé au gérant. Période par défaut : le mois en cours. */
@RestController
@RequestMapping("/api/rapports")
@RequiredArgsConstructor
public class ClassementVendeursController {

    private final ClassementVendeursService service;

    @GetMapping("/classement-vendeurs")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, Object>> classement(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateDebut,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFin) {
        LocalDate fin = dateFin != null ? dateFin : LocalDate.now();
        LocalDate debut = dateDebut != null ? dateDebut : fin.withDayOfMonth(1);
        if (debut.isAfter(fin)) {
            LocalDate t = debut; debut = fin; fin = t;
        }
        if (debut.isBefore(fin.minusYears(2))) debut = fin.minusYears(2);
        return ResponseEntity.ok(service.classement(debut, fin));
    }
}
