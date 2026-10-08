package com.ges.boutique.dette;

import com.ges.boutique.exception.FonctionnaliteDesactiveeException;
import com.ges.boutique.exception.MessageErreurUtil;
import com.ges.boutique.exception.RessourceIntrouvableException;
import com.ges.boutique.feature.CleFonctionnalite;
import com.ges.boutique.feature.RequireFeature;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/dettes-anciennes")
@RequiredArgsConstructor
@CrossOrigin("*")
@Tag(name = "Dettes Anciennes", description = "Gestion des dettes antérieures des clients")
public class DetteAncienneController {

    private final DetteAncienneService detteService;

    // Seules la création/modification sont bloquées si le super admin désactive cette
    // fonctionnalité — règlement (paiement) et lecture restent ouverts pour ne jamais
    // empêcher un client de la boutique de rembourser ou consulter ses dettes réelles.
    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'VENDEUR')")
    @RequireFeature(CleFonctionnalite.DETTES_ANCIENNES)
    @Operation(summary = "Créer une nouvelle dette ancienne")
    public ResponseEntity<Map<String, Object>> creerDette(@RequestBody DetteAncienneRequest request) {
        DetteAncienneDto dette = detteService.creerDette(request);
        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("message", "Dette ancienne créée avec succès");
        response.put("dette", dette);
        return ResponseEntity.ok(response);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'VENDEUR')")
    @RequireFeature(CleFonctionnalite.DETTES_ANCIENNES)
    @Operation(summary = "Modifier une dette ancienne")
    public ResponseEntity<Map<String, Object>> modifierDette(@PathVariable Long id, @RequestBody DetteAncienneRequest request) {
        DetteAncienneDto dette = detteService.modifierDette(id, request);
        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("message", "Dette modifiée avec succès");
        response.put("dette", dette);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'VENDEUR')")
    @Operation(summary = "Obtenir une dette par son ID")
    public ResponseEntity<Map<String, Object>> getDetteById(@PathVariable Long id) {
        DetteAncienneDto dette = detteService.obtenirDetteParId(id);
        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("dette", dette);
        return ResponseEntity.ok(response);
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'VENDEUR')")
    @Operation(summary = "Obtenir toutes les dettes")
    public ResponseEntity<Map<String, Object>> getAllDettes() {
        List<DetteAncienneDto> dettes = detteService.obtenirToutesDettes();
        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("dettes", dettes);
        response.put("nombreDettes", dettes.size());
        return ResponseEntity.ok(response);
    }

    @GetMapping("/client/{clientId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'VENDEUR')")
    @Operation(summary = "Obtenir toutes les dettes d'un client")
    public ResponseEntity<Map<String, Object>> getDettesParClient(@PathVariable Long clientId) {
        List<DetteAncienneDto> dettes = detteService.obtenirDettesParClient(clientId);
        Double totalDettes = dettes.stream()
                .filter(d -> !d.getEstReglee())
                .mapToDouble(DetteAncienneDto::getMontantRestant)
                .sum();

        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("dettes", dettes);
        response.put("nombreDettes", dettes.size());
        response.put("totalRestant", totalDettes);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/non-reglees")
    @PreAuthorize("hasAnyRole('ADMIN', 'VENDEUR')")
    @Operation(summary = "Obtenir toutes les dettes non réglées")
    public ResponseEntity<Map<String, Object>> getDettesNonReglees() {
        List<DetteAncienneDto> dettes = detteService.obtenirToutesDettesNonReglees();
        Double totalRestant = dettes.stream()
                .mapToDouble(DetteAncienneDto::getMontantRestant)
                .sum();

        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("dettes", dettes);
        response.put("nombreDettes", dettes.size());
        response.put("totalRestant", totalRestant);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/reglees")
    @PreAuthorize("hasAnyRole('ADMIN', 'VENDEUR')")
    @Operation(summary = "Obtenir toutes les dettes réglées")
    public ResponseEntity<Map<String, Object>> getDettesReglees() {
        List<DetteAncienneDto> dettes = detteService.obtenirToutesDettesReglees();
        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("dettes", dettes);
        response.put("nombreDettes", dettes.size());
        return ResponseEntity.ok(response);
    }

    @GetMapping("/recherche")
    @PreAuthorize("hasAnyRole('ADMIN', 'VENDEUR')")
    @Operation(summary = "Rechercher des dettes par client")
    public ResponseEntity<Map<String, Object>> rechercherDettes(@RequestParam String query) {
        List<DetteAncienneDto> dettes = detteService.rechercherDettes(query);
        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("dettes", dettes);
        response.put("nombreResultats", dettes.size());
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Supprimer une dette (seulement si aucun règlement)")
    public ResponseEntity<Map<String, Object>> supprimerDette(@PathVariable Long id) {
        detteService.supprimerDette(id);
        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("message", "Dette supprimée avec succès");
        return ResponseEntity.ok(response);
    }

    @PostMapping("/reglement")
    @PreAuthorize("hasAnyRole('ADMIN', 'VENDEUR')")
    @Operation(summary = "Enregistrer un règlement de dette")
    public ResponseEntity<Map<String, Object>> enregistrerReglement(@RequestBody ReglementDetteRequest request) {
        ReglementDetteAncienne reglement = detteService.enregistrerReglement(request);
        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("message", "Règlement enregistré avec succès");
        response.put("reglement", reglement);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{detteId}/reglements")
    @PreAuthorize("hasAnyRole('ADMIN', 'VENDEUR')")
    @Operation(summary = "Obtenir l'historique des règlements d'une dette")
    public ResponseEntity<Map<String, Object>> getHistoriqueReglements(@PathVariable Long detteId) {
        List<ReglementDetteAncienne> reglements = detteService.getHistoriqueReglements(detteId);
        Double totalPaye = reglements.stream()
                .mapToDouble(ReglementDetteAncienne::getMontantPaye)
                .sum();

        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("reglements", reglements);
        response.put("nombreReglements", reglements.size());
        response.put("totalPaye", totalPaye);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/reglements/periode")
    @PreAuthorize("hasAnyRole('ADMIN', 'VENDEUR')")
    @Operation(summary = "Obtenir les règlements sur une période")
    public ResponseEntity<Map<String, Object>> getReglementsParPeriode(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateDebut,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFin) {
        List<ReglementDetteAncienne> reglements = detteService.getReglementsParPeriode(dateDebut, dateFin);
        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("reglements", reglements);
        response.put("nombreReglements", reglements.size());
        response.put("dateDebut", dateDebut);
        response.put("dateFin", dateFin);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/statistiques/globales")
    @PreAuthorize("hasAnyRole('ADMIN', 'VENDEUR')")
    @Operation(summary = "Obtenir les statistiques globales des dettes")
    public ResponseEntity<Map<String, Object>> getStatistiquesGlobales() {
        Map<String, Object> statistiques = detteService.getStatistiquesGlobales();
        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("statistiques", statistiques);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/statistiques/client/{clientId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'VENDEUR')")
    @Operation(summary = "Obtenir les statistiques des dettes d'un client")
    public ResponseEntity<Map<String, Object>> getStatistiquesParClient(@PathVariable Long clientId) {
        Map<String, Object> statistiques = detteService.getStatistiquesParClient(clientId);
        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("statistiques", statistiques);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/statistiques/reglements")
    @PreAuthorize("hasAnyRole('ADMIN', 'VENDEUR')")
    @Operation(summary = "Obtenir les statistiques des règlements sur une période")
    public ResponseEntity<Map<String, Object>> getStatistiquesReglementsParPeriode(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateDebut,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFin) {
        Map<String, Object> statistiques = detteService.getStatistiquesReglementsParPeriode(dateDebut, dateFin);
        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("statistiques", statistiques);
        return ResponseEntity.ok(response);
    }

    @ExceptionHandler(RessourceIntrouvableException.class)
    public ResponseEntity<Map<String, Object>> handleRessourceIntrouvable(RessourceIntrouvableException e) {
        Map<String, Object> response = new HashMap<>();
        response.put("success", false);
        response.put("error", e.getMessage());
        response.put("message", e.getMessage());
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> handleIllegalArgument(IllegalArgumentException e) {
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

    // Sans ce handler dédié, l'AccessDeniedException levée par le @PreAuthorize
    // hasRole('ADMIN') sur la suppression (un handler local prime toujours sur
    // celui, plus spécifique, de GlobalExceptionHandler) tombait dans le
    // handleGeneralException ci-dessous et renvoyait un 500 "erreur interne"
    // trompeur au lieu d'un 403 clair.
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<Map<String, Object>> handleAccessDenied(AccessDeniedException e) {
        Map<String, Object> response = new HashMap<>();
        response.put("success", false);
        response.put("error", "Vous n'avez pas les permissions nécessaires pour effectuer cette action");
        response.put("message", "Vous n'avez pas les permissions nécessaires pour effectuer cette action");
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(response);
    }

    // Même piège que AccessDeniedException ci-dessus : sans handler dédié, l'exception
    // levée par @RequireFeature tombait dans handleGeneralException (500 trompeur au
    // lieu d'un 403 clair). errorCode ajouté : Angular/Ionic/RN vérifient tous
    // errorCode === 'FEATURE_DISABLED' pour afficher le popup dédié — sans lui, ce
    // handler local répondait bien 403 mais le popup explicatif ne s'affichait jamais.
    @ExceptionHandler(FonctionnaliteDesactiveeException.class)
    public ResponseEntity<Map<String, Object>> handleFonctionnaliteDesactivee(FonctionnaliteDesactiveeException e) {
        Map<String, Object> response = new HashMap<>();
        response.put("success", false);
        response.put("error", e.getMessage());
        response.put("message", e.getMessage());
        response.put("errorCode", "FEATURE_DISABLED");
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(response);
    }

    // Refus d'un rôle personnalisé (PermissionInterceptor) : sans ce handler, le
    // handler générique ci-dessous (local, donc prioritaire) le changeait en erreur 500.
    @ExceptionHandler(com.ges.boutique.exception.PermissionRefuseeException.class)
    public ResponseEntity<Map<String, Object>> handlePermissionRefusee(com.ges.boutique.exception.PermissionRefuseeException e) {
        Map<String, Object> response = new HashMap<>();
        response.put("success", false);
        response.put("error", e.getMessage());
        response.put("message", e.getMessage());
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