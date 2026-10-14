package com.ges.boutique.offre;

import com.ges.boutique.feature.CleFonctionnalite;
import com.ges.boutique.feature.FeatureToggleService;
import com.ges.boutique.produit.Produit;
import com.ges.boutique.produit.ProduitRepository;
import com.ges.boutique.vente.LigneVente;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Offres par quantité (« 2 pour 75 F ») : réglées par le gérant seul, appliquées
 * automatiquement par le serveur à chaque vente, sans que le vendeur puisse les retirer.
 */
@Service
@RequiredArgsConstructor
public class OffreQuantiteService {

    static final ZoneId ZONE = ZoneId.of("Africa/Bamako");
    private static final int PALIERS_MAX = 5;

    private final OffreQuantiteRepository repository;
    private final ProduitRepository produitRepository;
    private final FeatureToggleService featureToggleService;

    // ================================================================= vente

    /**
     * Applique l'offre à une ligne de vente qui vient d'être préparée. Ne touche pas aux lignes
     * dont le prix a été changé à la main, qui ont une remise, qui sont vendues par carton /
     * unité de vente, ou au poids : l'offre porte sur l'article au prix normal.
     */
    public void appliquer(LigneVente ligne) {
        ligne.setRemiseOffre(0.0);
        ligne.setOffreLibelle(null);
        ligne.setOffrePaliers(null);
        Produit produit = ligne.getProduit();
        if (produit == null || produit.getId() == null || produit.getModeMesure() != null) return;
        if (!featureToggleService.estActive(CleFonctionnalite.OFFRES_QUANTITE)) return;
        if (ligne.getNiveauId() != null || (ligne.getNiveauFacteur() != null && ligne.getNiveauFacteur() > 1)) return;
        if (positif(ligne.getRemisePourcentage()) || positif(ligne.getRemiseMontant())) return;
        Double prixNormal = produit.getPrixVente();
        if (prixNormal == null || prixNormal <= 0 || ligne.getPrixUnitaire() == null
                || Math.abs(ligne.getPrixUnitaire() - prixNormal) > 0.001) return;
        Integer q = ligne.getQuantite();
        if (q == null || q < 2) return;

        List<CalculOffre.Palier> paliers = paliersValables(produit.getId());
        if (paliers.isEmpty()) return;
        CalculOffre.Resultat r = CalculOffre.calculer(q, prixNormal, paliers);
        if (r.remise() <= 0) return;
        ligne.appliquerRemiseOffre(r.remise(), r.libelle(), CalculOffre.versTexte(paliers));
    }

    /**
     * Montant à rendre quand on retourne {@code k} articles d'une ligne vendue avec une offre,
     * dont {@code dejaRendus} ont déjà été retournés : ce que le client a payé pour ce qu'il
     * avait, moins le prix de ce qu'il garde (avec les offres du jour de la vente).
     * Ex. 2 pour 75 F, il rend 1 bonbon : 75 − 50 = 25 F.
     */
    public static double montantRetour(LigneVente ligne, int k, int dejaRendus) {
        int q = ligne.getQuantite() == null ? 0 : ligne.getQuantite();
        int avant = Math.max(0, q - dejaRendus);
        int apres = Math.max(0, avant - k);
        List<CalculOffre.Palier> paliers = CalculOffre.depuisTexte(ligne.getOffrePaliers());
        double unitaire = ligne.getPrixApresRemise() != null ? ligne.getPrixApresRemise() : ligne.getPrixUnitaire();
        double paye = CalculOffre.calculer(avant, unitaire, paliers).prixTotal();
        double garde = CalculOffre.calculer(apres, unitaire, paliers).prixTotal();
        return CalculOffre.arrondir(Math.max(0, paye - garde));
    }

    private List<CalculOffre.Palier> paliersValables(Long produitId) {
        LocalDate jour = LocalDate.now(ZONE);
        return repository.findByProduitIdAndActiveTrue(produitId).stream()
                .filter(o -> o.estValable(jour))
                .sorted(Comparator.comparingInt(OffreQuantite::getQuantite))
                .map(o -> new CalculOffre.Palier(o.getQuantite(), o.getPrix()))
                .toList();
    }

    // ================================================================= lecture

