package com.ges.boutique.planning;

import com.ges.boutique.feature.CleFonctionnalite;
import com.ges.boutique.feature.RequireFeature;
import com.ges.boutique.utilisateur.Utilisateur;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;

/** Planning de l'équipe (PLANNING, désactivé par défaut) : le gérant le remplit, chacun voit le sien. */
@RestController
@RequestMapping("/api/planning")
@RequiredArgsConstructor
@RequireFeature(CleFonctionnalite.PLANNING)
public class PlanningController {

    private static final ZoneId ZONE = ZoneId.of("Africa/Bamako");

    private final PlanningService service;

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, Object>> semaine(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate debut) {
        return ResponseEntity.ok(service.semaine(debut == null ? LocalDate.now(ZONE) : debut));
    }

    @PutMapping
    @PreAuthorize("hasRole('ADMIN')")
    @SuppressWarnings("unchecked")
    public ResponseEntity<Map<String, Object>> enregistrer(@RequestBody Map<String, Object> corps) {
        return ResponseEntity.ok(service.enregistrer((List<Map<String, Object>>) corps.get("cellules")));
    }

    @PostMapping("/copier")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, Object>> copier(@RequestBody Map<String, Object> corps) {
        LocalDate depuis = LocalDate.parse(String.valueOf(corps.get("depuis")));
        LocalDate vers = LocalDate.parse(String.valueOf(corps.get("vers")));
        boolean ecraser = Boolean.parseBoolean(String.valueOf(corps.get("ecraser")));
        return ResponseEntity.ok(service.copierSemaine(depuis, vers, ecraser));
    }

    @GetMapping("/moi")
    @PreAuthorize("hasAnyRole('ADMIN', 'VENDEUR')")
    public ResponseEntity<Map<String, Object>> moi(@AuthenticationPrincipal Utilisateur moi,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate debut) {
        return ResponseEntity.ok(service.maSemaine(moi, debut == null ? LocalDate.now(ZONE) : debut));
    }
}
