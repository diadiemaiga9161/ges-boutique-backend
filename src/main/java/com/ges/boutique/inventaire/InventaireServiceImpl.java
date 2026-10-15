package com.ges.boutique.inventaire;

import com.ges.boutique.exception.RessourceIntrouvableException;
import com.ges.boutique.exception.StockInsuffisantException;
import com.ges.boutique.produit.Produit;
import com.ges.boutique.produit.ProduitNiveau;
import com.ges.boutique.produit.ProduitNiveauRepository;
import com.ges.boutique.produit.ProduitRepository;
import com.ges.boutique.utilisateur.Utilisateur;
import com.ges.boutique.utilisateur.UtilisateurRepository;
import com.ges.boutique.config.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class InventaireServiceImpl implements InventaireService {

    private final MouvementStockRepository mouvementStockRepository;
    private final ProduitRepository produitRepository;
    private final ProduitNiveauRepository produitNiveauRepository;
    private final UtilisateurRepository utilisateurRepository;
    private final NotificationService notificationService;

    // ========== MÉTHODES EXISTANTES ==========

    @Override
    @Transactional
    @CacheEvict(value = "produits", allEntries = true)
    public void entreeStock(Long produitId, Integer quantite, Long utilisateurId, String motif) {
        entreeStock(produitId, quantite, utilisateurId, motif, null);
    }

    @Override
    @Transactional
    @CacheEvict(value = "produits", allEntries = true)
    public void entreeStock(Long produitId, Integer quantite, Long utilisateurId, String motif, LocalDateTime dateMouvement) {
        Produit produit = produitRepository.findById(produitId)
                .orElseThrow(() -> new RessourceIntrouvableException("Produit non trouvé"));

        int ancienneQuantite = produit.getQuantite();
        Long niveauCibleId = null;
        String niveauCibleNom = null;

        // Produit avec niveaux : Produit.quantite est une valeur DÉRIVÉE, recalculée
        // à chaque vente à partir des stocks de niveaux (VenteServiceImpl.syncProduitQuantite).
        // Si on l'incrémentait directement ici, l'entrée serait silencieusement perdue
        // à la prochaine vente. Le stock entrant va donc toujours dans le niveau racine
        // (le "produit principal", ex: Carton) — jamais ailleurs.
        ProduitNiveau niveauPrincipal = appliquerDeltaNiveauRacineEtRecalculer(produit, quantite);
        if (niveauPrincipal != null) {
            niveauCibleId = niveauPrincipal.getId();
            niveauCibleNom = niveauPrincipal.getNom();
        }
        int nouvelleQuantite = produit.getQuantite();

        produitRepository.save(produit);

        MouvementStock mouvement = new MouvementStock();
        mouvement.setProduit(produit);
        mouvement.setQuantite(quantite);
        mouvement.setTypeMouvement(TypeMouvement.ENTREE);
        mouvement.setQuantiteAvant(ancienneQuantite);
        mouvement.setQuantiteApres(nouvelleQuantite);
        mouvement.setMotif(motif);
        mouvement.setDateMouvement(dateMouvement != null ? dateMouvement : LocalDateTime.now());
        if (niveauCibleId != null) {
            mouvement.setNiveauId(niveauCibleId);
            mouvement.setNiveauNom(niveauCibleNom);
        }

        if (utilisateurId != null) {
            utilisateurRepository.findById(utilisateurId).ifPresent(mouvement::setUtilisateur);
        }
        mouvementStockRepository.save(mouvement);
        notificationService.notifierMiseAJourStock(produit.getId(), produit.getNom(), nouvelleQuantite);
    }

    private static final int PROFONDEUR_MAX_NIVEAUX = 5;

    /** Combien d'unités de base (niveau feuille) représente 1 unité de ce niveau —
     *  miroir de VenteServiceImpl.facteurVersBase, pour que Produit.quantite reste
     *  cohérent quel que soit le point d'entrée (vente ou entrée de stock). */
    private long facteurVersBase(ProduitNiveau niveau, List<ProduitNiveau> niveaux, Set<Long> visites) {
        if (!visites.add(niveau.getId()) || visites.size() > PROFONDEUR_MAX_NIVEAUX) {
            throw new IllegalStateException(
                    "Hiérarchie de niveaux invalide (cycle ou profondeur > " + PROFONDEUR_MAX_NIVEAUX +
                            ") pour le produit " + niveau.getProduit().getId());
        }
        ProduitNiveau child = niveaux.stream()
                .filter(n -> niveau.getId().equals(n.getParentId()))
                .findFirst().orElse(null);
        if (child == null) return 1L;
        long facteurChild = child.getFacteur() != null && child.getFacteur() > 0 ? child.getFacteur() : 1L;
        return facteurChild * facteurVersBase(child, niveaux, visites);
    }

    /**
     * BUG FIX (audit comptable/stock, point 3 — désynchronisation cascade) : jusqu'ici, seule
     * entreeStock() était "niveau-aware" (elle mettait à jour ProduitNiveau.stock en plus de
     * Produit.quantite) ; toutes les autres méthodes de ce fichier modifiaient Produit.quantite
     * directement, laissant ProduitNiveau.stock figé. Résultat vérifié en base réelle : un achat
     * annulé (via sortieStock) puis un nouvel achat (via entreeStock, qui repart du niveau resté
     * pollué) faisait doubler le stock affiché (40 au lieu de 20 réellement en stock).
     *
     * Applique algébriquement `delta` unités de base au niveau racine (le "produit principal",
     * ex: Carton) d'un produit à niveaux de conditionnement, recalcule Produit.quantite comme
     * somme de tous les niveaux convertis en unités de base (même logique que entreeStock), et
     * met à jour l'objet `produit` passé en paramètre (l'appelant reste responsable de le
     * persister). Tout mouvement de stock générique (entrée, sortie, retour, ajustement manuel,
     * bonus) passe donc toujours par le niveau racine — jamais par un niveau intermédiaire ou
     * feuille — exactement comme le fait déjà entreeStock() pour les entrées.
     *
     * @return le niveau racine modifié, ou null si le produit n'a pas de niveaux de
     *         conditionnement (l'appelant doit alors modifier Produit.quantite directement).
     * @throws StockInsuffisantException si delta est négatif et ferait passer le niveau racine
     *         sous 0 (le stock à retirer n'est pas physiquement disponible à ce niveau).
     */
    private ProduitNiveau appliquerDeltaNiveauRacineEtRecalculer(Produit produit, int delta) {
        List<ProduitNiveau> niveaux = produitNiveauRepository.findByProduitIdOrderByOrdreAsc(produit.getId());
        if (niveaux.isEmpty()) {
            produit.setQuantite(produit.getQuantite() + delta);
            return null;
        }

        ProduitNiveau niveauPrincipal = niveaux.stream()
                .filter(n -> n.getParentId() == null)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        "Aucun niveau racine (produit principal) trouvé pour le produit " + produit.getNom()));

        // `delta` est en unités de base (pièces, comme Produit.quantite). On le range en unités
        // du niveau racine quand il en fait un compte rond (ex: 24 pièces = 1 carton), et le
        // reste au niveau le plus petit : avant, les pièces étaient ajoutées telles quelles au
        // niveau racine (24 pièces rendues devenaient 24 cartons) dès qu'il y avait 2 niveaux.
        // Un seul niveau : facteur 1, tout va au niveau racine, comme avant.
        long parRacine = facteurVersBase(niveauPrincipal, niveaux, new HashSet<>());
        ProduitNiveau feuille = parRacine == 1 ? niveauPrincipal : niveaux.stream()
                .filter(n -> niveaux.stream().noneMatch(m -> n.getId().equals(m.getParentId())))
                .findFirst().orElse(niveauPrincipal);
        int racine = niveauPrincipal.getStock() != null ? niveauPrincipal.getStock() : 0;
        int petit = feuille.getStock() != null ? feuille.getStock() : 0;
        int principal = produit.getQuantitePrincipale() != null ? produit.getQuantitePrincipale() : 0;
        int parPrincipal = niveauPrincipal.getFacteur() != null && niveauPrincipal.getFacteur() > 0 ? niveauPrincipal.getFacteur() : 1;
        int stockAvant = racine;

        int deltaRacine = (int) (delta / parRacine);
        int deltaPetit = (int) (delta % parRacine);
        if (feuille == niveauPrincipal) {
            racine += deltaRacine + deltaPetit;
        } else {
            racine += deltaRacine;
            petit += deltaPetit;
            // Pièces manquantes au plus petit niveau : on ouvre une unité du niveau racine.
            while (petit < 0 && racine > 0) {
                racine--;
                petit += (int) parRacine;
            }
        }
        // Unités racine manquantes : on ouvre le stock principal fermé (même logique que la vente).
        while (racine < 0 && principal > 0) {
            principal--;
            racine += parPrincipal;
        }
        if (racine < 0 || petit < 0) {
            throw new StockInsuffisantException("Stock insuffisant pour " + produit.getNom()
                    + ". Disponible: " + produit.getQuantite() + ", demandé: " + (-delta));
        }
        niveauPrincipal.setStock(racine);
        produitNiveauRepository.save(niveauPrincipal);
        if (feuille != niveauPrincipal) {
            feuille.setStock(petit);
            produitNiveauRepository.save(feuille);
        }
        if (produit.getQuantitePrincipale() != null || principal != 0) {
            produit.setQuantitePrincipale(principal);
        }
        log.debug("Niveaux {} : racine {} -> {}, delta {}", produit.getNom(), stockAvant, racine, delta);

        // Total affiché = tous les niveaux en pièces + le stock principal encore fermé
        // (même calcul que VenteServiceImpl.syncProduitQuantite) : avant, le stock fermé
        // était oublié et le total chutait après un retour, un achat ou un ajustement.
        List<ProduitNiveau> niveauxMaj = produitNiveauRepository.findByProduitIdOrderByOrdreAsc(produit.getId());
        long total = niveauxMaj.stream()
                .mapToLong(n -> (n.getStock() != null ? n.getStock() : 0L) * facteurVersBase(n, niveauxMaj, new HashSet<>()))
                .sum();
        total += (long) principal * parPrincipal * parRacine;
        produit.setQuantite((int) total);

        return niveauPrincipal;
    }

    @Override
    @Transactional
    @CacheEvict(value = "produits", allEntries = true)
    public void sortieStock(Long produitId, Integer quantite, Long utilisateurId, String motif) {
        doSortieStock(produitId, quantite, utilisateurId, motif, null);
    }

    @Override
    @Transactional
    @CacheEvict(value = "produits", allEntries = true)
    public void sortieStock(Long produitId, Integer quantite, Long utilisateurId, String motif, String typeSortie) {
        doSortieStock(produitId, quantite, utilisateurId, motif, typeSortie);
    }

    private void doSortieStock(Long produitId, Integer quantite, Long utilisateurId, String motif, String typeSortie) {
        Produit produit = produitRepository.findById(produitId)
                .orElseThrow(() -> new RessourceIntrouvableException("Produit non trouvé"));

        if (produit.getQuantite() < quantite) {
            throw new StockInsuffisantException("Stock insuffisant. Disponible: " + produit.getQuantite() + ", Demande: " + quantite);
        }

        int ancienneQuantite = produit.getQuantite();
        // BUG FIX (audit comptable/stock) : voir appliquerDeltaNiveauRacineEtRecalculer — sans
        // ça, cette sortie (utilisée notamment par l'annulation d'achat fournisseur) ne touchait
        // que Produit.quantite, jamais ProduitNiveau.stock, d'où la désynchronisation cascade.
        appliquerDeltaNiveauRacineEtRecalculer(produit, -quantite);
        int nouvelleQuantite = produit.getQuantite();
        produitRepository.save(produit);

        MouvementStock mouvement = new MouvementStock();
        mouvement.setProduit(produit);
        mouvement.setQuantite(quantite);
        mouvement.setTypeMouvement(TypeMouvement.SORTIE);
        mouvement.setQuantiteAvant(ancienneQuantite);
        mouvement.setQuantiteApres(nouvelleQuantite);
        mouvement.setMotif(motif);
        mouvement.setTypeSortie(typeSortie);
        mouvement.setDateMouvement(LocalDateTime.now());

        if (utilisateurId != null) {
            utilisateurRepository.findById(utilisateurId).ifPresent(mouvement::setUtilisateur);
        }
        mouvementStockRepository.save(mouvement);
        notificationService.notifierMiseAJourStock(produit.getId(), produit.getNom(), nouvelleQuantite);
        log.info("📦 Stock SORTIE: -{} x {} (Type: {}) - Stock: {} → {}",
                quantite, produit.getNom(), typeSortie, ancienneQuantite, nouvelleQuantite);
    }

    @Override
    @Transactional
    @CacheEvict(value = "produits", allEntries = true)
    public void retourStock(Long produitId, Integer quantite, Long utilisateurId, String motif) {
        Produit produit = produitRepository.findById(produitId)
                .orElseThrow(() -> new RessourceIntrouvableException("Produit non trouvé"));

        int ancienneQuantite = produit.getQuantite();
        // BUG FIX (audit comptable/stock) : utilisée par RetourVenteServiceImpl (retour vente) —
        // voir appliquerDeltaNiveauRacineEtRecalculer pour le pourquoi.
        appliquerDeltaNiveauRacineEtRecalculer(produit, quantite);
        int nouvelleQuantite = produit.getQuantite();
        produitRepository.save(produit);

        enregistrerMouvement(produit, quantite, TypeMouvement.RETOUR,
                ancienneQuantite, nouvelleQuantite, utilisateurId, motif, null, null, null);
    }

    @Override
    @Transactional
    @CacheEvict(value = "produits", allEntries = true)
    public void ajusterStock(Long produitId, Integer nouvelleQuantiteDemandee, Long utilisateurId, String motif) {
        Produit produit = produitRepository.findById(produitId)
                .orElseThrow(() -> new RessourceIntrouvableException("Produit non trouvé"));

        int ancienneQuantite = produit.getQuantite();
        int difference = nouvelleQuantiteDemandee - ancienneQuantite;
        TypeMouvement type = difference > 0 ? TypeMouvement.ENTREE : TypeMouvement.SORTIE;

        // BUG FIX (audit comptable/stock) : ajusterStock fixe une quantité ABSOLUE (ex: correction
        // d'inventaire) — on applique donc la différence (delta) au niveau racine plutôt que
        // d'écraser Produit.quantite directement, sinon ProduitNiveau.stock se désynchronise dès
        // le prochain mouvement sur un produit à niveaux.
        appliquerDeltaNiveauRacineEtRecalculer(produit, difference);
        int nouvelleQuantite = produit.getQuantite();
        produitRepository.save(produit);

        enregistrerMouvement(produit, Math.abs(difference), type,
                ancienneQuantite, nouvelleQuantite, utilisateurId, motif, null, null, null);
    }

    // ========== NOUVELLES MÉTHODES AVEC TRACABILITÉ ==========

    @Override
    @Transactional
    @CacheEvict(value = "produits", allEntries = true)
    public void entreeStockAchat(Long produitId, Integer quantite, Long utilisateurId, Long achatId) {
        Produit produit = produitRepository.findById(produitId)
                .orElseThrow(() -> new RessourceIntrouvableException("Produit non trouvé"));

        int ancienneQuantite = produit.getQuantite();
        appliquerDeltaNiveauRacineEtRecalculer(produit, quantite);
        int nouvelleQuantite = produit.getQuantite();
        produitRepository.save(produit);

        String motif = "Achat fournisseur #" + achatId;
        enregistrerMouvement(produit, quantite, TypeMouvement.ENTREE,
                ancienneQuantite, nouvelleQuantite, utilisateurId, motif, achatId, "ACHAT", achatId);

        log.info("📦 Stock ENTRÉE: +{} x {} (Achat #{}) - Stock: {} → {}",
                quantite, produit.getNom(), achatId, ancienneQuantite, nouvelleQuantite);
    }

    @Override
    @Transactional
    @CacheEvict(value = "produits", allEntries = true)
    public void sortieStockAnnulationAchat(Long produitId, Integer quantite, Long utilisateurId, Long achatId) {
        Produit produit = produitRepository.findById(produitId)
                .orElseThrow(() -> new RessourceIntrouvableException("Produit non trouvé"));

        int stockActuel = produit.getQuantite();
        int qteEffective = Math.min(quantite, stockActuel);

        if (qteEffective <= 0) {
            log.warn("Stock déjà à 0 pour {} — aucune sortie créée", produit.getNom());
            return;
        }

        int ancienneQuantite = produit.getQuantite();
        appliquerDeltaNiveauRacineEtRecalculer(produit, -qteEffective);
        int nouvelleQuantite = produit.getQuantite();
        produitRepository.save(produit);

        String motif = "Annulation achat fournisseur #" + achatId;
        enregistrerMouvement(produit, qteEffective, TypeMouvement.SORTIE,
                ancienneQuantite, nouvelleQuantite, utilisateurId, motif, achatId, "ANNULATION_ACHAT", achatId);

        log.info("📦 Stock SORTIE: -{} x {} (Annulation Achat #{}) - Stock: {} → {}",
                qteEffective, produit.getNom(), achatId, ancienneQuantite, nouvelleQuantite);
    }

    @Override
    @Transactional
    @CacheEvict(value = "produits", allEntries = true)
    public void sortieStockVente(Long produitId, Integer quantite, Long utilisateurId, Long venteId) {
        Produit produit = produitRepository.findById(produitId)
                .orElseThrow(() -> new RessourceIntrouvableException("Produit non trouvé"));

        if (produit.getQuantite() < quantite) {
            throw new StockInsuffisantException("Stock insuffisant. Disponible: " + produit.getQuantite() + ", Demande: " + quantite);
        }

        int ancienneQuantite = produit.getQuantite();
        appliquerDeltaNiveauRacineEtRecalculer(produit, -quantite);
        int nouvelleQuantite = produit.getQuantite();
        produitRepository.save(produit);

        String motif = "Vente #" + venteId;
        enregistrerMouvement(produit, quantite, TypeMouvement.SORTIE,
                ancienneQuantite, nouvelleQuantite, utilisateurId, motif, null, "VENTE", venteId);

        log.info("📦 Stock SORTIE: -{} x {} (Vente #{}) - Stock: {} → {}",
                quantite, produit.getNom(), venteId, ancienneQuantite, nouvelleQuantite);
    }

    @Override
    @Transactional
    @CacheEvict(value = "produits", allEntries = true)
    public void entreeStockRetourVente(Long produitId, Integer quantite, Long utilisateurId, Long retourId) {
        Produit produit = produitRepository.findById(produitId)
                .orElseThrow(() -> new RessourceIntrouvableException("Produit non trouvé"));

        int ancienneQuantite = produit.getQuantite();
        appliquerDeltaNiveauRacineEtRecalculer(produit, quantite);
        int nouvelleQuantite = produit.getQuantite();
        produitRepository.save(produit);

        String motif = "Retour vente #" + retourId;
        enregistrerMouvement(produit, quantite, TypeMouvement.RETOUR,
                ancienneQuantite, nouvelleQuantite, utilisateurId, motif, null, "RETOUR_VENTE", retourId);

        log.info("📦 Stock RETOUR: +{} x {} (Retour Vente #{}) - Stock: {} → {}",
                quantite, produit.getNom(), retourId, ancienneQuantite, nouvelleQuantite);
    }

    @Override
    @Transactional
    @CacheEvict(value = "produits", allEntries = true)
    public void entreeStockBonusFournisseur(Long produitId, Integer quantite, Long objectifId) {
        Produit produit = produitRepository.findById(produitId)
                .orElseThrow(() -> new RessourceIntrouvableException("Produit non trouvé"));

        int ancienneQuantite = produit.getQuantite();
        appliquerDeltaNiveauRacineEtRecalculer(produit, quantite);
        int nouvelleQuantite = produit.getQuantite();
        produitRepository.save(produit);

        String motif = "Bonus fournisseur - Objectif #" + objectifId;
        enregistrerMouvement(produit, quantite, TypeMouvement.BONUS_FOURNISSEUR,
                ancienneQuantite, nouvelleQuantite, null, motif, null, "OBJECTIF_FOURNISSEUR", objectifId);

        log.info("🎁 Stock BONUS_FOURNISSEUR: +{} x {} (Objectif #{}) - Stock: {} → {}",
                quantite, produit.getNom(), objectifId, ancienneQuantite, nouvelleQuantite);
    }

    // ========== MÉTHODE PRIVÉE AMÉLIORÉE ==========

    private void enregistrerMouvement(Produit produit, Integer quantite, TypeMouvement type,
                                      Integer quantiteAvant, Integer quantiteApres,
                                      Long utilisateurId, String motif, Long achatId,
                                      String referenceType, Long referenceId) {
        MouvementStock mouvement = new MouvementStock();
        mouvement.setProduit(produit);
        mouvement.setQuantite(quantite);
        mouvement.setTypeMouvement(type);
        mouvement.setQuantiteAvant(quantiteAvant);
        mouvement.setQuantiteApres(quantiteApres);
        mouvement.setMotif(motif);
        mouvement.setAchatId(achatId);
        mouvement.setReferenceType(referenceType);
        mouvement.setReferenceId(referenceId);

        if (utilisateurId != null) {
            Utilisateur utilisateur = utilisateurRepository.findById(utilisateurId).orElse(null);
            mouvement.setUtilisateur(utilisateur);
        }

        mouvementStockRepository.save(mouvement);
        notificationService.notifierMiseAJourStock(produit.getId(), produit.getNom(), quantiteApres);
    }

    // ========== MÉTHODES DE CONSULTATION ==========

    @Override
    public List<MouvementStock> obtenirHistoriqueProduit(Long produitId) {
        return mouvementStockRepository.findByProduitId(produitId);
    }

    @Override
    public List<MouvementStock> obtenirMouvementsParDate(LocalDateTime debut, LocalDateTime fin) {
        return mouvementStockRepository.findByDateMouvementBetween(debut, fin);
    }

    @Override
    public List<Produit> obtenirProduitsStockFaible() {
        return produitRepository.trouverProduitsStockFaible();
    }

    @Override
    public Map<String, Object> obtenirStatistiquesInventaire() {
        Map<String, Object> stats = new HashMap<>();

        List<Produit> produitsStockFaible = obtenirProduitsStockFaible();
        List<Produit> produitsRupture = produitRepository.trouverProduitsEnRupture();
        Double valeurTotale = produitRepository.getValeurTotaleStock();

        long totalEntrees = mouvementStockRepository.findByTypeMouvement(TypeMouvement.ENTREE).stream()
                .mapToLong(MouvementStock::getQuantite)
                .sum();

        long totalSorties = mouvementStockRepository.findByTypeMouvement(TypeMouvement.SORTIE).stream()
                .mapToLong(MouvementStock::getQuantite)
                .sum();

        stats.put("produitsStockFaible", produitsStockFaible.size());
        stats.put("produitsRupture", produitsRupture.size());
        stats.put("valeurTotaleStock", valeurTotale != null ? valeurTotale : 0);
        stats.put("totalEntrees", totalEntrees);
        stats.put("totalSorties", totalSorties);
        stats.put("variationNet", totalEntrees - totalSorties);

        return stats;
    }

    @Override
    public List<MouvementStock> obtenirSorties(String typeSortie, Long utilisateurId, Long produitId,
                                                LocalDateTime dateDebut, LocalDateTime dateFin) {
        return mouvementStockRepository.findSorties(typeSortie, utilisateurId, produitId, dateDebut, dateFin);
    }

    @Override
    public List<MouvementStock> obtenirMouvementsParAchat(Long achatId) {
        return mouvementStockRepository.findByAchatId(achatId);
    }

    @Override
    public List<MouvementStock> obtenirTousMouvements() {
        return mouvementStockRepository.findAllByOrderByDateMouvementDesc();
    }
}