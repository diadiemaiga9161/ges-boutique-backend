package com.ges.boutique.securite;

import com.ges.boutique.feature.CleFonctionnalite;
import com.ges.boutique.feature.RequireFeature;
import com.ges.boutique.utilisateur.Utilisateur;
import com.ges.boutique.utilisateur.UtilisateurRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/presence")
@RequiredArgsConstructor
@Tag(name = "Présence", description = "Qui est actuellement en ligne — fonctionnalité activable par le super admin")
public class PresenceController {

    private final PresenceService presenceService;
    private final UtilisateurRepository utilisateurRepository;

    @GetMapping("/en-ligne")
    @PreAuthorize("hasRole('ADMIN')")
    @RequireFeature(CleFonctionnalite.VOIR_PERSONNES_EN_LIGNE)
    @Operation(summary = "Lister les utilisateurs actuellement en ligne (actifs dans les 3 dernières minutes)")
    public ResponseEntity<Map<String, Object>> obtenirEnLigne() {
        List<Map<String, Object>> utilisateurs = presenceService.obtenirUtilisateursEnLigne().stream()
                .map(utilisateurRepository::findByUsername)
                .filter(java.util.Optional::isPresent)
                .map(java.util.Optional::get)
                .map(this::versDto)
                .collect(Collectors.toList());

        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("enLigne", utilisateurs);
        response.put("nombre", utilisateurs.size());
        return ResponseEntity.ok(response);
    }

    private Map<String, Object> versDto(Utilisateur u) {
        Map<String, Object> dto = new HashMap<>();
        dto.put("id", u.getId());
        dto.put("nomComplet", u.getNomComplet());
        dto.put("username", u.getUsername());
        dto.put("role", u.getRole());
        return dto;
    }
}
