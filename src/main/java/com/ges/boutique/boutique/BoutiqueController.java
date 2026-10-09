package com.ges.boutique.boutique;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ges.boutique.exception.MessageErreurUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/boutique")
@RequiredArgsConstructor
@Tag(name = "Boutique", description = "Gestion des informations de la boutique")
public class BoutiqueController {

    private final BoutiqueService boutiqueService;
    private final LogoAllege logoAllege;
    private final ObjectMapper objectMapper;

    // ==================== Endpoints sans ID (existants) ====================

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'VENDEUR')")
    @Operation(summary = "Obtenir les informations de la boutique")
    public ResponseEntity<Map<String, Object>> obtenirBoutique() {
        Boutique boutique = boutiqueService.obtenirBoutique();
        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("boutique", avecLogoAllege(boutique));
        return ResponseEntity.ok(response);
    }

    @PutMapping
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Modifier les informations de la boutique")
    public ResponseEntity<Map<String, Object>> modifierBoutique(@RequestBody Boutique boutique) {
        Boutique boutiqueModifiee = boutiqueService.modifierBoutique(boutique);
        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("message", "Informations de la boutique modifiées avec succès");
        response.put("boutique", boutiqueModifiee);
        return ResponseEntity.ok(response);
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Créer une nouvelle boutique")
    public ResponseEntity<Map<String, Object>> creerBoutique(@RequestBody Boutique boutique) {
        Boutique nouvelleBoutique = boutiqueService.creerBoutique(boutique);
        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("message", "Boutique créée avec succès");
        response.put("boutique", nouvelleBoutique);
        return ResponseEntity.ok(response);
    }

    // ==================== Nouveaux endpoints avec ID (pour compatibilité frontend) ====================

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'VENDEUR')")
    @Operation(summary = "Obtenir les informations de la boutique par ID (ignoré car une seule boutique)")
    public ResponseEntity<Map<String, Object>> obtenirBoutiqueParId(@PathVariable Long id) {
        // L'ID est ignoré, on retourne toujours la boutique unique
        return obtenirBoutique();
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Modifier la boutique par ID (ignoré)")
    public ResponseEntity<Map<String, Object>> modifierBoutiqueParId(@PathVariable Long id,
                                                                     @RequestBody Boutique boutique) {
        return modifierBoutique(boutique);
    }

    // ==================== Fonctionnalités (réservé super admin) ====================

    // Pas un rôle séparé : n'importe quel compte ADMIN existant peut être promu
    // "super admin" via le flag super_admin en base (voir Utilisateur.superAdmin).
    // Un ADMIN classique (flag à false) reçoit un 403 ici comme un VENDEUR.
    @PutMapping("/fonctionnalites")
    @PreAuthorize("hasRole('ADMIN') and authentication.principal.superAdmin")
    @Operation(summary = "Activer/désactiver les fonctionnalités de la boutique (Transferts, Vitrine...) — réservé au super admin")
    public ResponseEntity<Map<String, Object>> modifierFonctionnalites(@RequestBody FonctionnalitesRequest request) {
        Boutique boutique = boutiqueService.modifierFonctionnalites(
                request.getFeatureTransfertsActif(),
                request.getFeatureVitrineActif()
        );
        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("message", "Fonctionnalités mises à jour avec succès");
        response.put("boutique", boutique);
        return ResponseEntity.ok(response);
    }

    // L'admin classique de la boutique peut recevoir les sauvegardes par email si le
    // super admin active feature.ENVOI_SAUVEGARDES_EMAIL pour sa boutique ET renseigne
    // ici l'adresse qui les reçoit — jamais choisie par l'admin de boutique lui-même.
    @PutMapping("/email-sauvegarde")
    @PreAuthorize("hasRole('ADMIN') and authentication.principal.superAdmin")
    @Operation(summary = "Définir l'adresse email qui reçoit les sauvegardes — réservé au super admin")
    public ResponseEntity<Map<String, Object>> definirEmailSauvegarde(@RequestBody Map<String, String> body) {
        Boutique boutique = boutiqueService.definirEmailSauvegarde(body.get("emailSauvegarde"));
        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("message", "Adresse email de sauvegarde mise à jour");
        response.put("boutique", boutique);
        return ResponseEntity.ok(response);
    }

    @PostMapping(value = "/upload-logo", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Uploader le logo de la boutique (PNG, JPG, SVG — max 2 Mo)")
    public ResponseEntity<Map<String, Object>> uploadLogo(@RequestParam("logo") MultipartFile file) {
        if (file.isEmpty()) {
            Map<String, Object> err = new HashMap<>();
            err.put("success", false);
            err.put("message", "Fichier vide");
            return ResponseEntity.badRequest().body(err);
        }
        String contentType = file.getContentType();
        if (contentType == null || (!contentType.startsWith("image/"))) {
            Map<String, Object> err = new HashMap<>();
            err.put("success", false);
            err.put("message", "Le fichier doit être une image (PNG, JPG, SVG...)");
            return ResponseEntity.badRequest().body(err);
        }
        if (file.getSize() > 2 * 1024 * 1024) {
            Map<String, Object> err = new HashMap<>();
            err.put("success", false);
            err.put("message", "Le fichier ne doit pas dépasser 2 Mo");
            return ResponseEntity.badRequest().body(err);
        }
        try {
            byte[] bytes = file.getBytes();
            String base64 = "data:" + contentType + ";base64," + Base64.getEncoder().encodeToString(bytes);
            Boutique boutique = boutiqueService.saveLogo(base64);
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "Logo mis à jour avec succès");
            response.put("logo", boutique.getLogo());
            return ResponseEntity.ok(response);
        } catch (IOException e) {
            Map<String, Object> err = new HashMap<>();
            err.put("success", false);
            err.put("message", "Erreur lors de la lecture du fichier: " + MessageErreurUtil.messageClient(e));
            return ResponseEntity.internalServerError().body(err);
        }
    }

    /** Mêmes champs que l'entité, avec le logo allégé (logo et logoPath, lus selon les applis). */
    @SuppressWarnings("unchecked")
    private Object avecLogoAllege(Boutique boutique) {
        if (boutique == null || boutique.getLogo() == null) return boutique;
        Map<String, Object> champs = objectMapper.convertValue(boutique, Map.class);
        String logo = logoAllege.alleger(boutique.getLogo());
        if (champs.containsKey("logo")) champs.put("logo", logo);
        if (champs.containsKey("logoPath")) champs.put("logoPath", logo);
        return champs;
    }
}
