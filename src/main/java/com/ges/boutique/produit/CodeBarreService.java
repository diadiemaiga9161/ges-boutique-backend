package com.ges.boutique.produit;

import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

/**
 * Codes-barres pour les produits qui n'en ont pas, à imprimer sur les étiquettes.
 * Format EAN-13 (celui des supermarchés), dans la série 20 à 29 réservée par GS1 à l'usage
 * interne des magasins : jamais le code d'un vrai produit du commerce. Le code vient du numéro
 * du produit (unique) ; un code existant n'est jamais modifié.
 */
@Service
@RequiredArgsConstructor
public class CodeBarreService {

    private final ProduitRepository produitRepository;

    /** produitIds vide ou null = tous les produits sans code. */
    @Transactional
    @CacheEvict(value = "produits", allEntries = true)
    public List<Map<String, Object>> genererManquants(Collection<Long> produitIds) {
        List<Produit> produits = (produitIds == null || produitIds.isEmpty())
                ? produitRepository.findAll()
                : produitRepository.findAllById(produitIds);
        List<Map<String, Object>> crees = new ArrayList<>();
        for (Produit p : produits) {
            if (p.getCodeBarre() != null && !p.getCodeBarre().isBlank()) continue;
            String code = codeLibre(p.getId());
            if (code == null) continue;
            p.setCodeBarre(code);
            produitRepository.save(p);
            crees.add(Map.of("id", p.getId(), "nom", p.getNom(), "codeBarre", code));
        }
        return crees;
    }

    private String codeLibre(Long id) {
        String corps = String.format("%010d", id % 10_000_000_000L);
        for (int prefixe = 20; prefixe <= 29; prefixe++) {
            String code = ean13(prefixe + corps);
            if (produitRepository.findByCodeBarre(code).isEmpty()) return code;
        }
        return null;
    }

    /** Ajoute le chiffre de contrôle EAN-13 à 12 chiffres. */
    static String ean13(String douzeChiffres) {
        int somme = 0;
        for (int i = 0; i < 12; i++) {
            int c = douzeChiffres.charAt(i) - '0';
            somme += (i % 2 == 0) ? c : c * 3;
        }
        return douzeChiffres + ((10 - somme % 10) % 10);
    }
}
