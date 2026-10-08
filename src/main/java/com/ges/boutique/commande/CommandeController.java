package com.ges.boutique.commande;

import com.ges.boutique.utilisateur.Utilisateur;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/commandes")
@RequiredArgsConstructor
public class CommandeController {

    private final CommandeService commandeService;

    private Long getUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof Utilisateur u) return u.getId();
        return null;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'VENDEUR')")
    public ResponseEntity<List<Commande>> getAll() {
        return ResponseEntity.ok(commandeService.findAll());
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'VENDEUR')")
    public ResponseEntity<Commande> getById(@PathVariable Long id) {
        return ResponseEntity.ok(commandeService.findById(id));
    }

    @GetMapping("/statut/{statut}")
    @PreAuthorize("hasAnyRole('ADMIN', 'VENDEUR')")
    public ResponseEntity<List<Commande>> getByStatut(@PathVariable StatutCommande statut) {
        return ResponseEntity.ok(commandeService.findByStatut(statut));
    }

    /**
     * Commandes vitrine pas encore traitées — appelé à l'ouverture de l'appli/connexion
     * sur les 3 plateformes (pas seulement via le WebSocket temps réel) pour que personne
     * ne rate une commande arrivée pendant qu'il n'était pas connecté.
     */
    @GetMapping("/vitrine-en-attente")
    @PreAuthorize("hasAnyRole('ADMIN', 'VENDEUR')")
    public ResponseEntity<List<Commande>> getVitrineEnAttente() {
        return ResponseEntity.ok(commandeService.trouverVitrineEnAttente());
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'VENDEUR')")
    public ResponseEntity<Map<String, Object>> creer(@RequestBody CommandeRequest request) {
        Commande commande = commandeService.creer(request);
        Map<String, Object> resp = new HashMap<>();
        resp.put("success", true);
        resp.put("message", "Commande créée");
        resp.put("commande", commande);
        return ResponseEntity.ok(resp);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'VENDEUR')")
    public ResponseEntity<Map<String, Object>> modifier(@PathVariable Long id, @RequestBody CommandeRequest request) {
        Commande commande = commandeService.modifier(id, request);
        Map<String, Object> resp = new HashMap<>();
        resp.put("success", true);
        resp.put("message", "Commande modifiée");
        resp.put("commande", commande);
        return ResponseEntity.ok(resp);
    }

    @PostMapping("/{id}/valider")
    @PreAuthorize("hasAnyRole('ADMIN', 'VENDEUR')")
    public ResponseEntity<Map<String, Object>> valider(@PathVariable Long id,
                                                         @RequestBody(required = false) ValidationCommandeRequest infosLivraison) {
        Commande commande = commandeService.valider(id, getUserId(), infosLivraison);
        Map<String, Object> resp = new HashMap<>();
        resp.put("success", true);
        resp.put("message", "Commande validée — vente créée N°" + commande.getVenteId());
        resp.put("commande", commande);
        return ResponseEntity.ok(resp);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'VENDEUR')")
    public ResponseEntity<Map<String, Object>> supprimer(@PathVariable Long id) {
        commandeService.supprimer(id);
        Map<String, Object> resp = new HashMap<>();
        resp.put("success", true);
        resp.put("message", "Commande supprimée");
        return ResponseEntity.ok(resp);
    }

    @PostMapping("/{id}/annuler")
    @PreAuthorize("hasAnyRole('ADMIN', 'VENDEUR')")
    public ResponseEntity<Map<String, Object>> annuler(@PathVariable Long id, @RequestBody(required = false) Map<String, Object> body) {
        Long userId = body != null && body.get("utilisateurId") != null ? Long.valueOf(body.get("utilisateurId").toString()) : null;
        Commande commande = commandeService.annuler(id, userId);
        Map<String, Object> resp = new HashMap<>();
        resp.put("success", true);
        resp.put("message", "Commande annulée" + (commande.getVenteId() != null ? " — vente et stock restaurés" : ""));
        resp.put("commande", commande);
        return ResponseEntity.ok(resp);
    }

    /** Body : {"etape": "PRETE" | "LIVREE" | null}. Réservé aux commandes confirmées. */
    @PatchMapping("/{id}/livraison")
    @PreAuthorize("hasAnyRole('ADMIN', 'VENDEUR')")
    public ResponseEntity<Map<String, Object>> changerEtapeLivraison(@PathVariable Long id,
                                                                     @RequestBody(required = false) Map<String, String> body) {
        String valeur = body == null ? null : body.get("etape");
        EtapeLivraison etape = (valeur == null || valeur.isBlank()) ? null : EtapeLivraison.valueOf(valeur);
        Commande c = commandeService.changerEtapeLivraison(id, etape);
        Map<String, Object> resp = new HashMap<>();
        resp.put("success", true);
        resp.put("commande", c);
        return ResponseEntity.ok(resp);
    }

    @PatchMapping("/{id}/payer-credit")
    @PreAuthorize("hasAnyRole('ADMIN', 'VENDEUR')")
    public ResponseEntity<Map<String, Object>> payerCredit(@PathVariable Long id, @RequestBody Map<String, Double> body) {
        Double montant = body.get("montant");
        if (montant == null || montant <= 0) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", "Montant invalide"));
        }
        Commande commande = commandeService.payerCredit(id, montant);
        Map<String, Object> resp = new HashMap<>();
        resp.put("success", true);
        resp.put("message", "Paiement enregistré");
        resp.put("commande", commande);
        return ResponseEntity.ok(resp);
    }

    @PostMapping("/payer-credits-groupes")
    @PreAuthorize("hasAnyRole('ADMIN', 'VENDEUR')")
    public ResponseEntity<Map<String, Object>> payerCreditsGroupes(@RequestBody Map<String, Object> body) {
        @SuppressWarnings("unchecked")
        List<Long> ids = ((List<Integer>) body.get("ids")).stream().map(Long::valueOf).toList();
        Double montantTotal = ((Number) body.get("montantTotal")).doubleValue();
        List<Commande> commandes = commandeService.payerCreditsGroupes(ids, montantTotal);
        Map<String, Object> resp = new HashMap<>();
        resp.put("success", true);
        resp.put("message", "Règlement groupé enregistré");
        resp.put("commandes", commandes);
        return ResponseEntity.ok(resp);
    }
}
