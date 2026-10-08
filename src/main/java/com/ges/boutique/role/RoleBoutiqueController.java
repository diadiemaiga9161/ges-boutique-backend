package com.ges.boutique.role;

import com.ges.boutique.feature.CleFonctionnalite;
import com.ges.boutique.feature.RequireFeature;
import com.ges.boutique.utilisateur.Utilisateur;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Rôles et permissions personnalisés — activés par le super admin
 * (CleFonctionnalite.ROLES_PERSONNALISES), puis gérés par le gérant de la boutique.
 */
@RestController
@RequestMapping("/api/roles")
@RequiredArgsConstructor
@Tag(name = "Rôles", description = "Rôles et permissions de la boutique")
public class RoleBoutiqueController {

    private final RoleBoutiqueService service;

    /**
     * Ce que la personne connectée a le droit de faire — lu par les apps pour masquer menus
     * et boutons. Toujours accessible (même fonctionnalité désactivée : actif=false).
     */
    @GetMapping("/mes-permissions")
    @PreAuthorize("hasAnyRole('ADMIN', 'VENDEUR')")
    @Operation(summary = "Permissions de l'utilisateur connecté")
    public ResponseEntity<Map<String, Object>> mesPermissions(@AuthenticationPrincipal Utilisateur moi) {
        Map<String, Object> body = new LinkedHashMap<>();
        boolean actif = service.fonctionnaliteActive();
        Set<String> permissions = service.permissionsEffectives(moi);
        body.put("actif", actif);
        body.put("toutAutorise", permissions == null);
        body.put("permissions", permissions == null ? List.of() : permissions);
        body.put("roleNom", service.nomRoleEffectif(moi));
        return ResponseEntity.ok(body);
    }

    @GetMapping("/catalogue")
    @PreAuthorize("hasRole('ADMIN')")
    @RequireFeature(CleFonctionnalite.ROLES_PERSONNALISES)
    @Operation(summary = "Liste des parties de l'application et des actions possibles")
    public ResponseEntity<List<Map<String, Object>>> catalogue() {
        return ResponseEntity.ok(service.catalogue());
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    @RequireFeature(CleFonctionnalite.ROLES_PERSONNALISES)
    @Operation(summary = "Lister les rôles")
    public ResponseEntity<List<Map<String, Object>>> lister() {
        return ResponseEntity.ok(service.lister());
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    @RequireFeature(CleFonctionnalite.ROLES_PERSONNALISES)
    @Operation(summary = "Créer un rôle (ex: Caissier)")
    public ResponseEntity<Map<String, Object>> creer(@RequestBody RoleBoutiqueRequest request) {
        return ResponseEntity.ok(service.creer(request));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @RequireFeature(CleFonctionnalite.ROLES_PERSONNALISES)
    @Operation(summary = "Modifier un rôle et ses permissions")
    public ResponseEntity<Map<String, Object>> modifier(@PathVariable Long id,
                                                        @RequestBody RoleBoutiqueRequest request,
                                                        @AuthenticationPrincipal Utilisateur moi) {
        return ResponseEntity.ok(service.modifier(id, request, moi));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @RequireFeature(CleFonctionnalite.ROLES_PERSONNALISES)
    @Operation(summary = "Supprimer un rôle non attribué")
    public ResponseEntity<Void> supprimer(@PathVariable Long id) {
        service.supprimer(id);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/utilisateurs/{utilisateurId}")
    @PreAuthorize("hasRole('ADMIN')")
    @RequireFeature(CleFonctionnalite.ROLES_PERSONNALISES)
    @Operation(summary = "Attribuer un rôle à une personne")
    public ResponseEntity<Map<String, Object>> attribuer(@PathVariable Long utilisateurId,
                                                         @RequestBody Map<String, Long> body,
                                                         @AuthenticationPrincipal Utilisateur moi) {
        Utilisateur u = service.attribuer(utilisateurId, body.get("roleId"), moi);
        Map<String, Object> res = new LinkedHashMap<>();
        res.put("id", u.getId());
        res.put("role", u.getRole().name());
        res.put("roleBoutiqueId", u.getRoleBoutiqueId());
        res.put("roleNom", service.nomRoleEffectif(u));
        return ResponseEntity.ok(res);
    }
}
