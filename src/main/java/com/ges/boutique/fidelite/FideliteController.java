package com.ges.boutique.fidelite;

import com.ges.boutique.exception.MessageErreurUtil;
import com.ges.boutique.feature.CleFonctionnalite;
import com.ges.boutique.feature.RequireFeature;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Programme de fidélité — voir CleFonctionnalite.PROGRAMME_FIDELITE (désactivable par
 * le super admin, comme toute nouvelle fonctionnalité de ce projet). L'utilisation des
 * points pendant une vente se fait en 2 temps côté appelant : appliquer la réduction
 * correspondante via le mécanisme de remise déjà existant (VenteRequest.remiseGlobale),
 * PUIS appeler POST .../utiliser une fois la vente créée pour débiter le solde — ceci ne
 * touche jamais au calcul du montant d'une vente.
 */
@RestController
@RequestMapping("/api/fidelite")
@RequiredArgsConstructor
@RequireFeature(CleFonctionnalite.PROGRAMME_FIDELITE)
@Tag(name = "Fidélité", description = "Programme de points fidélité client")
public class FideliteController {

    private final FideliteService fideliteService;

    @GetMapping("/parametres")
    @PreAuthorize("hasAnyRole('ADMIN', 'VENDEUR')")
    @Operation(summary = "Obtenir les taux de conversion configurés pour la boutique")
    public FideliteParametresDto obtenirParametres() {
        return fideliteService.obtenirParametres();
    }

    @PutMapping("/parametres")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Modifier les taux de conversion — n'importe quel admin de la boutique")
    public FideliteParametresDto definirParametres(@RequestBody FideliteParametresDto parametres) {
        fideliteService.definirParametres(parametres);
        return fideliteService.obtenirParametres();
    }

    @GetMapping("/clients/{clientId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'VENDEUR')")
    @Operation(summary = "Obtenir le solde de points d'un client et sa valeur en FCFA")
    public FideliteSoldeDto obtenirSolde(@PathVariable Long clientId) {
        return fideliteService.obtenirSolde(clientId);
    }

    @GetMapping("/clients/{clientId}/mouvements")
    @PreAuthorize("hasAnyRole('ADMIN', 'VENDEUR')")
    @Operation(summary = "Historique des mouvements de points d'un client")
    public List<MouvementFidelite> obtenirHistorique(@PathVariable Long clientId) {
        return fideliteService.obtenirHistorique(clientId);
    }

    @PostMapping("/clients/{clientId}/utiliser")
    @PreAuthorize("hasAnyRole('ADMIN', 'VENDEUR')")
    @Operation(summary = "Débiter des points utilisés comme réduction sur une vente")
    public ResponseEntity<Map<String, Object>> utiliserPoints(@PathVariable Long clientId, @RequestBody Map<String, Object> body) {
        Map<String, Object> resp = new HashMap<>();
        try {
            int points = Integer.parseInt(body.get("points").toString());
            Long venteId = body.get("venteId") != null ? Long.valueOf(body.get("venteId").toString()) : null;
            fideliteService.utiliserPoints(clientId, points, venteId, "Utilisé sur une vente");
            resp.put("success", true);
            resp.put("solde", fideliteService.obtenirSolde(clientId));
            return ResponseEntity.ok(resp);
        } catch (IllegalArgumentException e) {
            resp.put("success", false);
            resp.put("message", MessageErreurUtil.messageClient(e));
            return ResponseEntity.badRequest().body(resp);
        }
    }

    @PatchMapping("/clients/{clientId}/ajuster")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Ajustement manuel du solde de points — réservé à l'admin")
    public ResponseEntity<Map<String, Object>> ajusterManuel(@PathVariable Long clientId, @RequestBody Map<String, Object> body) {
        Map<String, Object> resp = new HashMap<>();
        int delta = Integer.parseInt(body.get("delta").toString());
        String motif = body.get("motif") != null ? body.get("motif").toString() : null;
        fideliteService.ajusterManuel(clientId, delta, motif);
        resp.put("success", true);
        resp.put("solde", fideliteService.obtenirSolde(clientId));
        return ResponseEntity.ok(resp);
    }
}
