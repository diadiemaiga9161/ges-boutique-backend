package com.ges.boutique.inventaire.comptage;

import com.ges.boutique.utilisateur.Utilisateur;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Comptage d'inventaire. Réservé à qui peut faire les mouvements de stock : le gérant, ou un
 * vendeur dont le rôle a « Inventaire » sur Modifier/Tout (même règle que les entrées/sorties).
 * Sous /api/inventaire : contrôlé aussi par PermissionInterceptor (partie STOCK). Pas de
 * DELETE (qui demanderait « Supprimer ») : retirer un produit = quantité null.
 */
@RestController
@RequestMapping("/api/inventaire/comptages")
@RequiredArgsConstructor
public class ComptageInventaireController {

    private static final String AUTORISE =
            "hasRole('ADMIN') or (hasRole('VENDEUR') and @roleBoutiqueService.vendeurPeut(authentication.principal, 'STOCK_GERER'))";

    private final ComptageInventaireService service;

    @GetMapping("/en-cours")
    @PreAuthorize(AUTORISE)
    public ResponseEntity<Map<String, Object>> enCours() {
        return service.enCours().map(ResponseEntity::ok).orElseGet(() -> ResponseEntity.noContent().build());
    }

    @GetMapping
    @PreAuthorize(AUTORISE)
    public List<Map<String, Object>> historique() {
        return service.historique();
    }

    @GetMapping("/{id}")
    @PreAuthorize(AUTORISE)
    public Map<String, Object> obtenir(@PathVariable Long id) {
        return service.obtenir(id);
    }

    /** Body : {"categorieId": 3} ou {} pour tous les produits. */
    @PostMapping
    @PreAuthorize(AUTORISE)
    public Map<String, Object> demarrer(@RequestBody(required = false) Map<String, Long> body,
                                        @AuthenticationPrincipal Utilisateur moi) {
        return service.demarrer(body == null ? null : body.get("categorieId"), moi);
    }

    /** Body : {"produitId": 5, "quantite": 42} ; quantite null = retirer le produit. */
    @PutMapping("/{id}/lignes")
    @PreAuthorize(AUTORISE)
    public Map<String, Object> compter(@PathVariable Long id, @RequestBody Map<String, Object> body,
                                       @AuthenticationPrincipal Utilisateur moi) {
        Long produitId = Long.valueOf(body.get("produitId").toString());
        Object q = body.get("quantite");
        Integer quantite = q == null ? null : (int) Math.round(Double.parseDouble(q.toString()));
        return service.compter(id, produitId, quantite, moi);
    }

    @PostMapping("/{id}/valider")
    @PreAuthorize(AUTORISE)
    public Map<String, Object> valider(@PathVariable Long id, @AuthenticationPrincipal Utilisateur moi) {
        return service.valider(id, moi);
    }

    @PostMapping("/{id}/abandonner")
    @PreAuthorize(AUTORISE)
    public Map<String, Object> abandonner(@PathVariable Long id, @AuthenticationPrincipal Utilisateur moi) {
        return service.abandonner(id, moi);
    }
}
