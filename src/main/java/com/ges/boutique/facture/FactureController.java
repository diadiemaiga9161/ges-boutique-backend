package com.ges.boutique.facture;

import com.ges.boutique.email.PdfFactureService;
import com.ges.boutique.email.QrCodeService;
import com.ges.boutique.exception.MessageErreurUtil;
import com.ges.boutique.exception.RessourceIntrouvableException;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/caisse/factures")
@RequiredArgsConstructor
public class FactureController {

    private final FactureService factureService;
    private final PdfFactureService pdfFactureService;
    private final QrCodeService qrCodeService;

    @Value("${app.base-url:}")
    private String appBaseUrl;

    // ==================== CRÉATION ====================
    @PostMapping
    public ResponseEntity<Map<String, Object>> creerFacture(@RequestBody FactureRequest request) {
        return ResponseEntity.ok(factureService.creerFacture(request));
    }

    @PostMapping("/depuis-vente/{venteId}")
    public ResponseEntity<Map<String, Object>> creerFactureDepuisVente(
            @PathVariable Long venteId,
            @RequestParam Long utilisateurId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime dateFacture) {
        return ResponseEntity.ok(factureService.creerFactureDepuisVente(venteId, dateFacture, utilisateurId));
    }

    // ==================== CONSULTATION ====================
    @GetMapping
    public ResponseEntity<Map<String, Object>> obtenirToutesFactures() {
        List<Map<String, Object>> factures = factureService.obtenirToutesFactures();
        Map<String, Object> response = new HashMap<>();
        response.put("factures", factures);
        response.put("nombreFactures", factures.size());
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{id}")
    public ResponseEntity<Map<String, Object>> obtenirFacture(@PathVariable Long id) {
        return ResponseEntity.ok(factureService.obtenirFacture(id));
    }

    @GetMapping("/statut/{statut}")
    public ResponseEntity<Map<String, Object>> obtenirFacturesParStatut(@PathVariable String statut) {
        List<Map<String, Object>> factures = factureService.obtenirFacturesParStatut(statut);
        Map<String, Object> response = new HashMap<>();
        response.put("factures", factures);
        response.put("nombreFactures", factures.size());
        return ResponseEntity.ok(response);
    }

    @GetMapping("/client")
    public ResponseEntity<Map<String, Object>> obtenirFacturesParClient(@RequestParam String clientNom) {
        List<Map<String, Object>> factures = factureService.obtenirFacturesParClientNom(clientNom);
        Map<String, Object> response = new HashMap<>();
        response.put("factures", factures);
        response.put("nombreFactures", factures.size());
        return ResponseEntity.ok(response);
    }

    @GetMapping("/periode")
    public ResponseEntity<Map<String, Object>> obtenirFacturesParPeriode(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) String dateDebut,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) String dateFin) {
        LocalDateTime debut = LocalDateTime.parse(dateDebut + "T00:00:00");
        LocalDateTime fin = LocalDateTime.parse(dateFin + "T23:59:59");
        List<Map<String, Object>> factures = factureService.obtenirFacturesParPeriodeMap(debut, fin);
        Map<String, Object> response = new HashMap<>();
        response.put("factures", factures);
        response.put("nombreFactures", factures.size());
        return ResponseEntity.ok(response);
    }

    /**
     * ENDPOINT CORRIGÉ : retourne une Map<String, Object> contenant la liste
     * des factures associées à une vente (sous la clé "factures") et le nombre.
     */
    @GetMapping("/vente/{venteId}")
    public ResponseEntity<Map<String, Object>> obtenirFacturesParVente(@PathVariable Long venteId) {
        List<Map<String, Object>> factures = factureService.obtenirFacturesParVenteMap(venteId);
        Map<String, Object> response = new HashMap<>();
        response.put("factures", factures);
        response.put("nombreFactures", factures.size());
        return ResponseEntity.ok(response);
    }

    @GetMapping("/statistiques")
    public ResponseEntity<Map<String, Object>> getStatistiques() {
        Map<String, Object> stats = factureService.getStatistiques();
        Map<String, Object> response = new HashMap<>();
        response.put("statistiques", stats);
        return ResponseEntity.ok(response);
    }

    // ==================== MODIFICATION ====================
    @PutMapping("/{id}")
    public ResponseEntity<Map<String, Object>> modifierFacture(@PathVariable Long id, @RequestBody FactureRequest request) {
        return ResponseEntity.ok(factureService.modifierFacture(id, request));
    }

    @PutMapping("/{id}/statut")
    public ResponseEntity<Map<String, Object>> modifierStatutFacture(@PathVariable Long id, @RequestParam String statut) {
        return ResponseEntity.ok(factureService.modifierStatutFacture(id, statut));
    }

    @PutMapping("/{id}/valider")
    public ResponseEntity<Map<String, Object>> validerFacture(@PathVariable Long id) {
        return ResponseEntity.ok(factureService.modifierStatutFacture(id, "VALIDE"));
    }

    @PutMapping("/{id}/annuler")
    public ResponseEntity<Map<String, Object>> annulerFacture(@PathVariable Long id) {
        return ResponseEntity.ok(factureService.modifierStatutFacture(id, "ANNULEE"));
    }

    @PutMapping("/{factureId}/lignes/{ligneId}/prix")
    public ResponseEntity<Map<String, Object>> modifierPrixLigne(
            @PathVariable Long factureId,
            @PathVariable Long ligneId,
            @RequestParam Double nouveauPrix) {
        return ResponseEntity.ok(factureService.modifierPrixLigne(factureId, ligneId, nouveauPrix));
    }

    // ==================== PDF ====================

    private String construireBaseUrl(HttpServletRequest request) {
        if (appBaseUrl != null && !appBaseUrl.isEmpty()) {
            return appBaseUrl;
        }
        String scheme = request.getScheme();
        String host = request.getServerName();
        int port = request.getServerPort();
        if ((scheme.equals("https") && port == 443) || (scheme.equals("http") && port == 80)) {
            return scheme + "://" + host;
        }
        return scheme + "://" + host + ":" + port;
    }

    @GetMapping("/{id}/pdf")
    public ResponseEntity<byte[]> telechargerPdf(@PathVariable Long id, HttpServletRequest request) {
        Facture facture = factureService.obtenirFactureEntite(id);
        byte[] pdf = pdfFactureService.genererPdfFacture(facture, construireBaseUrl(request));
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_PDF);
        headers.set(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"facture-" + facture.getNumeroFacture() + ".pdf\"");
        return new ResponseEntity<>(pdf, headers, HttpStatus.OK);
    }

    @GetMapping("/{id}/pdf/view")
    public ResponseEntity<byte[]> voirPdf(@PathVariable Long id, HttpServletRequest request) {
        Facture facture = factureService.obtenirFactureEntite(id);
        byte[] pdf = pdfFactureService.genererPdfFacture(facture, construireBaseUrl(request));
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_PDF);
        headers.set(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"facture-" + facture.getNumeroFacture() + ".pdf\"");
        return new ResponseEntity<>(pdf, headers, HttpStatus.OK);
    }

    @GetMapping("/{id}/qrcode")
    public ResponseEntity<byte[]> obtenirQrCode(@PathVariable Long id, HttpServletRequest request) {
        String url = construireBaseUrl(request) + "/api/caisse/factures/" + id + "/pdf";
        byte[] qr = qrCodeService.genererQrCode(url, 250, 250);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.IMAGE_PNG);
        return new ResponseEntity<>(qr, headers, HttpStatus.OK);
    }

    // ==================== SUPPRESSION ====================
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> supprimerFacture(@PathVariable Long id) {
        factureService.supprimerFacture(id);
        return ResponseEntity.noContent().build();
    }

    // ==================== GESTION DES ERREURS ====================
    @ExceptionHandler(RessourceIntrouvableException.class)
    public ResponseEntity<Map<String, Object>> handleNotFound(RessourceIntrouvableException e) {
        Map<String, Object> error = new HashMap<>();
        error.put("success", false);
        error.put("error", e.getMessage());
        error.put("message", e.getMessage());
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(error);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> handleBadRequest(IllegalArgumentException e) {
        Map<String, Object> error = new HashMap<>();
        error.put("success", false);
        error.put("error", e.getMessage());
        error.put("message", e.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
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
    public ResponseEntity<Map<String, Object>> handleGeneral(Exception e) {
        log.error("Erreur interne", e);
        Map<String, Object> error = new HashMap<>();
        String messageSur = MessageErreurUtil.messageClient(e);
        error.put("success", false);
        error.put("error", "Erreur interne : " + messageSur);
        error.put("message", "Erreur interne : " + messageSur);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
    }
}