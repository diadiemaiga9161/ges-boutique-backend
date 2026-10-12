package com.ges.boutique.pointage;

import com.ges.boutique.feature.CleFonctionnalite;
import com.ges.boutique.feature.RequireFeature;
import com.ges.boutique.utilisateur.Utilisateur;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.util.Map;

/**
 * Pointage des employés — activé par le super admin (POINTAGE, désactivé par défaut).
 * L'employé scanne le code QR affiché dans la boutique ; le gérant voit la journée,
 * le résumé du mois, et pointe à la main ceux qui n'ont pas de compte.
 */
@RestController
@RequestMapping("/api/pointage")
@RequiredArgsConstructor
@RequireFeature(CleFonctionnalite.POINTAGE)
@Tag(name = "Pointage", description = "Arrivées et départs des employés")
public class PointageController {

    private final PointageService service;

    // ---------------------------------------------------------------- employé

    @PostMapping("/scanner")
    @PreAuthorize("hasAnyRole('ADMIN', 'VENDEUR')")
    @Operation(summary = "Pointer (arrivée puis départ) avec le code QR de la boutique")
    public ResponseEntity<Map<String, Object>> scanner(@AuthenticationPrincipal Utilisateur moi,
                                                       @RequestBody Map<String, String> corps) {
        return ResponseEntity.ok(service.scanner(moi, corps == null ? null : corps.get("code")));
    }

    @GetMapping("/moi")
    @PreAuthorize("hasAnyRole('ADMIN', 'VENDEUR')")
    @Operation(summary = "Mes pointages du mois")
    public ResponseEntity<Map<String, Object>> moi(@AuthenticationPrincipal Utilisateur moi,
                                                   @RequestParam(required = false) String mois) {
        return ResponseEntity.ok(service.mesPointages(moi, lireMois(mois)));
    }

    // ---------------------------------------------------------------- gérant

    @GetMapping("/code")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Code QR à afficher dans la boutique (change toutes les 30 secondes)")
    public ResponseEntity<Map<String, Object>> code() {
        return ResponseEntity.ok(service.codeActuel());
    }

    @GetMapping("/jour")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, Object>> jour(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return ResponseEntity.ok(service.jour(date == null ? LocalDate.now(PointageService.ZONE) : date));
    }

    @GetMapping("/resume")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, Object>> resume(@RequestParam(required = false) String mois) {
        return ResponseEntity.ok(service.resumeMois(lireMois(mois)));
    }

    @PostMapping("/manuel")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Pointer ou corriger à la main un employé")
    public ResponseEntity<Map<String, Object>> manuel(@AuthenticationPrincipal Utilisateur moi,
                                                      @RequestBody Map<String, Object> corps) {
        return ResponseEntity.ok(service.enregistrerManuel(corps, moi));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> supprimer(@PathVariable Long id) {
        service.supprimer(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/parametres")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, Object>> parametres() {
        return ResponseEntity.ok(service.parametres());
    }

    @PutMapping("/parametres")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, Object>> modifierParametres(@RequestBody Map<String, Object> corps) {
        return ResponseEntity.ok(service.modifierParametres(corps));
    }

    private static YearMonth lireMois(String mois) {
        if (mois == null || mois.isBlank()) return YearMonth.now(PointageService.ZONE);
        try {
            return YearMonth.parse(mois.trim());
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("Mois invalide (format AAAA-MM).");
        }
    }
}
