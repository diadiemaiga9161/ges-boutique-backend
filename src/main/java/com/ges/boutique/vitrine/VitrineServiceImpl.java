package com.ges.boutique.vitrine;

import com.ges.boutique.boutique.Boutique;
import com.ges.boutique.boutique.BoutiqueService;
import com.ges.boutique.commande.Commande;
import com.ges.boutique.commande.CommandeRepository;
import com.ges.boutique.commande.OrigineCommande;
import com.ges.boutique.produit.Produit;
import com.ges.boutique.produit.ProduitRepository;
import com.ges.boutique.promo.Promotion;
import com.ges.boutique.promo.PromotionService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Service dédié au mini-site vitrine PUBLIC (sans authentification).
 * Ne réimplémente aucune logique métier existante : réutilise
 * PromotionService (promos actives) et BoutiqueService (infos boutique),
 * et se contente de mapper vers des DTOs minimaux sans champs internes.
 */
@Service
@RequiredArgsConstructor
public class VitrineServiceImpl implements VitrineService {

    private final ProduitRepository produitRepository;
    private final PromotionService promotionService;
    private final BoutiqueService boutiqueService;
    private final CommandeRepository commandeRepository;
    private final com.ges.boutique.feature.FeatureToggleService featureToggleService;

    /** Un téléphone garde au plus ce nombre de commandes à suivre (limite les abus). */
    private static final int MAX_SUIVI = 20;

    @Override
    @org.springframework.transaction.annotation.Transactional(readOnly = true)
    public List<VitrineSuiviDto> suivreCommandes(VitrineSuiviRequest request) {
        List<VitrineSuiviDto> resultat = new ArrayList<>();
        if (request == null || request.getCommandes() == null) return resultat;

        request.getCommandes().stream()
                .filter(x -> x != null && x.getNumero() != null && !x.getNumero().isBlank()
                        && x.getCode() != null && !x.getCode().isBlank())
                .limit(MAX_SUIVI)
                .forEach(x -> commandeRepository
                        .findFirstByNumeroCommandeAndOrigine(x.getNumero().trim(), OrigineCommande.VITRINE)
                        .filter(c -> memeCode(c.getCodeSuivi(), x.getCode().trim()))
                        .filter(c -> resultat.stream().noneMatch(r -> r.getNumero().equals(c.getNumeroCommande())))
                        .ifPresent(c -> resultat.add(versSuivi(c))));
        resultat.sort(Comparator.comparing(VitrineSuiviDto::getDateCommande,
                Comparator.nullsLast(Comparator.reverseOrder())));
        return resultat;
    }

    /** Comparaison en temps constant (ne laisse pas deviner le code caractère par caractère). */
    private static boolean memeCode(String attendu, String recu) {
        if (attendu == null || attendu.isEmpty()) return false;
        return java.security.MessageDigest.isEqual(
                attendu.getBytes(java.nio.charset.StandardCharsets.UTF_8),
                recu.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    private VitrineSuiviDto versSuivi(Commande c) {
        VitrineSuiviDto dto = new VitrineSuiviDto();
        dto.setNumero(c.getNumeroCommande());
        dto.setStatut(c.getStatut() == null ? null : c.getStatut().name());
        dto.setEtapeLivraison(c.getEtapeLivraison() == null ? null : c.getEtapeLivraison().name());
        dto.setDateCommande(c.getDateCommande());
        dto.setMontantTotal(c.getMontantTotal());
        c.getLignes().forEach(l -> {
            VitrineSuiviDto.Ligne ligne = new VitrineSuiviDto.Ligne();
            ligne.setNom(l.getProduit() != null ? l.getProduit().getNom() : "Produit");
            ligne.setQuantite(l.getQuantite());
            dto.getLignes().add(ligne);
        });
        return dto;
    }

    @Override
    public List<VitrineProduitDto> obtenirProduitsVitrine() {
        List<Produit> produits = produitRepository.findAll();
        boolean photos = featureToggleService.estActive(com.ges.boutique.feature.CleFonctionnalite.IMAGES_PRODUITS);
        return produits.stream()
                .map(this::versDto)
                .peek(dto -> { if (!photos) dto.setImageVersion(null); })
                .collect(Collectors.toList());
    }

    @Override
    public VitrineInfoDto obtenirInfosVitrine() {
        Boutique boutique = boutiqueService.obtenirBoutique();
        VitrineInfoDto dto = new VitrineInfoDto();
        dto.setNom(boutique.getNom());
        dto.setAdresse(boutique.getAdresse());
        dto.setTelephone(boutique.getTelephone());
        dto.setHorairesOuverture(boutique.getHorairesOuverture());
        dto.setLogoPath(boutique.getLogoPath());
        dto.setVitrineActive(!Boolean.FALSE.equals(boutique.getFeatureVitrineActif()));
        return dto;
    }

    private VitrineProduitDto versDto(Produit produit) {
        VitrineProduitDto dto = new VitrineProduitDto();
        dto.setId(produit.getId());
        dto.setNom(produit.getNom());
        dto.setImageVersion(produit.getImageVersion());
        dto.setCategorieNom(produit.getCategorie() != null ? produit.getCategorie().getNom() : null);
        // Vente à la mesure : prix affiché au kg/L/m, comme le client le connaît.
        dto.setPrixVente(com.ges.boutique.produit.ModeMesure.prixLisible(produit, produit.getPrixVente()));
        dto.setUnite(produit.getModeMesure() != null ? produit.getModeMesure().getUnite() : null);
        int quantite = produit.getQuantite() != null ? produit.getQuantite() : 0;
        dto.setDisponible(quantite > 0);
        if (quantite <= 0) {
            dto.setStatutStock("RUPTURE");
        } else if (produit.estStockFaible()) {
            dto.setStatutStock("STOCK_FAIBLE");
        } else {
            dto.setStatutStock("DISPONIBLE");
        }

        Promotion promo = meilleurePromo(produit);
        if (promo != null) {
            dto.setEnPromotion(true);
            dto.setPromotionTitre(promo.getTitre());
            dto.setPromotionReduction(formatReduction(promo));
        } else {
            dto.setEnPromotion(false);
            dto.setPromotionTitre(null);
            dto.setPromotionReduction(null);
        }

        return dto;
    }

    /** Parmi les promos applicables au produit, retient la plus avantageuse (réduction équivalente la plus élevée). */
    private Promotion meilleurePromo(Produit produit) {
        List<Promotion> promos = promotionService.obtenirPromosPourProduit(produit.getId());
        if (promos == null || promos.isEmpty()) {
            return null;
        }
        return promos.stream()
                .max(Comparator.comparingDouble(p -> montantReduction(p, produit.getPrixVente())))
                .orElse(null);
    }

    private double montantReduction(Promotion promo, Double prixVente) {
        if (promo.getValeurReduction() == null) {
            return 0d;
        }
        if ("POURCENTAGE".equals(promo.getTypeReduction())) {
            double prix = prixVente != null ? prixVente : 0d;
            return prix * promo.getValeurReduction() / 100d;
        }
        return promo.getValeurReduction();
    }

    private String formatReduction(Promotion promo) {
        if (promo.getValeurReduction() == null) {
            return null;
        }
        return "POURCENTAGE".equals(promo.getTypeReduction())
                ? promo.getValeurReduction().intValue() + "%"
                : promo.getValeurReduction().intValue() + " FCFA";
    }
}
