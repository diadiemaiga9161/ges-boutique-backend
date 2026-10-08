package com.ges.boutique.produit;

import com.ges.boutique.exception.RessourceIntrouvableException;
import com.ges.boutique.inventaire.MouvementStock;
import com.ges.boutique.inventaire.MouvementStockRepository;
import com.ges.boutique.inventaire.TypeMouvement;
import com.ges.boutique.journalaudit.JournalAuditService;
import com.ges.boutique.journalaudit.TypeActionAudit;
import com.ges.boutique.utilisateur.Utilisateur;
import com.ges.boutique.utilisateur.UtilisateurRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/produits")
@RequiredArgsConstructor
public class ProduitNiveauController {

    private final ProduitNiveauRepository niveauRepository;
    private final ProduitRepository produitRepository;
    private final MouvementStockRepository mouvementStockRepository;
    private final UtilisateurRepository utilisateurRepository;
    private final JournalAuditService journalAuditService;

    private Long getUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof Utilisateur u) return u.getId();
        return null;
    }

    @GetMapping("/{produitId}/niveaux")
    @PreAuthorize("hasAnyRole('ADMIN', 'VENDEUR')")
    public ResponseEntity<Map<String, Object>> getNiveaux(@PathVariable Long produitId) {
        List<ProduitNiveau> niveaux = niveauRepository.findByProduitIdOrderByOrdreAsc(produitId);
        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("niveaux", niveaux);
        // Stock du produit principal pas encore décomposé : parent implicite
        // du niveau racine (parentId = null), nécessaire côté client pour
        // calculer la disponibilité réelle en cascade (cf. disponibleNiveau()).
        produitRepository.findById(produitId).ifPresent(p ->
                response.put("quantitePrincipale", p.getQuantitePrincipale() != null ? p.getQuantitePrincipale() : 0));
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{produitId}/niveaux")
    @PreAuthorize("hasRole('ADMIN')")
    @Transactional
    public ResponseEntity<Map<String, Object>> creerNiveau(
            @PathVariable Long produitId,
            @RequestBody ProduitNiveauRequest request) {
        Produit produit = produitRepository.findById(produitId)
                .orElseThrow(() -> new RessourceIntrouvableException("Produit non trouvé: " + produitId));
        if (produit.getModeMesure() != null) {
            throw new IllegalStateException("Ce produit est vendu au " + produit.getModeMesure().getUnite()
                    + " : il ne peut pas avoir de niveaux (carton, paquet...).");
        }

        List<ProduitNiveau> existants = niveauRepository.findByProduitIdOrderByOrdreAsc(produitId);

        // Premier niveau créé pour ce produit : son parent implicite est le
        // produit principal lui-même. On capture son stock actuel dans
        // quantitePrincipale AVANT qu'une synchronisation ne l'écrase (sinon
        // cette quantité est perdue dès le premier ajustement de stock d'un
        // niveau, cf. bug remonté par l'utilisateur).
        if (existants.isEmpty() && produit.getQuantitePrincipale() == null) {
            produit.setQuantitePrincipale(produit.getQuantite() != null ? produit.getQuantite() : 0);
            // Réaffecter le retour de save() : sans @Transactional ici, l'entité
            // est détachée entre les appels et son numéro de @Version ne serait
            // pas rafraîchi en mémoire, ce qui ferait échouer le save() suivant
            // (syncProduitQuantite) avec un OptimisticLockException.
            produit = produitRepository.save(produit);
        }

        ProduitNiveau niveau = new ProduitNiveau();
        niveau.setProduit(produit);
        niveau.setNom(request.getNom());
        niveau.setParentId(request.getParentId());
        niveau.setFacteur(request.getFacteur() != null ? request.getFacteur() : 1);
        niveau.setPrixAchat(request.getPrixAchat() != null ? request.getPrixAchat() : 0.0);
        niveau.setPrixVente(request.getPrixVente() != null ? request.getPrixVente() : 0.0);
        niveau.setStock(request.getStock() != null ? request.getStock() : 0);

        // Calcul automatique de l'ordre depuis la hiérarchie parentId
        if (request.getOrdre() != null) {
            niveau.setOrdre(request.getOrdre());
        } else if (request.getParentId() == null) {
            niveau.setOrdre(1); // racine
        } else {
            int ordreParent = existants.stream()
                    .filter(n -> n.getId().equals(request.getParentId()))
                    .findFirst().map(ProduitNiveau::getOrdre).orElse(0);
            niveau.setOrdre(ordreParent + 1);
        }

        ProduitNiveau saved = niveauRepository.save(niveau);

        // Rafraîchit immédiatement le total affiché (quantite) pour qu'il
        // reflète la cascade dès la création du niveau, sans attendre un
        // premier ajustement de stock.
        List<ProduitNiveau> niveauxApres = niveauRepository.findByProduitIdOrderByOrdreAsc(produitId);
        syncProduitQuantite(produit, niveauxApres);

        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("niveau", saved);
        return ResponseEntity.ok(response);
    }

    @PutMapping("/niveaux/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Transactional
    public ResponseEntity<Map<String, Object>> modifierNiveau(
            @PathVariable Long id,
            @RequestBody ProduitNiveauRequest request) {
        ProduitNiveau niveau = niveauRepository.findById(id)
                .orElseThrow(() -> new RessourceIntrouvableException("Niveau non trouvé: " + id));

        Double ancienPrixAchat = niveau.getPrixAchat();
        Double ancienPrixVente = niveau.getPrixVente();

        if (request.getNom() != null) niveau.setNom(request.getNom());
        if (request.getOrdre() != null) niveau.setOrdre(request.getOrdre());
        if (request.getParentId() != null) niveau.setParentId(request.getParentId());
        if (request.getFacteur() != null) niveau.setFacteur(request.getFacteur());
        if (request.getPrixAchat() != null) niveau.setPrixAchat(request.getPrixAchat());
        if (request.getPrixVente() != null) niveau.setPrixVente(request.getPrixVente());
        if (request.getStock() != null) niveau.setStock(request.getStock());
        ProduitNiveau saved = niveauRepository.save(niveau);

        boolean prixAchatChange = request.getPrixAchat() != null && !request.getPrixAchat().equals(ancienPrixAchat);
        boolean prixVenteChange = request.getPrixVente() != null && !request.getPrixVente().equals(ancienPrixVente);
        if (prixAchatChange || prixVenteChange) {
            StringBuilder details = new StringBuilder("Niveau #" + saved.getId() + " (" + saved.getNom()
                    + ") du produit #" + saved.getProduit().getId() + " : ");
            if (prixVenteChange) {
                details.append("prix vente ").append(ancienPrixVente).append(" F -> ").append(saved.getPrixVente()).append(" F");
            }
            if (prixAchatChange) {
                if (prixVenteChange) details.append(" ; ");
                details.append("prix achat ").append(ancienPrixAchat).append(" F -> ").append(saved.getPrixAchat()).append(" F");
            }

            Utilisateur auteur = null;
            Long userId = getUserId();
            if (userId != null) {
                auteur = utilisateurRepository.findById(userId).orElse(null);
            }
            journalAuditService.enregistrer(
                    auteur != null ? auteur.getId() : null,
                    auteur != null ? auteur.getNomComplet() : null,
                    TypeActionAudit.MODIFICATION_PRIX_PRODUIT,
                    details.toString());
        }

        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("niveau", saved);
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/niveaux/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, Object>> supprimerNiveau(@PathVariable Long id) {
        niveauRepository.findById(id)
                .orElseThrow(() -> new RessourceIntrouvableException("Niveau non trouvé: " + id));
        niveauRepository.deleteById(id);
        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("message", "Niveau supprimé");
        return ResponseEntity.ok(response);
    }

    @PatchMapping("/niveaux/{id}/stock")
    @PreAuthorize("hasAnyRole('ADMIN', 'VENDEUR')")
    public ResponseEntity<Map<String, Object>> ajusterStock(
            @PathVariable Long id,
            @RequestBody Map<String, Integer> body) {
        ProduitNiveau niveau = niveauRepository.findById(id)
                .orElseThrow(() -> new RessourceIntrouvableException("Niveau non trouvé: " + id));
        Integer newStock = body.get("stock");
        if (newStock == null || newStock < 0) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", "Stock invalide"));
        }
        niveau.setStock(newStock);
        niveauRepository.save(niveau);

        // Synchroniser Produit.quantite avec le total des stocks niveaux
        Produit produit = niveau.getProduit();
        List<ProduitNiveau> niveaux = niveauRepository.findByProduitIdOrderByOrdreAsc(produit.getId());
        syncProduitQuantite(produit, niveaux);

        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("niveau", niveau);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/niveaux/{id}/decomposer")
    @PreAuthorize("hasAnyRole('ADMIN', 'VENDEUR')")
    @Transactional
    public ResponseEntity<Map<String, Object>> decomposer(@PathVariable Long id) {
        ProduitNiveau target = niveauRepository.findById(id)
                .orElseThrow(() -> new RessourceIntrouvableException("Niveau non trouvé: " + id));

        Produit produit = target.getProduit();
        List<ProduitNiveau> niveaux = niveauRepository.findByProduitIdOrderByOrdreAsc(produit.getId());

        if (target.getParentId() == null) {
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", target.getNom() + " est déjà le niveau le plus grand. Ajoutez du stock directement."
            ));
        }

        ProduitNiveau parent = niveaux.stream()
                .filter(n -> n.getId().equals(target.getParentId()))
                .findFirst().orElse(null);

        if (parent == null) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", "Niveau parent introuvable."));
        }

        int parentStock = parent.getStock() != null ? parent.getStock() : 0;
        if (parentStock < 1) {
            String gpNom = parent.getParentId() == null ? produit.getNom() :
                    niveaux.stream().filter(n -> n.getId().equals(parent.getParentId()))
                            .findFirst().map(ProduitNiveau::getNom).orElse("le niveau supérieur");
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", "Plus de " + parent.getNom() + " disponible. Ouvrez d'abord un " + gpNom + " → " + parent.getNom()
            ));
        }

        Utilisateur utilisateurCourant = null;
        Long userId = getUserId();
        if (userId != null) {
            utilisateurCourant = utilisateurRepository.findById(userId).orElse(null);
        }

        int targetStockAvant = target.getStock() != null ? target.getStock() : 0;

        parent.setStock(parentStock - 1);
        niveauRepository.save(parent);

        MouvementStock sortieParent = new MouvementStock();
        sortieParent.setProduit(target.getProduit());
        sortieParent.setQuantite(1);
        sortieParent.setTypeMouvement(TypeMouvement.SORTIE);
        sortieParent.setQuantiteAvant(parentStock);
        sortieParent.setQuantiteApres(parentStock - 1);
        sortieParent.setNiveauId(parent.getId());
        sortieParent.setNiveauNom(parent.getNom());
        sortieParent.setMotif("Décomposition");
        sortieParent.setReferenceType("DECOMPOSITION");
        sortieParent.setUtilisateur(utilisateurCourant);
        mouvementStockRepository.save(sortieParent);

        int newStock = targetStockAvant + target.getFacteur();
        target.setStock(newStock);
        niveauRepository.save(target);

        MouvementStock entreeTarget = new MouvementStock();
        entreeTarget.setProduit(target.getProduit());
        entreeTarget.setQuantite(target.getFacteur());
        entreeTarget.setTypeMouvement(TypeMouvement.ENTREE);
        entreeTarget.setQuantiteAvant(targetStockAvant);
        entreeTarget.setQuantiteApres(newStock);
        entreeTarget.setNiveauId(target.getId());
        entreeTarget.setNiveauNom(target.getNom());
        entreeTarget.setMotif("Décomposition depuis " + parent.getNom());
        entreeTarget.setReferenceType("DECOMPOSITION");
        entreeTarget.setUtilisateur(utilisateurCourant);
        mouvementStockRepository.save(entreeTarget);

        List<ProduitNiveau> updatedNiveaux = niveauRepository.findByProduitIdOrderByOrdreAsc(produit.getId());
        syncProduitQuantite(produit, updatedNiveaux);

        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("message", "Ouvert : 1 " + parent.getNom() + " → " + target.getFacteur() + " " + target.getNom());
        response.put("niveaux", updatedNiveaux);
        response.put("produitQuantite", produit.getQuantite());
        return ResponseEntity.ok(response);
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private void syncProduitQuantite(Produit produit, List<ProduitNiveau> niveaux) {
        long total = 0;
        for (ProduitNiveau n : niveaux) {
            long f = facteurVersBase(n, niveaux);
            total += (n.getStock() != null ? n.getStock() : 0) * f;
        }
        // Le stock encore "principal" (non décomposé) alimente le niveau
        // racine (parentId = null) : il faut l'ajouter au total affiché, sinon
        // le produit semble en rupture tant qu'aucun niveau n'a été ouvert.
        if (produit.getQuantitePrincipale() != null && !niveaux.isEmpty()) {
            ProduitNiveau racine = niveaux.stream().filter(n -> n.getParentId() == null).findFirst().orElse(null);
            if (racine != null) {
                long facteurRacine = racine.getFacteur() != null && racine.getFacteur() > 0 ? racine.getFacteur() : 1L;
                total += produit.getQuantitePrincipale() * facteurRacine * facteurVersBase(racine, niveaux);
            }
        }
        produit.setQuantite((int) total);
        produitRepository.save(produit);
    }

    private long facteurVersBase(ProduitNiveau niveau, List<ProduitNiveau> niveaux) {
        ProduitNiveau child = niveaux.stream()
                .filter(n -> niveau.getId().equals(n.getParentId()))
                .findFirst().orElse(null);
        if (child == null) return 1L; // feuille = unité de base
        return child.getFacteur() * facteurVersBase(child, niveaux);
    }

    @DeleteMapping("/{produitId}/niveaux")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, Object>> supprimerTousNiveaux(@PathVariable Long produitId) {
        niveauRepository.deleteByProduitId(produitId);
        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("message", "Tous les niveaux supprimés");
        return ResponseEntity.ok(response);
    }
}
