package com.ges.boutique.produit;

import com.ges.boutique.feature.CleFonctionnalite;
import com.ges.boutique.feature.FeatureToggleService;
import com.ges.boutique.feature.RequireFeature;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Photos des produits (fonctionnalité IMAGES_PRODUITS, désactivée par défaut).
 * L'affichage passe par une URL publique versionnée (/api/public/produits/{id}/image?v=...)
 * pour que les balises <img> marchent sans jeton et que le navigateur garde la photo en
 * cache : une nouvelle photo change le numéro de version, donc l'URL.
 */
@RestController
@RequiredArgsConstructor
@Tag(name = "Photos produits", description = "Photo optionnelle de chaque produit")
public class ProduitImageController {

    private final ProduitImageService service;
    private final FeatureToggleService featureToggleService;

    @PostMapping(value = "/api/produits/{id}/image", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasRole('ADMIN')")
    @RequireFeature(CleFonctionnalite.IMAGES_PRODUITS)
    @Operation(summary = "Ajouter ou remplacer la photo d'un produit")
    public ResponseEntity<Map<String, Object>> envoyer(@PathVariable Long id,
                                                       @RequestParam("image") MultipartFile image) throws IOException {
        long version = service.enregistrer(id, image);
        return ResponseEntity.ok(Map.of("success", true, "imageVersion", version));
    }

    @DeleteMapping("/api/produits/{id}/image")
    @PreAuthorize("hasRole('ADMIN')")
    @RequireFeature(CleFonctionnalite.IMAGES_PRODUITS)
    @Operation(summary = "Retirer la photo d'un produit")
    public ResponseEntity<Map<String, Object>> supprimer(@PathVariable Long id) {
        service.supprimer(id);
        return ResponseEntity.ok(Map.of("success", true));
    }

    @GetMapping("/api/public/produits/{id}/image")
    @Operation(summary = "Afficher la photo d'un produit (taille=mini pour les listes)")
    public ResponseEntity<byte[]> afficher(@PathVariable Long id,
                                           @RequestParam(defaultValue = "mini") String taille) {
        if (!featureToggleService.estActive(CleFonctionnalite.IMAGES_PRODUITS)) {
            return ResponseEntity.notFound().build();
        }
        return service.lire(id, !"grande".equals(taille))
                .map(octets -> ResponseEntity.ok()
                        .contentType(MediaType.IMAGE_JPEG)
                        .cacheControl(CacheControl.maxAge(365, TimeUnit.DAYS).cachePublic().immutable())
                        .body(octets))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }
}
