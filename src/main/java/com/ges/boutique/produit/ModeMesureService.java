package com.ges.boutique.produit;

import com.ges.boutique.exception.RessourceIntrouvableException;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Passe un produit existant à la vente à la mesure (ou le remet à l'unité) en convertissant
 * son stock, son seuil d'alerte et ses prix, pour que rien ne devienne faux au passage :
 * 50 (kg) en stock -> 50 000 g, 600 F/kg -> 0,6 F/g. Les produits à niveaux (carton,
 * paquet...) ou à unités de vente en sont exclus : les deux mélangés compliqueraient la vente.
 */
@Service
@RequiredArgsConstructor
public class ModeMesureService {

    private final ProduitRepository produitRepository;
    private final UniteVenteRepository uniteVenteRepository;

    @Transactional
    @CacheEvict(value = "produits", allEntries = true)
    public Produit changer(Long produitId, ModeMesure nouveau) {
        return changer(produitId, nouveau, 1.0);
    }

    /**
     * contenance = combien d'unités de mesure (kg, L, m) dans UNE unité du produit à l'unité.
     * Ex : un sac de 50 kg -> 50 ; 10 sacs en stock deviennent 500 kg, et 30 000 F le sac
     * deviennent 600 F le kg. Pour un produit déjà compté au kg (sucre "1 kg"), contenance = 1.
     */
    @Transactional
    @CacheEvict(value = "produits", allEntries = true)
    public Produit changer(Long produitId, ModeMesure nouveau, Double contenance) {
        double c = contenance == null ? 1.0 : contenance;
        if (!(c > 0) || c > 1_000_000) {
            throw new IllegalArgumentException("Indiquez combien de kg (ou L, m) contient une unité du produit.");
        }
        Produit p = produitRepository.findById(produitId)
                .orElseThrow(() -> new RessourceIntrouvableException("Produit introuvable"));
        ModeMesure ancien = p.getModeMesure();
        if (ancien == nouveau) return p;

        if (nouveau != null) {
            if (p.getNiveaux() != null && !p.getNiveaux().isEmpty()) {
                throw new IllegalStateException("Ce produit a des niveaux (carton, paquet...). "
                        + "Retirez-les avant de le vendre au " + nouveau.getUnite() + ".");
            }
            if (!uniteVenteRepository.findByProduitIdOrderByOrdreAsc(produitId).isEmpty()) {
                throw new IllegalStateException("Ce produit a des unités de vente. "
                        + "Retirez-les avant de le vendre au " + nouveau.getUnite() + ".");
            }
        }

        // Retour à l'unité d'abord (si besoin), puis passage à la nouvelle mesure.
        if (ancien != null) {
            // Retour à l'unité : une unité = c kg (ex : on revend en sacs de 50 kg).
            double f = ancien.getFacteur() * (nouveau == null ? c : 1.0);
            p.setQuantite(arrondiDiv(p.getQuantite(), f));
            p.setSeuilAlerte(arrondiDiv(p.getSeuilAlerte(), f));
            p.setPrixVente(fois(p.getPrixVente(), f));
            p.setPrixAchat(fois(p.getPrixAchat(), f));
            p.setUniteBase("Unité");
            p.setModeMesure(null);
        }
        if (nouveau != null) {
            // Une unité actuelle = c unités de mesure = c x facteur petites unités (g).
            double f = nouveau.getFacteur() * (ancien == null ? c : 1.0);
            p.setQuantite(p.getQuantite() == null ? 0 : (int) Math.round(p.getQuantite() * f));
            p.setSeuilAlerte(p.getSeuilAlerte() == null ? null : (int) Math.round(p.getSeuilAlerte() * f));
            p.setPrixVente(div(p.getPrixVente(), f));
            p.setPrixAchat(div(p.getPrixAchat(), f));
            p.setUniteBase(nouveau.getPetiteUnite());
            p.setModeMesure(nouveau);
        }
        return produitRepository.save(p);
    }

    private static Integer arrondiDiv(Integer v, double f) {
        return v == null ? null : (int) Math.round(v / (double) f);
    }

    private static Double div(Double v, double f) {
        return v == null ? null : v / f;
    }

    private static Double fois(Double v, double f) {
        return v == null ? null : v * f;
    }
}