    /** Offres en cours, pour que la caisse affiche le bon total avant d'envoyer la vente. */
    public List<Map<String, Object>> enCours() {
        if (!featureToggleService.estActive(CleFonctionnalite.OFFRES_QUANTITE)) return List.of();
        LocalDate jour = LocalDate.now(ZONE);
        return repository.findByActiveTrue().stream()
                .filter(o -> o.estValable(jour))
                .sorted(Comparator.comparing(OffreQuantite::getProduitId).thenComparing(OffreQuantite::getQuantite))
                .map(o -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("produitId", o.getProduitId());
                    m.put("quantite", o.getQuantite());
                    m.put("prix", o.getPrix());
                    return m;
                })
                .toList();
    }

    /** Toutes les offres (page du gérant), avec le nom et le prix normal du produit. */
    public List<Map<String, Object>> lister() {
        List<OffreQuantite> offres = repository.findAllByOrderByProduitIdAscQuantiteAsc();
        Map<Long, Produit> produits = produitRepository.findAllById(
                offres.stream().map(OffreQuantite::getProduitId).collect(Collectors.toSet())).stream()
                .collect(Collectors.toMap(Produit::getId, p -> p));
        LocalDate jour = LocalDate.now(ZONE);
        return offres.stream().map(o -> versMap(o, produits.get(o.getProduitId()), jour))
                .sorted(Comparator.comparing((Map<String, Object> m) -> String.valueOf(m.get("produitNom")))
                        .thenComparing(m -> (Integer) m.get("quantite")))
                .toList();
    }

    // ================================================================= gérant

    @Transactional
    public Map<String, Object> enregistrer(Long id, Map<String, Object> corps, String auteur) {
        OffreQuantite o = id == null ? new OffreQuantite()
                : repository.findById(id).orElseThrow(() -> new IllegalArgumentException("Offre introuvable."));
        Long produitId = lireLong(corps.get("produitId"), "Choisissez le produit.");
        Produit produit = produitRepository.findById(produitId)
                .orElseThrow(() -> new IllegalArgumentException("Produit introuvable."));
        if (produit.getModeMesure() != null) {
            throw new IllegalArgumentException("Les produits vendus au poids, au litre ou au mètre ne peuvent pas avoir d'offre par quantité.");
        }
        double prixNormal = produit.getPrixVente() == null ? 0 : produit.getPrixVente();
        if (prixNormal <= 0) throw new IllegalArgumentException("Ce produit n'a pas de prix de vente.");

        int quantite = (int) lireLong(corps.get("quantite"), "Indiquez le nombre d'articles du lot.").longValue();
        if (quantite < 2 || quantite > 10_000) throw new IllegalArgumentException("Le lot doit contenir au moins 2 articles.");
        double prix = lireDouble(corps.get("prix"), "Indiquez le prix du lot.");
        if (prix <= 0) throw new IllegalArgumentException("Le prix du lot doit être supérieur à 0.");
        if (prix >= quantite * prixNormal - 0.001) {
            throw new IllegalArgumentException("Ce n'est pas une réduction : " + quantite + " articles coûtent déjà "
                    + Math.round(quantite * prixNormal) + " F au prix normal.");
        }
        LocalDate debut = lireDate(corps.get("dateDebut"));
        LocalDate fin = lireDate(corps.get("dateFin"));
        if (debut != null && fin != null && fin.isBefore(debut)) {
            throw new IllegalArgumentException("La date de fin doit être après la date de début.");
        }
        boolean active = corps.get("active") == null || Boolean.parseBoolean(String.valueOf(corps.get("active")));

        if (active) {
            List<OffreQuantite> autres = repository.findByProduitIdAndActiveTrue(produitId).stream()
                    .filter(a -> !a.getId().equals(o.getId()))
                    .toList();
            boolean doublon = autres.stream().anyMatch(a -> a.getQuantite() == quantite && periodesSeChevauchent(a, debut, fin));
            if (doublon) throw new IllegalArgumentException("Une offre pour " + quantite + " articles existe déjà sur cette période.");
            if (autres.size() >= PALIERS_MAX) throw new IllegalArgumentException("Maximum " + PALIERS_MAX + " offres actives par produit.");
        }

        o.setProduitId(produitId);
        o.setQuantite(quantite);
        o.setPrix(CalculOffre.arrondir(prix));
        o.setDateDebut(debut);
        o.setDateFin(fin);
        o.setActive(active);
        if (o.getId() == null) {
            o.setCreePar(auteur);
            o.setDateCreation(LocalDateTime.now(ZONE));
        }
        repository.save(o);
        return versMap(o, produit, LocalDate.now(ZONE));
    }

    @Transactional
    public void supprimer(Long id) {
        if (!repository.existsById(id)) throw new IllegalArgumentException("Offre introuvable.");
        repository.deleteById(id);
    }

    // ================================================================= outils

    private Map<String, Object> versMap(OffreQuantite o, Produit p, LocalDate jour) {
        Map<String, Object> m = new LinkedHashMap<>();
        double prixNormal = p != null && p.getPrixVente() != null ? p.getPrixVente() : 0;
        m.put("id", o.getId());
        m.put("produitId", o.getProduitId());
        m.put("produitNom", p != null ? p.getNom() : "Produit supprimé");
        m.put("prixNormal", prixNormal);
        m.put("prixAchat", p != null && p.getPrixAchat() != null ? p.getPrixAchat() : 0);
        m.put("quantite", o.getQuantite());
        m.put("prix", o.getPrix());
        m.put("economie", CalculOffre.arrondir(o.getQuantite() * prixNormal - o.getPrix()));
        m.put("dateDebut", o.getDateDebut());
        m.put("dateFin", o.getDateFin());
        m.put("active", o.getActive());
        m.put("enCours", o.estValable(jour));
        m.put("creePar", o.getCreePar());
        return m;
    }

    private static boolean periodesSeChevauchent(OffreQuantite a, LocalDate debut, LocalDate fin) {
        LocalDate aDebut = a.getDateDebut() == null ? LocalDate.MIN : a.getDateDebut();
        LocalDate aFin = a.getDateFin() == null ? LocalDate.MAX : a.getDateFin();
        LocalDate bDebut = debut == null ? LocalDate.MIN : debut;
        LocalDate bFin = fin == null ? LocalDate.MAX : fin;
        return !aFin.isBefore(bDebut) && !bFin.isBefore(aDebut);
    }

    private static boolean positif(Double v) {
        return v != null && v > 0;
    }

    private static Long lireLong(Object v, String message) {
        if (v == null || String.valueOf(v).isBlank()) throw new IllegalArgumentException(message);
        try {
            return (long) Double.parseDouble(String.valueOf(v).trim());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(message);
        }
    }

    private static double lireDouble(Object v, String message) {
        if (v == null || String.valueOf(v).isBlank()) throw new IllegalArgumentException(message);
        try {
            return Double.parseDouble(String.valueOf(v).trim().replace(',', '.'));
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(message);
        }
    }

    private static LocalDate lireDate(Object v) {
        if (v == null || String.valueOf(v).isBlank()) return null;
        try {
            return LocalDate.parse(String.valueOf(v).trim().substring(0, 10));
        } catch (RuntimeException e) {
            throw new IllegalArgumentException("Date invalide.");
        }
    }
}
