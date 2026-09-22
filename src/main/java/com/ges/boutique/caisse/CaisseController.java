package com.ges.boutique.caisse;

import com.ges.boutique.exception.MessageErreurUtil;
import com.ges.boutique.exception.RessourceIntrouvableException;
import com.ges.boutique.exception.SoldeInsuffisantException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.TemporalAdjusters;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.transaction.annotation.Transactional;

@Transactional
@RestController
@RequestMapping("/api/caisse")
@RequiredArgsConstructor
@Tag(name = "Caisse", description = "Gestion complète de la caisse")
public class CaisseController {

    private final CaisseService caisseService;

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'VENDEUR')")
    @Operation(summary = "Test de connexion")
    public ResponseEntity<Map<String, Object>> test() {
        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("message", "API Caisse disponible");
        return ResponseEntity.ok(response);
    }

    // ==================== GESTION DES CAISSES ====================

    @PostMapping("/ouvrir")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Ouvrir la caisse")
    public ResponseEntity<Map<String, Object>> ouvrirCaisse() {
        Caisse caisse = caisseService.ouvrirCaisse();
        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("message", "Caisse ouverte avec succès");
        response.put("caisse", caisse);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/fermer")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Fermer la caisse")
    public ResponseEntity<Map<String, Object>> fermerCaisse(@RequestParam(required = false) Long utilisateurId) {
        Caisse caisse = caisseService.fermerCaisse(utilisateurId);
        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("message", "Caisse fermée avec succès");
        response.put("caisse", caisse);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/verifier")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Vérifier la caisse avec solde réel")
    public ResponseEntity<Map<String, Object>> verifierCaisse(@RequestBody VerificationCaisseRequest request) {
        Caisse caisse = caisseService.verifierCaisse(
                request.getSoldeReelSaisi(),
                request.getUtilisateurId(),
                request.getObservations()
        );
        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("message", "Caisse vérifiée avec succès");
        response.put("caisse", caisse);
        response.put("ecart", caisse.getEcart());
        return ResponseEntity.ok(response);
    }

    @GetMapping("/etat")
    @PreAuthorize("hasAnyRole('ADMIN', 'VENDEUR')")
    @Operation(summary = "Obtenir l'état de la caisse")
    public ResponseEntity<Map<String, Object>> getEtatCaisse() {
        Caisse caisse = caisseService.getCaisseOuverte();
        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("caisse", caisse);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/solde")
    @PreAuthorize("hasAnyRole('ADMIN', 'VENDEUR')")
    @Operation(summary = "Obtenir le solde actuel de la caisse")
    public ResponseEntity<Map<String, Object>> getSolde() {
        Double solde = caisseService.getSoldeActuel();
        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("solde", solde);
        response.put("soldeSysteme", caisseService.getSoldeSysteme());
        return ResponseEntity.ok(response);
    }

    @GetMapping("/ecart")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Obtenir l'écart de caisse")
    public ResponseEntity<Map<String, Object>> getEcart() {
        Map<String, Object> ecart = caisseService.getEcartCaisse();
        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("ecart", ecart);
        return ResponseEntity.ok(response);
    }

    // ==================== OPÉRATIONS DE CAISSE ====================

    @PostMapping("/entree")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Ajouter une entrée en caisse")
    public ResponseEntity<Map<String, Object>> entreeCaisse(@RequestBody CaisseRequest request) {
        OperationCaisse operation = caisseService.entreeCaisse(
                request.getMontant(),
                request.getMotif(),
                request.getUtilisateurId(),
                request.getModePaiement(),
                request.getReferencePaiement()
        );
        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("message", "Entrée en caisse enregistrée avec succès");
        response.put("operation", operation);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/sortie")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Ajouter une sortie de caisse")
    public ResponseEntity<Map<String, Object>> sortieCaisse(@RequestBody CaisseRequest request) {
        OperationCaisse operation = caisseService.sortieCaisse(
                request.getMontant(),
                request.getMotif(),
                request.getUtilisateurId()
        );
        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("message", "Sortie de caisse enregistrée avec succès");
        response.put("operation", operation);
        return ResponseEntity.ok(response);
    }

    // ==================== GESTION DES CRÉDITS ====================

    @GetMapping("/credits")
    @PreAuthorize("hasAnyRole('ADMIN', 'VENDEUR')")
    @Operation(summary = "Obtenir tous les crédits non réglés")
    public ResponseEntity<Map<String, Object>> getCreditsNonRegles() {
        List<OperationCaisse> credits = caisseService.getCreditsNonRegles();
        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("credits", credits);
        response.put("nombreCredits", credits.size());
        response.put("montantTotal", credits.stream().mapToDouble(OperationCaisse::getMontantRestant).sum());
        return ResponseEntity.ok(response);
    }

    @GetMapping("/credits/retard")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Obtenir les crédits en retard")
    public ResponseEntity<Map<String, Object>> getCreditsEnRetard() {
        List<OperationCaisse> credits = caisseService.getCreditsEnRetard();
        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("creditsEnRetard", credits);
        response.put("nombreCreditsEnRetard", credits.size());
        response.put("montantTotalRetard", credits.stream().mapToDouble(OperationCaisse::getMontantRestant).sum());
        return ResponseEntity.ok(response);
    }

    @PostMapping("/credits/reglement")
    @PreAuthorize("hasAnyRole('ADMIN', 'VENDEUR')")
    @Operation(summary = "Enregistrer le règlement d'un crédit")
    public ResponseEntity<Map<String, Object>> reglementCredit(@RequestBody ReglementCreditRequest request) {
        OperationCaisse reglement = caisseService.reglementCredit(
                request.getVenteCreditId(),
                request.getMontantRegle(),
                request.getUtilisateurId(),
                request.getModePaiement(),
                request.getReferencePaiement(),
                request.getMotif(),
                request.getReferenceGroupe()
        );
        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("message", "Règlement de crédit enregistré avec succès");
        response.put("reglement", reglement);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/credits/situation")
    @PreAuthorize("hasAnyRole('ADMIN', 'VENDEUR')")
    @Operation(summary = "Obtenir la situation complète des crédits")
    public ResponseEntity<Map<String, Object>> getSituationCredits() {
        Map<String, Object> situation = caisseService.getSituationCredits();
        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("situation", situation);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/credits/{venteCreditId}/reglements")
    @PreAuthorize("hasAnyRole('ADMIN', 'VENDEUR')")
    @Operation(summary = "Obtenir l'historique des règlements d'un crédit")
    public ResponseEntity<Map<String, Object>> getHistoriqueReglementsCredit(@PathVariable Long venteCreditId) {
        List<OperationCaisse> reglements = caisseService.getHistoriqueReglementsCredit(venteCreditId);
        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("reglements", reglements);
        response.put("nombreReglements", reglements.size());
        return ResponseEntity.ok(response);
    }

    @GetMapping("/credits/reglements")
    @PreAuthorize("hasAnyRole('ADMIN', 'VENDEUR')")
    @Operation(summary = "Lister tous les règlements de crédit (optionnel: filtré par période)")
    public ResponseEntity<?> getReglementsParPeriode(
            @RequestParam(required = false) String dateDebut,
            @RequestParam(required = false) String dateFin) {
        return ResponseEntity.ok(caisseService.getReglementsParPeriode(dateDebut, dateFin));
    }

    @PostMapping("/credits/reglement/{operationId}/annuler")
    @PreAuthorize("hasAnyRole('ADMIN', 'VENDEUR')")
    @Operation(summary = "Annuler un règlement de crédit (reverse l'entrée caisse et la dette)")
    public ResponseEntity<?> annulerReglementCredit(
            @PathVariable Long operationId,
            @RequestParam Long utilisateurId) {
        return ResponseEntity.ok(caisseService.annulerReglementCredit(operationId, utilisateurId));
    }

    // ==================== OPÉRATIONS PAR PÉRIODE ====================

    @GetMapping("/operations/aujourdhui")
    @PreAuthorize("hasAnyRole('ADMIN', 'VENDEUR')")
    @Operation(summary = "Obtenir les opérations du jour")
    public ResponseEntity<Map<String, Object>> getOperationsDuJour() {
        List<OperationCaisse> operations = caisseService.getOperationsDuJour();
        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("operations", operations);
        response.put("nombreOperations", operations.size());
        return ResponseEntity.ok(response);
    }

    @GetMapping("/operations/semaine")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Obtenir les opérations de la semaine")
    public ResponseEntity<Map<String, Object>> getOperationsDeLaSemaine() {
        List<OperationCaisse> operations = caisseService.getOperationsDeLaSemaine();
        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("operations", operations);
        response.put("nombreOperations", operations.size());
        return ResponseEntity.ok(response);
    }

    @GetMapping("/operations/mois")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Obtenir les opérations du mois")
    public ResponseEntity<Map<String, Object>> getOperationsDuMois() {
        List<OperationCaisse> operations = caisseService.getOperationsDuMois();
        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("operations", operations);
        response.put("nombreOperations", operations.size());
        return ResponseEntity.ok(response);
    }

    @GetMapping("/operations/annee")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Obtenir les opérations de l'année")
    public ResponseEntity<Map<String, Object>> getOperationsDeLAnnee() {
        List<OperationCaisse> operations = caisseService.getOperationsDeLAnnee();
        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("operations", operations);
        response.put("nombreOperations", operations.size());
        return ResponseEntity.ok(response);
    }

    @GetMapping("/operations/periode")
    @PreAuthorize("hasAnyRole('ADMIN', 'VENDEUR')")
    @Operation(summary = "Obtenir les opérations sur une période personnalisée")
    public ResponseEntity<Map<String, Object>> getOperationsParPeriode(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateDebut,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFin) {
        List<OperationCaisse> operations = caisseService.getOperationsParPeriode(dateDebut, dateFin);
        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("operations", operations);
        response.put("nombreOperations", operations.size());
        response.put("dateDebut", dateDebut);
        response.put("dateFin", dateFin);
        return ResponseEntity.ok(response);
    }

    // ==================== STATISTIQUES CAISSE ====================

    @GetMapping("/statistiques/aujourdhui")
    @PreAuthorize("hasAnyRole('ADMIN', 'VENDEUR')")
    @Operation(summary = "Obtenir les statistiques du jour")
    public ResponseEntity<Map<String, Object>> getStatistiquesDuJour() {
        Map<String, Object> statistiques = caisseService.getStatistiquesDuJour();
        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("date", LocalDate.now());
        response.put("statistiques", statistiques);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/statistiques/semaine")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Obtenir les statistiques de la semaine")
    public ResponseEntity<Map<String, Object>> getStatistiquesDeLaSemaine() {
        Map<String, Object> statistiques = caisseService.getStatistiquesDeLaSemaine();
        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("statistiques", statistiques);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/statistiques/mois")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Obtenir les statistiques du mois")
    public ResponseEntity<Map<String, Object>> getStatistiquesDuMois() {
        Map<String, Object> statistiques = caisseService.getStatistiquesDuMois();
        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("statistiques", statistiques);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/statistiques/annee")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Obtenir les statistiques de l'année")
    public ResponseEntity<Map<String, Object>> getStatistiquesDeLAnnee() {
        Map<String, Object> statistiques = caisseService.getStatistiquesDeLAnnee();
        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("statistiques", statistiques);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/statistiques/periode")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Obtenir les statistiques sur une période personnalisée")
    public ResponseEntity<Map<String, Object>> getStatistiquesParPeriode(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateDebut,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFin) {
        Map<String, Object> statistiques = caisseService.getStatistiquesParPeriode(dateDebut, dateFin);
        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("statistiques", statistiques);
        return ResponseEntity.ok(response);
    }

    // ==================== REVENUS ET PERTES ====================

    @GetMapping("/revenus-pertes")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Obtenir les revenus et pertes sur une période")
    public ResponseEntity<Map<String, Object>> getRevenusEtPertesParPeriode(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateDebut,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFin) {
        Map<String, Object> resultats = caisseService.getRevenusEtPertesParPeriode(dateDebut, dateFin);
        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("resultats", resultats);
        return ResponseEntity.ok(response);
    }

    // ==================== VENTES COMPTANT/CRÉDIT ====================

    @GetMapping("/ventes/comptant/aujourdhui")
    @PreAuthorize("hasAnyRole('ADMIN', 'VENDEUR')")
    @Operation(summary = "Obtenir les ventes comptant du jour")
    public ResponseEntity<Map<String, Object>> getVentesComptantDuJour() {
        Map<String, Object> ventes = caisseService.getVentesComptantDuJour();
        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("ventes", ventes);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/ventes/credit/aujourdhui")
    @PreAuthorize("hasAnyRole('ADMIN', 'VENDEUR')")
    @Operation(summary = "Obtenir les ventes à crédit du jour")
    public ResponseEntity<Map<String, Object>> getVentesCreditDuJour() {
        Map<String, Object> ventes = caisseService.getVentesCreditDuJour();
        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("ventes", ventes);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/ventes/comptant/periode")
    @PreAuthorize("hasAnyRole('ADMIN', 'VENDEUR')")
    @Operation(summary = "Obtenir les ventes comptant sur une période")
    public ResponseEntity<Map<String, Object>> getVentesComptantParPeriode(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateDebut,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFin) {
        Map<String, Object> ventes = caisseService.getVentesComptantParPeriode(dateDebut, dateFin);
        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("ventes", ventes);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/ventes/credit/periode")
    @PreAuthorize("hasAnyRole('ADMIN', 'VENDEUR')")
    @Operation(summary = "Obtenir les ventes à crédit sur une période")
    public ResponseEntity<Map<String, Object>> getVentesCreditParPeriode(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateDebut,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFin) {
        Map<String, Object> ventes = caisseService.getVentesCreditParPeriode(dateDebut, dateFin);
        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("ventes", ventes);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/ventes/statistiques")
    @PreAuthorize("hasAnyRole('ADMIN', 'VENDEUR')")
    @Operation(summary = "Obtenir les statistiques des ventes comptant/crédit")
    public ResponseEntity<Map<String, Object>> getStatistiquesVentesComptantCredit() {
        Map<String, Object> stats = caisseService.getStatistiquesVentesComptantCredit();
        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("statistiques", stats);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/paiements-groupes")
    @PreAuthorize("hasAnyRole('ADMIN', 'VENDEUR')")
    @Operation(summary = "Obtenir l'historique des paiements groupés")
    public ResponseEntity<List<Map<String, Object>>> getPaiementsGroupes() {
        return ResponseEntity.ok(caisseService.getPaiementsGroupes());
    }

    // ==================== PAGE PARAMÈTRES : RÉINITIALISATION / HISTORIQUE ====================

    @PostMapping("/reinitialiser")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Réinitialiser les statistiques de la caisse du jour")
    public ResponseEntity<Map<String, Object>> reinitialiserJour() {
        caisseService.reinitialiserJour();
        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("message", "Caisse du jour réinitialisée avec succès");
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/historique")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Supprimer tout l'historique des opérations de caisse")
    public ResponseEntity<Map<String, Object>> supprimerHistorique() {
        caisseService.supprimerHistorique();
        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("message", "Historique supprimé avec succès");
        return ResponseEntity.ok(response);
    }

    // ==================== TRANSFERT CAISSE → BANQUE ====================

    @PostMapping("/transferer-vers-banque")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Transférer de l'argent de la caisse vers un compte bancaire")
    public ResponseEntity<Map<String, Object>> transfererVersBanque(@RequestBody TransfertCaisseBanqueRequest request) {
        Map<String, Object> result = caisseService.transfererVersBanque(request);
        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("message", "Transfert caisse → banque effectué avec succès");
        response.put("resultat", result);
        return ResponseEntity.ok(response);
    }

    // ==================== RÉCONCILIATION CAISSE PAR VENDEUR ====================

    @GetMapping("/reconciliation-vendeurs")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Réconciliation caisse par vendeur pour une date donnée (défaut: aujourd'hui)")
    public ResponseEntity<Map<String, Object>> getReconciliationVendeurs(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        LocalDate dateCible = date != null ? date : LocalDate.now();
        List<ReconciliationVendeurDTO> reconciliation = caisseService.getReconciliationVendeurs(dateCible);
        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("date", dateCible);
        response.put("reconciliation", reconciliation);
        return ResponseEntity.ok(response);
    }

    // ==================== GESTION DES ERREURS ====================

    @ExceptionHandler(RessourceIntrouvableException.class)
    public ResponseEntity<Map<String, Object>> handleRessourceIntrouvable(RessourceIntrouvableException e) {
        Map<String, Object> response = new HashMap<>();
        response.put("success", false);
        response.put("error", e.getMessage());
        response.put("message", e.getMessage());
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
    }

    @ExceptionHandler(SoldeInsuffisantException.class)
    public ResponseEntity<Map<String, Object>> handleSoldeInsuffisant(SoldeInsuffisantException e) {
        Map<String, Object> response = new HashMap<>();
        response.put("success", false);
        response.put("error", e.getMessage());
        response.put("message", e.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<Map<String, Object>> handleIllegalState(IllegalStateException e) {
        Map<String, Object> response = new HashMap<>();
        response.put("success", false);
        response.put("error", e.getMessage());
        response.put("message", e.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> handleIllegalArgument(IllegalArgumentException e) {
        Map<String, Object> response = new HashMap<>();
        response.put("success", false);
        response.put("error", e.getMessage());
        response.put("message", e.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
    }

    // Sans ce handler dédié, l'AccessDeniedException levée par les @PreAuthorize
    // ci-dessus (ex: VENDEUR sur /ouvrir, /entree...) tombait dans le
    // handleGeneralException ci-dessous (un handler local prime toujours sur
    // celui, plus spécifique, de GlobalExceptionHandler) et renvoyait un 500
    // "erreur interne" trompeur au lieu d'un 403 clair.
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<Map<String, Object>> handleAccessDenied(AccessDeniedException e) {
        Map<String, Object> response = new HashMap<>();
        response.put("success", false);
        response.put("error", "Vous n'avez pas les permissions nécessaires pour effectuer cette action");
        response.put("message", "Vous n'avez pas les permissions nécessaires pour effectuer cette action");
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(response);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleGeneralException(Exception e) {
        Map<String, Object> response = new HashMap<>();
        response.put("success", false);
        String messageSur = MessageErreurUtil.messageClient(e);
        response.put("error", "Une erreur interne est survenue: " + messageSur);
        response.put("message", "Une erreur interne est survenue: " + messageSur);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
    }
}