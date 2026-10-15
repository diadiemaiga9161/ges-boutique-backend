package com.ges.boutique.devis;

import com.ges.boutique.feature.CleFonctionnalite;
import com.ges.boutique.feature.RequireFeature;
import com.ges.boutique.utilisateur.Utilisateur;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/** Devis pro forma (DEVIS, désactivé par défaut) : réservé au gérant. */
@RestController
@RequestMapping("/api/devis")
@RequiredArgsConstructor
@RequireFeature(CleFonctionnalite.DEVIS)
@PreAuthorize("hasRole('ADMIN')")
public class DevisController {

    private final DevisService service;

    @GetMapping
    public ResponseEntity<List<Map<String, Object>>> lister() {
        return ResponseEntity.ok(service.lister());
    }

    @GetMapping("/{id}")
    public ResponseEntity<Map<String, Object>> obtenir(@PathVariable Long id) {
        return ResponseEntity.ok(service.obtenir(id));
    }

    @PostMapping
    public ResponseEntity<Map<String, Object>> creer(@AuthenticationPrincipal Utilisateur moi, @RequestBody Map<String, Object> corps) {
        return ResponseEntity.ok(service.enregistrer(null, corps, moi));
    }

    @PutMapping("/{id}")
    public ResponseEntity<Map<String, Object>> modifier(@AuthenticationPrincipal Utilisateur moi, @PathVariable Long id,
                                                        @RequestBody Map<String, Object> corps) {
        return ResponseEntity.ok(service.enregistrer(id, corps, moi));
    }

    @PostMapping("/{id}/refuser")
    public ResponseEntity<Map<String, Object>> refuser(@PathVariable Long id) {
        return ResponseEntity.ok(service.refuser(id));
    }

    @PostMapping("/{id}/prolonger")
    public ResponseEntity<Map<String, Object>> prolonger(@PathVariable Long id, @RequestBody Map<String, Object> corps) {
        int jours = corps.get("jours") == null ? 30 : (int) Double.parseDouble(String.valueOf(corps.get("jours")));
        return ResponseEntity.ok(service.prolonger(id, jours));
    }

    @PostMapping("/{id}/transformer")
    public ResponseEntity<Map<String, Object>> transformer(@AuthenticationPrincipal Utilisateur moi, @PathVariable Long id,
                                                           @RequestBody Map<String, Object> corps) {
        return ResponseEntity.ok(service.transformer(id, corps, moi));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> supprimer(@PathVariable Long id) {
        service.supprimer(id);
        return ResponseEntity.noContent().build();
    }
}
