package com.ges.boutique.produit;

import com.ges.boutique.feature.CleFonctionnalite;
import com.ges.boutique.feature.RequireFeature;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/** Vente à la mesure (VENTE_A_LA_MESURE, désactivée par défaut) : bascule d'un produit existant. */
@RestController
@RequestMapping("/api/produits")
@RequiredArgsConstructor
@Tag(name = "Vente à la mesure", description = "Produits vendus au kilo, au litre ou au mètre")
public class ModeMesureController {

    private final ModeMesureService modeMesureService;
    private final ProduitService produitService;

    @PutMapping("/{id}/mode-mesure")
    @PreAuthorize("hasRole('ADMIN')")
    @RequireFeature(CleFonctionnalite.VENTE_A_LA_MESURE)
    @Operation(summary = "Vendre un produit au kg/L/m (ou le remettre à l'unité) en convertissant son stock")
    public ResponseEntity<ProduitDto> changer(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        Object brut = body.get("modeMesure");
        String valeur = brut == null ? null : brut.toString();
        ModeMesure mode;
        try {
            mode = valeur == null || valeur.isBlank() ? null : ModeMesure.valueOf(valeur);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Unité de mesure inconnue : " + valeur);
        }
        // contenance : nombre de kg (L, m) dans une unité du produit (ex : 50 pour un sac de 50 kg).
        Double contenance = null;
        Object c = body.get("contenance");
        if (c != null && !c.toString().isBlank()) {
            try {
                contenance = Double.valueOf(c.toString().replace(',', '.'));
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("Contenance invalide : " + c);
            }
        }
        return ResponseEntity.ok(produitService.convertirEnDto(modeMesureService.changer(id, mode, contenance)));
    }
}
