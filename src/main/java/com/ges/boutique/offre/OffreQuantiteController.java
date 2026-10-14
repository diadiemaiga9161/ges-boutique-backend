package com.ges.boutique.offre;

import com.ges.boutique.feature.CleFonctionnalite;
import com.ges.boutique.feature.RequireFeature;
import com.ges.boutique.utilisateur.Utilisateur;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Offres par quantité — activées par le super admin (OFFRES_QUANTITE, désactivé par défaut).
 * Seul le gérant crée, modifie ou supprime une offre ; le vendeur lit seulement les offres
 * en cours, pour que sa caisse affiche le même total que le serveur.
 */
@RestController
@RequestMapping("/api/offres-quantite")
@RequiredArgsConstructor
@RequireFeature(CleFonctionnalite.OFFRES_QUANTITE)
@Tag(name = "Offres par quantité", description = "Ex : 2 pour 75 F, appliqué automatiquement à la vente")
public class OffreQuantiteController {

    private final OffreQuantiteService service;

    @GetMapping("/en-cours")
    @PreAuthorize("hasAnyRole('ADMIN', 'VENDEUR')")
    @Operation(summary = "Offres valables aujourd'hui (pour le calcul du panier)")
    public ResponseEntity<List<Map<String, Object>>> enCours() {
        return ResponseEntity.ok(service.enCours());
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<Map<String, Object>>> lister() {
        return ResponseEntity.ok(service.lister());
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, Object>> creer(@AuthenticationPrincipal Utilisateur moi,
                                                     @RequestBody Map<String, Object> corps) {
        return ResponseEntity.ok(service.enregistrer(null, corps, moi.getNomComplet()));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, Object>> modifier(@AuthenticationPrincipal Utilisateur moi, @PathVariable Long id,
                                                        @RequestBody Map<String, Object> corps) {
        return ResponseEntity.ok(service.enregistrer(id, corps, moi.getNomComplet()));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> supprimer(@PathVariable Long id) {
        service.supprimer(id);
        return ResponseEntity.noContent().build();
    }
}
