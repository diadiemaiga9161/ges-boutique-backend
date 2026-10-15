package com.ges.boutique.produit;

import com.ges.boutique.exception.RessourceIntrouvableException;
import com.ges.boutique.feature.CleFonctionnalite;
import com.ges.boutique.feature.RequireFeature;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Surnoms d'un produit pour la vente à la voix : seul le gérant les modifie. La
 * reconnaissance vocale et la recherche du produit se font dans l'appli ; le serveur
 * ne fait que garder la liste.
 */
@RestController
@RequestMapping("/api/produits")
@RequiredArgsConstructor
@RequireFeature(CleFonctionnalite.VENTE_VOIX)
@Tag(name = "Vente à la voix", description = "Surnoms des produits pour la reconnaissance vocale")
public class SurnomsProduitController {

    private static final int LONGUEUR_MAX = 500;

    private final ProduitRepository produitRepository;

    @PutMapping("/{produitId}/surnoms")
    @PreAuthorize("hasRole('ADMIN')")
    @Transactional
    @CacheEvict(value = "produits", allEntries = true)
    @Operation(summary = "Enregistrer les surnoms d'un produit (séparés par des virgules)")
    public ResponseEntity<Map<String, Object>> enregistrer(@PathVariable Long produitId,
                                                           @RequestBody Map<String, String> corps) {
        Produit produit = produitRepository.findById(produitId)
                .orElseThrow(() -> new RessourceIntrouvableException("Produit introuvable : " + produitId));
        String surnoms = nettoyer(corps.get("surnoms"));
        if (surnoms != null && surnoms.length() > LONGUEUR_MAX) {
            throw new IllegalArgumentException("Trop de surnoms (" + LONGUEUR_MAX + " caractères au maximum).");
        }
        produit.setSurnoms(surnoms);
        produitRepository.save(produit);
        return ResponseEntity.ok(Map.of("success", true, "produitId", produitId, "surnoms", surnoms == null ? "" : surnoms));
    }

    /** Surnoms sans espaces superflus ni doublons, séparés par « , » ; null si vide. */
    static String nettoyer(String brut) {
        if (brut == null) return null;
        Set<String> uniques = Arrays.stream(brut.split("[,;\\n]"))
                .map(s -> s.trim().replaceAll("\\s+", " "))
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toCollection(LinkedHashSet::new));
        return uniques.isEmpty() ? null : String.join(", ", uniques);
    }
}
