package com.ges.boutique.vente;

import com.ges.boutique.caisse.CaisseService;
import com.ges.boutique.utilisateur.UtilisateurMapper;
import com.ges.boutique.vente.dto.VenteAnnuleeDTO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Optional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/ventes")
@RequiredArgsConstructor
@Tag(name = "Ventes", description = "Gestion des ventes")
public class VenteController {

    private final VenteService venteService;
    private final VenteMapper venteMapper;
    private final UtilisateurMapper utilisateurMapper;
    private final CaisseService caisseService;
    private final VenteRepository venteRepository;

    // ==================== CRÉATION ====================

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'VENDEUR')")
    @Operation(summary = "Créer une vente (comptant)")
    public ResponseEntity<Map<String, Object>> creerVente(
            @RequestBody VenteRequest request,
            @RequestHeader(value = "X-Client-Request-ID", required = false) String clientRequestId) {

        // Idempotence : si une vente avec ce clientRequestId existe déjà, on la retourne
        if (clientRequestId != null && !clientRequestId.isBlank()) {
            Optional<Vente> existing = venteRepository.findByClientRequestId(clientRequestId);
            if (existing.isPresent()) {
                log.info("Vente déjà créée pour clientRequestId={}, retour sans doublon", clientRequestId);
                Map<String, Object> response = new HashMap<>();
                response.put("success", true);
                response.put("message", "Vente déjà enregistrée");
                response.put("vente", venteMapper.toVenteMap(existing.get()));
                return ResponseEntity.ok(response);
            }
            request.setClientRequestId(clientRequestId);
        }

        Vente vente = venteService.creerVente(request);
        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("message", "Vente créée avec succès");
        response.put("vente", venteMapper.toVenteMap(vente));
        return ResponseEntity.ok(response);
    }

    @PostMapping("/credit")
    @PreAuthorize("hasAnyRole('ADMIN', 'VENDEUR')")
    @Operation(summary = "Créer une vente à crédit")
    public ResponseEntity<Map<String, Object>> creerVenteCredit(
            @RequestBody VenteCreditRequest request,
            @RequestHeader(value = "X-Client-Request-ID", required = false) String clientRequestId) {

        // Idempotence : si un crédit avec ce clientRequestId existe déjà, on le retourne
        if (clientRequestId != null && !clientRequestId.isBlank()) {
            Optional<Vente> existing = venteRepository.findByClientRequestId(clientRequestId);
            if (existing.isPresent()) {
                log.info("Crédit déjà créé pour clientRequestId={}, retour sans doublon", clientRequestId);
                Map<String, Object> response = new HashMap<>();
                response.put("success", true);
                response.put("message", "Crédit déjà enregistré");
                response.put("vente", venteMapper.toVenteMap(existing.get()));
                return ResponseEntity.ok(response);
            }
            request.setClientRequestId(clientRequestId);
        }

        Vente vente = venteService.creerVenteCredit(request);
        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("message", "Crédit créé avec succès");
        response.put("vente", venteMapper.toVenteMap(vente));
        return ResponseEntity.ok(response);
    }

    // ==================== LECTURE ====================

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'VENDEUR')")
    @Operation(summary = "Obtenir une vente par ID")
    public ResponseEntity<Map<String, Object>> obtenirVenteParId(@PathVariable Long id) {
        Vente vente = venteService.obtenirVenteParId(id);
        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("vente", venteMapper.toVenteMap(vente));
        return ResponseEntity.ok(response);
    }

    @GetMapping("/credit/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'VENDEUR')")
    @Operation(summary = "Obtenir un crédit par ID")
    public ResponseEntity<Map<String, Object>> obtenirVenteCreditParId(@PathVariable Long id) {
        Vente vente = venteService.obtenirVenteCreditParId(id);
        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("vente", venteMapper.toVenteMap(vente));
        return ResponseEntity.ok(response);
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'VENDEUR')")
    @Operation(summary = "Obtenir toutes les ventes")
    public ResponseEntity<List<Map<String, Object>>> obtenirToutesVentes(
            @RequestParam(required = false, defaultValue = "false") boolean inclureAnnulees) {
        List<Vente> ventes = venteService.obtenirToutesVentes(inclureAnnulees);
        return ResponseEntity.ok(venteMapper.toVenteMapList(ventes));
    }

    @GetMapping("/credits")
    @PreAuthorize("hasAnyRole('ADMIN', 'VENDEUR')")
    @Operation(summary = "Obtenir tous les crédits")
    public ResponseEntity<List<Map<String, Object>>> obtenirTousCredits() {
        List<Vente> credits = venteService.obtenirTousCredits();
        return ResponseEntity.ok(venteMapper.toVenteMapList(credits));
    }

    @GetMapping("/credits/regles")
    @PreAuthorize("hasAnyRole('ADMIN', 'VENDEUR')")
    @Operation(summary = "Obtenir les crédits réglés")
    public ResponseEntity<Map<String, Object>> obtenirCreditsRegles() {
        List<Vente> credits = venteService.obtenirCreditsRegles();
        Map<String, Object> response = new HashMap<>();
        response.put("credits", venteMapper.toVenteMapList(credits));
        response.put("nombreCredits", credits.size());
        response.put("montantTotal", credits.stream().mapToDouble(Vente::getMontantTotal).sum());
        return ResponseEntity.ok(response);
    }

    @GetMapping("/credits/non-regles")
    @PreAuthorize("hasAnyRole('ADMIN', 'VENDEUR')")
    @Operation(summary = "Obtenir les crédits non réglés")
    public ResponseEntity<Map<String, Object>> obtenirCreditsNonRegles() {
        List<Vente> credits = venteService.obtenirCreditsNonRegles();
        Map<String, Object> response = new HashMap<>();
        response.put("credits", venteMapper.toVenteMapList(credits));
        response.put("nombreCredits", credits.size());
        response.put("montantTotal", credits.stream().mapToDouble(Vente::getMontantRestant).sum());
        return ResponseEntity.ok(response);
    }

    @GetMapping("/credits/en-retard")
    @PreAuthorize("hasAnyRole('ADMIN', 'VENDEUR')")
    @Operation(summary = "Obtenir les crédits en retard")
    public ResponseEntity<Map<String, Object>> obtenirCreditsEnRetard() {
        List<Vente> credits = venteService.obtenirCreditsEnRetard();
        Map<String, Object> response = new HashMap<>();
        response.put("credits", venteMapper.toVenteMapList(credits));
        response.put("nombreCredits", credits.size());
        response.put("montantTotal", credits.stream().mapToDouble(Vente::getMontantRestant).sum());
        return ResponseEntity.ok(response);
    }

    @GetMapping("/credits/client")
    @PreAuthorize("hasAnyRole('ADMIN', 'VENDEUR')")
    @Operation(summary = "Obtenir les crédits par client")
    public ResponseEntity<List<Map<String, Object>>> obtenirCreditsParClient(@RequestParam String clientNom) {
        List<Vente> credits = venteService.obtenirCreditsParClient(clientNom);
        return ResponseEntity.ok(venteMapper.toVenteMapList(credits));
    }

    @GetMapping("/credits/by-client/{clientId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'VENDEUR')")
    @Operation(summary = "Obtenir les crédits d'un client par son id (situation client)")
    public ResponseEntity<List<Map<String, Object>>> obtenirCreditsParClientId(@PathVariable Long clientId) {
        List<Vente> credits = venteService.obtenirCreditsParClientId(clientId);
        return ResponseEntity.ok(venteMapper.toVenteMapList(credits));
    }

    @GetMapping("/by-client/{clientId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'VENDEUR')")
    @Operation(summary = "Obtenir toutes les ventes (comptant + credit) d'un client par son id, filtre cote serveur")
    public ResponseEntity<List<Map<String, Object>>> obtenirVentesParClientId(@PathVariable Long clientId) {
        List<Vente> ventes = venteService.obtenirVentesParClientId(clientId);
        return ResponseEntity.ok(venteMapper.toVenteMapList(ventes));
    }

    @GetMapping("/vendeur/{vendeurId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'VENDEUR')")
    @Operation(summary = "Obtenir les ventes par vendeur")
    public ResponseEntity<List<Map<String, Object>>> obtenirVentesParVendeur(@PathVariable Long vendeurId) {
        List<Vente> ventes = venteService.obtenirVentesParVendeur(vendeurId);
        return ResponseEntity.ok(venteMapper.toVenteMapList(ventes));
    }

    @GetMapping("/periode")
    @PreAuthorize("hasAnyRole('ADMIN', 'VENDEUR')")
    @Operation(summary = "Obtenir les ventes par période")
    public ResponseEntity<List<Map<String, Object>>> obtenirVentesParDateRange(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateDebut,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFin) {
        List<Vente> ventes = venteService.obtenirVentesParDateRange(dateDebut, dateFin);
        return ResponseEntity.ok(venteMapper.toVenteMapList(ventes));
    }

    @GetMapping("/aujourdhui")
    @PreAuthorize("hasAnyRole('ADMIN', 'VENDEUR')")
    @Operation(summary = "Obtenir les ventes du jour")
    public ResponseEntity<Map<String, Object>> obtenirVentesDuJour() {
        List<Vente> ventes = venteService.obtenirVentesDuJour();
        Map<String, Object> response = new HashMap<>();
        response.put("ventes", venteMapper.toVenteMapList(ventes));
        response.put("nombreVentes", ventes.size());
        response.put("montantTotal", ventes.stream().mapToDouble(Vente::getMontantTotal).sum());
        response.put("beneficeTotal", ventes.stream().mapToDouble(Vente::getBeneficeTotal).sum());
        return ResponseEntity.ok(response);
    }

    /**
     * Ventes annulées avec noms résolus du vendeur et de l'annuleur.
     * Le path variable {boutiqueId} est conservé pour cohérence API multi-boutiques ;
     * il est transmis au service mais non utilisé en filtrage (une instance = une boutique).
     */
    @GetMapping("/{boutiqueId}/annulees")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Obtenir les ventes annulées avec noms vendeur et annuleur")
    public ResponseEntity<List<VenteAnnuleeDTO>> obtenirVentesAnnulees(@PathVariable Long boutiqueId) {
        return ResponseEntity.ok(venteService.obtenirVentesAnnulees(boutiqueId));
    }

    /**
     * Crédits actifs (non annulés) — endpoint optimisé pour éviter le chargement
     * de toutes les ventes côté Ionic/Angular.
     * Le path variable {boutiqueId} est conservé pour cohérence API multi-boutiques.
     */
    @GetMapping("/{boutiqueId}/credits-actifs")
    @PreAuthorize("hasAnyRole('ADMIN', 'VENDEUR')")
    @Operation(summary = "Obtenir les crédits actifs (endpoint optimisé)")
    public ResponseEntity<List<Vente>> obtenirCreditsActifs(@PathVariable Long boutiqueId) {
        return ResponseEntity.ok(venteService.obtenirCreditsActifs(boutiqueId));
    }

    // ==================== MODIFICATION ====================

    @PutMapping("/{venteId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'VENDEUR')")
    @Operation(summary = "Modifier une vente")
    public ResponseEntity<Map<String, Object>> modifierVente(@PathVariable Long venteId, @RequestBody VenteRequest request) {
        Vente vente = venteService.modifierVente(venteId, request);
        return ResponseEntity.ok(venteMapper.toVenteMap(vente));
    }

    @PutMapping("/credits/{venteId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'VENDEUR')")
    @Operation(summary = "Modifier un crédit")
    public ResponseEntity<Map<String, Object>> modifierVenteCredit(@PathVariable Long venteId, @RequestBody VenteCreditRequest request) {
        Vente vente = venteService.modifierVenteCredit(venteId, request);
        return ResponseEntity.ok(venteMapper.toVenteMap(vente));
    }

    @PutMapping("/{venteId}/modifier-lignes")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Modifier les produits d'une vente avec ajustement de caisse (différence entree/sortie)")
    public ResponseEntity<Map<String, Object>> modifierLignesVente(
            @PathVariable Long venteId,
            @RequestBody ModificationLignesRequest request) {
        Map<String, Object> result = venteService.modifierLignesVente(venteId, request);
        result.put("success", true);
        result.put("message", "Vente modifiée avec succès");
        return ResponseEntity.ok(result);
    }

    // ==================== REMISES ====================

    @PostMapping("/{venteId}/remise-globale")
    @PreAuthorize("hasAnyRole('ADMIN', 'VENDEUR')")
    @Operation(summary = "Appliquer une remise globale")
    public ResponseEntity<Map<String, Object>> appliquerRemiseGlobale(
            @PathVariable Long venteId,
            @RequestParam Double remise,
            @RequestParam RemiseType type) {
        Vente vente = venteService.appliquerRemiseGlobale(venteId, remise, type);
        return ResponseEntity.ok(venteMapper.toVenteMap(vente));
    }

    @DeleteMapping("/{venteId}/remise-globale")
    @PreAuthorize("hasAnyRole('ADMIN', 'VENDEUR')")
    @Operation(summary = "Annuler la remise globale")
    public ResponseEntity<Map<String, Object>> annulerRemiseGlobale(@PathVariable Long venteId) {
        Vente vente = venteService.annulerRemiseGlobale(venteId);
        return ResponseEntity.ok(venteMapper.toVenteMap(vente));
    }

    @PostMapping("/lignes/{ligneId}/remise")
    @PreAuthorize("hasAnyRole('ADMIN', 'VENDEUR')")
    @Operation(summary = "Appliquer une remise sur une ligne")
    public ResponseEntity<Map<String, Object>> appliquerRemiseLigne(
            @PathVariable Long ligneId,
            @RequestParam Double remise,
            @RequestParam RemiseType type) {
        LigneVente ligne = venteService.appliquerRemiseLigne(ligneId, remise, type);
        return ResponseEntity.ok(venteMapper.toLigneMap(ligne));
    }

    @DeleteMapping("/lignes/{ligneId}/remise")
    @PreAuthorize("hasAnyRole('ADMIN', 'VENDEUR')")
    @Operation(summary = "Annuler la remise sur une ligne")
    public ResponseEntity<Map<String, Object>> annulerRemiseLigne(@PathVariable Long ligneId) {
        LigneVente ligne = venteService.annulerRemiseLigne(ligneId);
        return ResponseEntity.ok(venteMapper.toLigneMap(ligne));
    }

    // ==================== RÈGLEMENTS CRÉDIT ====================

    @PostMapping("/credits/{venteId}/reglement")
    @PreAuthorize("hasAnyRole('ADMIN', 'VENDEUR')")
    @Operation(summary = "Enregistrer un règlement de crédit")
    public ResponseEntity<Map<String, Object>> enregistrerReglementCredit(
            @PathVariable Long venteId,
            @RequestBody ReglementCreditRequest request) {
        request.setVenteId(venteId);
        Vente vente = venteService.enregistrerReglementCredit(venteId, request);
        return ResponseEntity.ok(venteMapper.toVenteMap(vente));
    }

    // ==================== SUPPRESSION ET ANNULATION ====================

    @DeleteMapping("/{venteId}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Supprimer définitivement une vente")
    public ResponseEntity<Map<String, Object>> supprimerVente(@PathVariable Long venteId) {
        venteService.supprimerVente(venteId);
        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("message", "Vente supprimée avec succès");
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/credits/{venteId}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Supprimer définitivement un crédit")
    public ResponseEntity<Map<String, Object>> supprimerVenteCredit(@PathVariable Long venteId) {
        venteService.supprimerVenteCredit(venteId);
        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("message", "Crédit supprimé avec succès");
        return ResponseEntity.ok(response);
    }

    /**
     * Annuler une vente (comptant)
     * @param venteId ID de la vente
     * @param utilisateurId ID de l'utilisateur qui annule
     * @param motif Motif de l'annulation
     * @param repercuterCaisse Si true, répercute l'annulation sur la caisse (retire le montant)
     */
    @PostMapping("/{venteId}/annuler")
    @PreAuthorize("hasAnyRole('ADMIN', 'VENDEUR')")
    @Operation(summary = "Annuler une vente avec répercussion optionnelle sur la caisse")
    public ResponseEntity<Map<String, Object>> annulerVente(
            @PathVariable Long venteId,
            @RequestParam(required = false) Long utilisateurId,
            @RequestParam(required = false) String motif,
            @RequestParam(required = false, defaultValue = "true") boolean repercuterCaisse) {

        log.info("=== ANNULATION VENTE ===");
        log.info("Vente ID: {}, Utilisateur: {}, Motif: {}, Répercuter en caisse: {}",
                venteId, utilisateurId, motif, repercuterCaisse);

        Vente vente = venteService.obtenirVenteParId(venteId);

        // Si la vente est déjà annulée, ne rien faire
        if (Boolean.TRUE.equals(vente.getAnnulee())) {
            log.warn("La vente {} est déjà annulée", vente.getNumeroVente());
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "Cette vente est déjà annulée");
            response.put("vente", venteMapper.toVenteMap(vente));
            response.put("dejaAnnulee", true);
            return ResponseEntity.ok(response);
        }

        // BUG FIX (audit comptable) : cet appel direct à caisseService.annulerVenteAvecRepercussion
        // faisait doublon avec l'appel identique déjà effectué à l'intérieur de
        // venteService.annulerVente() ci-dessous (VenteServiceImpl.annulerVente appelle
        // caisseService.annulerVente(vente,...) de façon inconditionnelle dès qu'un
        // paiement a été reçu). Comme CaisseServiceImpl.annulerVenteAvecRepercussion ne
        // marque jamais vente.annulee=true lui-même (seul VenteServiceImpl le fait, à la
        // toute fin), le garde-fou "déjà annulée" de la caisse ne se déclenchait pas
        // entre les deux appels : la caisse était débitée DEUX FOIS du montant de la
        // vente à chaque annulation avec repercuterCaisse=true (comportement par défaut).
        // repercuterCaisse=false ne protégeait déjà pas de ce doublon (le second appel,
        // interne à venteService.annulerVente, est inconditionnel) — ce paramètre est
        // conservé uniquement pour compatibilité avec le frontend existant.
        if (!repercuterCaisse) {
            log.warn("repercuterCaisse=false demandé pour la vente {} mais ignoré : la répercussion caisse est " +
                    "désormais gérée uniquement par venteService.annulerVente (source unique, plus de doublon)", vente.getNumeroVente());
        }

        // Annuler la vente dans le service vente (gère aussi la répercussion caisse)
        Vente venteAnnulee = venteService.annulerVente(venteId, utilisateurId, motif);

        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("message", "Vente annulée avec succès");
        response.put("vente", venteMapper.toVenteMap(venteAnnulee));
        response.put("repercuterCaisse", repercuterCaisse);
        return ResponseEntity.ok(response);
    }

    /**
     * Annuler un crédit
     * @param venteId ID du crédit
     * @param utilisateurId ID de l'utilisateur qui annule
     * @param motif Motif de l'annulation
     * @param repercuterCaisse Si true, répercute l'annulation sur la caisse
     */
    @PostMapping("/credits/{venteId}/annuler")
    @PreAuthorize("hasAnyRole('ADMIN', 'VENDEUR')")
    @Operation(summary = "Annuler un crédit avec répercussion optionnelle sur la caisse")
    public ResponseEntity<Map<String, Object>> annulerVenteCredit(
            @PathVariable Long venteId,
            @RequestParam(required = false) Long utilisateurId,
            @RequestParam(required = false) String motif,
            @RequestParam(required = false, defaultValue = "true") boolean repercuterCaisse) {

        log.info("=== ANNULATION CRÉDIT ===");
        log.info("Crédit ID: {}, Utilisateur: {}, Motif: {}, Répercuter en caisse: {}",
                venteId, utilisateurId, motif, repercuterCaisse);

        Vente vente = venteService.obtenirVenteParId(venteId);

        // Vérifier que c'est bien un crédit
        if (!Boolean.TRUE.equals(vente.getEstCredit())) {
            log.error("La vente {} n'est pas un crédit", vente.getNumeroVente());
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("error", "Cette vente n'est pas un crédit");
            return ResponseEntity.badRequest().body(errorResponse);
        }

        // Si le crédit est déjà annulé, ne rien faire
        if (Boolean.TRUE.equals(vente.getAnnulee())) {
            log.warn("Le crédit {} est déjà annulé", vente.getNumeroVente());
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "Ce crédit est déjà annulé");
            response.put("vente", venteMapper.toVenteMap(vente));
            response.put("dejaAnnulee", true);
            return ResponseEntity.ok(response);
        }

        // Vérifier si le crédit a déjà été payé partiellement
        if (vente.getMontantVerse() != null && vente.getMontantVerse() > 0 && repercuterCaisse) {
            log.warn("Le crédit a déjà été partiellement payé ({} FCFA). L'annulation ne modifie pas le solde de la caisse.",
                    vente.getMontantVerse());
        }

        // BUG FIX (audit comptable) : même doublon que sur /{venteId}/annuler — cet appel
        // direct à caisseService.annulerVenteCreditAvecRepercussion faisait doublon avec
        // l'appel identique déjà effectué à l'intérieur de venteService.annulerVenteCredit()
        // ci-dessous (qui délègue à annulerVente(), lequel appelle caisseService.annulerVenteCredit
        // de façon inconditionnelle dès qu'un paiement a été reçu). Comme
        // CaisseServiceImpl.annulerVenteCreditAvecRepercussion ne marque jamais
        // vente.annulee=true lui-même, son garde-fou "déjà annulé" ne se déclenchait pas
        // entre les deux appels : la caisse était débitée DEUX FOIS du montant versé à
        // chaque annulation de crédit payé avec repercuterCaisse=true (comportement par
        // défaut) — sans même le garde-fou de solde insuffisant qui existe pour les ventes
        // comptant (aucune vérification sur ce chemin), donc silencieux.
        if (!repercuterCaisse) {
            log.warn("repercuterCaisse=false demandé pour le crédit {} mais ignoré : la répercussion caisse est " +
                    "désormais gérée uniquement par venteService.annulerVenteCredit (source unique, plus de doublon)", vente.getNumeroVente());
        }

        // Annuler le crédit dans le service vente (gère aussi la répercussion caisse)
        Vente venteAnnulee = venteService.annulerVenteCredit(venteId, utilisateurId, motif);

        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("message", "Crédit annulé avec succès");
        response.put("vente", venteMapper.toVenteMap(venteAnnulee));
        response.put("repercuterCaisse", repercuterCaisse);
        return ResponseEntity.ok(response);
    }

    // ==================== STATISTIQUES ====================

    @GetMapping("/statistiques/chiffre-affaire")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Statistiques chiffre d'affaires")
    public ResponseEntity<Map<String, Object>> obtenirStatistiquesChiffreAffaire() {
        return ResponseEntity.ok(venteService.obtenirStatistiquesChiffreAffaire());
    }

    @GetMapping("/statistiques/journalieres")
    @PreAuthorize("hasAnyRole('ADMIN', 'VENDEUR')")
    @Operation(summary = "Statistiques journalières")
    public ResponseEntity<Map<String, Object>> obtenirStatistiquesJournalieres(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return ResponseEntity.ok(venteService.obtenirStatistiquesJournalieres(date));
    }

    @GetMapping("/statistiques/hebdomadaires")
    @PreAuthorize("hasAnyRole('ADMIN', 'VENDEUR')")
    @Operation(summary = "Statistiques hebdomadaires")
    public ResponseEntity<Map<String, Object>> obtenirStatistiquesHebdomadaires() {
        return ResponseEntity.ok(venteService.obtenirStatistiquesHebdomadaires());
    }

    @GetMapping("/statistiques/mensuelles")
    @PreAuthorize("hasAnyRole('ADMIN', 'VENDEUR')")
    @Operation(summary = "Statistiques mensuelles")
    public ResponseEntity<Map<String, Object>> obtenirStatistiquesMensuelles() {
        return ResponseEntity.ok(venteService.obtenirStatistiquesMensuelles());
    }

    @GetMapping("/statistiques/credits")
    @PreAuthorize("hasAnyRole('ADMIN', 'VENDEUR')")
    @Operation(summary = "Statistiques des crédits")
    public ResponseEntity<Map<String, Object>> getStatistiquesCredits() {
        return ResponseEntity.ok(venteService.getStatistiquesCredits());
    }

    @GetMapping("/statistiques/nombre-periodes")
    @PreAuthorize("hasAnyRole('ADMIN', 'VENDEUR')")
    @Operation(summary = "Compter les ventes sur une période")
    public ResponseEntity<Map<String, Object>> compterVentesParDateRange(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime debut,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime fin) {
        Long count = venteService.compterVentesParDateRange(debut, fin);
        Map<String, Object> response = new HashMap<>();
        response.put("debut", debut);
        response.put("fin", fin);
        response.put("nombreVentes", count);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/statistiques/vendeur/{vendeurId}/ca")
    @PreAuthorize("hasAnyRole('ADMIN', 'VENDEUR')")
    @Operation(summary = "Chiffre d'affaires par vendeur")
    public ResponseEntity<Map<String, Object>> obtenirChiffreAffaireVendeur(@PathVariable Long vendeurId) {
        Double ca = venteService.obtenirChiffreAffaireVendeur(vendeurId);
        Map<String, Object> response = new HashMap<>();
        response.put("vendeurId", vendeurId);
        response.put("chiffreAffaire", ca);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/statistiques/top-clients")
    @PreAuthorize("hasAnyRole('ADMIN', 'VENDEUR')")
    @Operation(summary = "Top clients")
    public ResponseEntity<List<Map<String, Object>>> obtenirTopClients() {
        return ResponseEntity.ok(venteService.obtenirTopClients());
    }

    @GetMapping("/statistiques/top-produits/quantite")
    @PreAuthorize("hasAnyRole('ADMIN', 'VENDEUR')")
    @Operation(summary = "Top produits par quantité")
    public ResponseEntity<List<Map<String, Object>>> obtenirTopProduitsParQuantite() {
        return ResponseEntity.ok(venteService.obtenirTopProduitsParQuantite());
    }

    @GetMapping("/statistiques/top-produits/ca")
    @PreAuthorize("hasAnyRole('ADMIN', 'VENDEUR')")
    @Operation(summary = "Top produits par chiffre d'affaires")
    public ResponseEntity<List<Map<String, Object>>> obtenirTopProduitsParChiffreAffaire() {
        return ResponseEntity.ok(venteService.obtenirTopProduitsParChiffreAffaire());
    }

    @GetMapping("/statistiques/top-produits")
    @PreAuthorize("hasAnyRole('ADMIN', 'VENDEUR')")
    @Operation(summary = "Top produits (les deux classements)")
    public ResponseEntity<Map<String, Object>> obtenirTopProduits() {
        Map<String, Object> result = new HashMap<>();
        result.put("parQuantite", venteService.obtenirTopProduitsParQuantite());
        result.put("parChiffreAffaire", venteService.obtenirTopProduitsParChiffreAffaire());
        return ResponseEntity.ok(result);
    }
}