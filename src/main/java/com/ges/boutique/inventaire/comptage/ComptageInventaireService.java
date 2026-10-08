package com.ges.boutique.inventaire.comptage;

import com.ges.boutique.exception.RessourceIntrouvableException;
import com.ges.boutique.inventaire.InventaireService;
import com.ges.boutique.produit.CategorieRepository;
import com.ges.boutique.produit.Produit;
import com.ges.boutique.produit.ProduitRepository;
import com.ges.boutique.utilisateur.Utilisateur;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;

/**
 * Comptage d'inventaire (voir ComptageInventaire). La validation ne fait que réutiliser
 * InventaireService.ajusterStock (niveaux de conditionnement, historique des mouvements,
 * verrou du stock) en appliquant l'ÉCART constaté au stock actuel.
 */
@Service
@RequiredArgsConstructor
public class ComptageInventaireService {

    private final ComptageInventaireRepository comptageRepository;
    private final LigneComptageRepository ligneRepository;
    private final ProduitRepository produitRepository;
    private final CategorieRepository categorieRepository;
    private final InventaireService inventaireService;

    @Transactional(readOnly = true)
    public Optional<Map<String, Object>> enCours() {
        return comptageRepository.findFirstByStatut(StatutComptage.EN_COURS).map(this::detail);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> obtenir(Long id) {
        return detail(trouver(id));
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> historique() {
        List<Map<String, Object>> liste = new ArrayList<>();
        for (ComptageInventaire c : comptageRepository.findTop20ByOrderByDateDebutDesc()) {
            Map<String, Object> m = entete(c);
            Totaux t = totaux(ligneRepository.findByComptageIdOrderByDateComptageDesc(c.getId()));
            m.put("nbComptes", t.nbLignes());
            m.put("nbEcarts", t.nbEcarts());
            m.put("valeurNette", t.valeurNette());
            liste.add(m);
        }
        return liste;
    }

    /** Un seul comptage en cours : s'il existe déjà, on le reprend. */
    @Transactional
    public Map<String, Object> demarrer(Long categorieId, Utilisateur auteur) {
        Optional<ComptageInventaire> existant = comptageRepository.findFirstByStatut(StatutComptage.EN_COURS);
        if (existant.isPresent()) return detail(existant.get());
        ComptageInventaire c = new ComptageInventaire();
        if (categorieId != null) {
            c.setCategorieId(categorieId);
            c.setCategorieNom(categorieRepository.findById(categorieId)
                    .orElseThrow(() -> new RessourceIntrouvableException("Catégorie introuvable")).getNom());
        }
        c.setCommencePar(nom(auteur));
        c.setDateDebut(LocalDateTime.now());
        return detail(comptageRepository.save(c));
    }

    /** Quantité comptée d'un produit (unité de stock). null = retirer le produit du comptage. */
    @Transactional
    public Map<String, Object> compter(Long id, Long produitId, Integer quantite, Utilisateur auteur) {
        ComptageInventaire c = enCoursOuErreur(id);
        if (quantite == null) {
            ligneRepository.findByComptageIdAndProduitId(id, produitId).ifPresent(ligneRepository::delete);
            return detail(c);
        }
        if (quantite < 0) throw new IllegalArgumentException("La quantité comptée ne peut pas être négative.");
        Produit p = produitRepository.findById(produitId)
                .orElseThrow(() -> new RessourceIntrouvableException("Produit introuvable"));
        if (c.getCategorieId() != null
                && (p.getCategorie() == null || !c.getCategorieId().equals(p.getCategorie().getId()))) {
            throw new IllegalArgumentException("Ce produit n'est pas dans la catégorie de ce comptage ("
                    + c.getCategorieNom() + ").");
        }
        LigneComptage l = ligneRepository.findByComptageIdAndProduitId(id, produitId).orElseGet(LigneComptage::new);
        l.setComptageId(id);
        l.setProduitId(produitId);
        l.setProduitNom(p.getNom());
        l.setQuantiteComptee(quantite);
        // Stock enregistré à l'instant du comptage (re-compté = nouvelle photo du stock).
        l.setStockAuComptage(p.getQuantite() == null ? 0 : p.getQuantite());
        l.setPrixAchat(p.getPrixAchat());
        l.setComptePar(nom(auteur));
        l.setDateComptage(LocalDateTime.now());
        ligneRepository.save(l);
        return detail(c);
    }

    /** Corrige le stock de chaque produit compté avec un écart, puis clôt le comptage. */
    @Transactional
    public Map<String, Object> valider(Long id, Utilisateur auteur) {
        ComptageInventaire c = enCoursOuErreur(id);
        String motif = "Comptage d'inventaire #" + id;
        for (LigneComptage l : ligneRepository.findByComptageIdOrderByDateComptageDesc(id)) {
            int ecart = l.getEcart();
            if (ecart == 0) continue;
            Produit p = produitRepository.findById(l.getProduitId()).orElse(null);
            if (p == null) continue; // produit supprimé entre-temps
            int actuel = p.getQuantite() == null ? 0 : p.getQuantite();
            // Écart appliqué au stock ACTUEL : les ventes faites depuis le comptage restent comptées.
            inventaireService.ajusterStock(p.getId(), Math.max(0, actuel + ecart), auteur.getId(), motif);
        }
        c.setStatut(StatutComptage.VALIDE);
        c.setTerminePar(nom(auteur));
        c.setDateFin(LocalDateTime.now());
        return detail(comptageRepository.save(c));
    }

    @Transactional
    public Map<String, Object> abandonner(Long id, Utilisateur auteur) {
        ComptageInventaire c = enCoursOuErreur(id);
        c.setStatut(StatutComptage.ABANDONNE);
        c.setTerminePar(nom(auteur));
        c.setDateFin(LocalDateTime.now());
        return detail(comptageRepository.save(c));
    }

    // ---------------------------------------------------------------- détail

    private ComptageInventaire trouver(Long id) {
        return comptageRepository.findById(id)
                .orElseThrow(() -> new RessourceIntrouvableException("Comptage introuvable"));
    }

    private ComptageInventaire enCoursOuErreur(Long id) {
        ComptageInventaire c = trouver(id);
        if (c.getStatut() != StatutComptage.EN_COURS) {
            throw new IllegalStateException("Ce comptage est déjà terminé.");
        }
        return c;
    }

    private Map<String, Object> entete(ComptageInventaire c) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", c.getId());
        m.put("statut", c.getStatut().name());
        m.put("categorieId", c.getCategorieId());
        m.put("categorieNom", c.getCategorieNom());
        m.put("commencePar", c.getCommencePar());
        m.put("dateDebut", c.getDateDebut());
        m.put("terminePar", c.getTerminePar());
        m.put("dateFin", c.getDateFin());
        return m;
    }

    private Map<String, Object> detail(ComptageInventaire c) {
        Map<String, Object> m = entete(c);
        List<LigneComptage> lignes = ligneRepository.findByComptageIdOrderByDateComptageDesc(c.getId());
        List<Map<String, Object>> sortie = new ArrayList<>();
        for (LigneComptage l : lignes) {
            Map<String, Object> x = new LinkedHashMap<>();
            x.put("produitId", l.getProduitId());
            x.put("produitNom", l.getProduitNom());
            x.put("quantiteComptee", l.getQuantiteComptee());
            x.put("stockAuComptage", l.getStockAuComptage());
            x.put("ecart", l.getEcart());
            x.put("valeurEcart", valeur(l));
            x.put("comptePar", l.getComptePar());
            x.put("dateComptage", l.getDateComptage());
            sortie.add(x);
        }
        Totaux t = totaux(lignes);
        m.put("lignes", sortie);
        m.put("nbComptes", t.nbLignes());
        m.put("nbEcarts", t.nbEcarts());
        m.put("valeurManquants", t.valeurManquants());
        m.put("valeurSurplus", t.valeurSurplus());
        m.put("valeurNette", t.valeurNette());
        m.put("nbProduitsACompter", c.getCategorieId() == null
                ? produitRepository.count()
                : produitRepository.findByCategorieId(c.getCategorieId()).size());
        return m;
    }

    private static double valeur(LigneComptage l) {
        return Math.round(l.getEcart() * (l.getPrixAchat() == null ? 0 : l.getPrixAchat()));
    }

    private record Totaux(int nbLignes, int nbEcarts, double valeurManquants, double valeurSurplus, double valeurNette) {}

    private static Totaux totaux(List<LigneComptage> lignes) {
        int nbEcarts = 0;
        double manquants = 0;
        double surplus = 0;
        for (LigneComptage l : lignes) {
            if (l.getEcart() == 0) continue;
            nbEcarts++;
            double v = valeur(l);
            if (v < 0) manquants += -v; else surplus += v;
        }
        return new Totaux(lignes.size(), nbEcarts, manquants, surplus, surplus - manquants);
    }

    private static String nom(Utilisateur u) {
        if (u == null) return null;
        return u.getNomComplet() != null && !u.getNomComplet().isBlank() ? u.getNomComplet() : u.getUsername();
    }
}
