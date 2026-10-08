package com.ges.boutique.commission;

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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Primes sur produits — activées par le super admin (PRIMES_PRODUITS, désactivé par
 * défaut), réglées par le gérant. Le vendeur consulte ses propres primes via /mes-primes.
 */
@RestController
@RequestMapping("/api/commissions")
@RequiredArgsConstructor
@RequireFeature(CleFonctionnalite.PRIMES_PRODUITS)
@Tag(name = "Primes produits", description = "Primes des vendeurs sur certains produits")
public class CommissionController {

    private final CommissionService service;

    // ---------------------------------------------------------------- vendeur

    @GetMapping("/mes-primes")
    @PreAuthorize("hasAnyRole('ADMIN', 'VENDEUR')")
    @Operation(summary = "Mes primes sur la période + règles en cours qui me concernent")
    public ResponseEntity<Map<String, Object>> mesPrimes(
            @AuthenticationPrincipal Utilisateur moi,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate debut,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fin) {
        LocalDate[] p = periode(debut, fin);
        Map<String, Object> calcul = service.calculer(p[0], p[1], moi.getId());
        Map<String, Object> res = new LinkedHashMap<>();
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> vendeurs = (List<Map<String, Object>>) calcul.get("vendeurs");
        res.put("debut", p[0]);
        res.put("fin", p[1]);
        res.put("resume", vendeurs.isEmpty() ? null : vendeurs.get(0));
        res.put("reglesEnCours", service.reglesEnCoursPour(moi.getId()));
        res.put("paiements", service.listerPaiements(moi.getId()));
        return ResponseEntity.ok(res);
    }

    // ---------------------------------------------------------------- gérant

    @GetMapping("/parametres")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, Object>> parametres() {
        return ResponseEntity.ok(service.parametres());
    }

    @PutMapping("/parametres")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Choisir les types de primes utilisés par la boutique")
    public ResponseEntity<Map<String, Object>> definirParametres(@RequestBody Map<String, List<String>> body) {
        return ResponseEntity.ok(service.definirTypesActifs(body.get("types")));
    }

    @GetMapping("/vendeurs")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<Map<String, Object>>> vendeurs() {
        return ResponseEntity.ok(service.vendeursProposes());
    }

    @GetMapping("/regles")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<Map<String, Object>>> regles() {
        return ResponseEntity.ok(service.listerRegles());
    }

    @PostMapping("/regles")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Créer une condition de prime")
    public ResponseEntity<Map<String, Object>> creer(@RequestBody RegleCommissionRequest request) {
        return ResponseEntity.ok(service.creerRegle(request));
    }

    @PutMapping("/regles/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, Object>> modifier(@PathVariable Long id, @RequestBody RegleCommissionRequest request) {
        return ResponseEntity.ok(service.modifierRegle(id, request));
    }

    @PatchMapping("/regles/{id}/arreter")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Arrêter une règle aujourd'hui (les primes déjà gagnées restent)")
    public ResponseEntity<Map<String, Object>> arreter(@PathVariable Long id) {
        return ResponseEntity.ok(service.arreterRegle(id));
    }

    @DeleteMapping("/regles/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> supprimer(@PathVariable Long id) {
        service.supprimerRegle(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/primes")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Primes de tous les vendeurs sur la période")
    public ResponseEntity<Map<String, Object>> primes(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate debut,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fin) {
        LocalDate[] p = periode(debut, fin);
        return ResponseEntity.ok(service.calculer(p[0], p[1], null));
    }

    @GetMapping("/paiements")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<Map<String, Object>>> paiements(@RequestParam(required = false) Long vendeurId) {
        return ResponseEntity.ok(service.listerPaiements(vendeurId));
    }

    @PostMapping("/paiements")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Enregistrer une prime versée à un vendeur")
    public ResponseEntity<Map<String, Object>> payer(@RequestBody Map<String, Object> body,
                                                     @AuthenticationPrincipal Utilisateur moi) {
        Long vendeurId = body.get("vendeurId") != null ? ((Number) body.get("vendeurId")).longValue() : null;
        Double montant = body.get("montant") != null ? ((Number) body.get("montant")).doubleValue() : null;
        String note = body.get("note") != null ? body.get("note").toString() : null;
        service.enregistrerPaiement(vendeurId, montant, note, moi);
        return ResponseEntity.ok(Map.of("success", true));
    }

    @DeleteMapping("/paiements/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> supprimerPaiement(@PathVariable Long id) {
        service.supprimerPaiement(id);
        return ResponseEntity.noContent().build();
    }

    /** Par défaut : le mois en cours. */
    private static LocalDate[] periode(LocalDate debut, LocalDate fin) {
        LocalDate auj = LocalDate.now();
        LocalDate d = debut != null ? debut : auj.withDayOfMonth(1);
        LocalDate f = fin != null ? fin : auj.withDayOfMonth(auj.lengthOfMonth());
        return new LocalDate[]{d, f};
    }
}
