package com.ges.boutique.securite;

import com.ges.boutique.email.PasswordResetService;
import com.ges.boutique.exception.MessageErreurUtil;
import com.ges.boutique.utilisateur.AuthRequest;
import com.ges.boutique.utilisateur.Utilisateur;
import com.ges.boutique.utilisateur.UtilisateurService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Tag(name = "Authentification", description = "API d'authentification")
public class AuthController {

    private final AuthenticationManager authenticationManager;
    private final JwtUtil jwtUtil;
    private final UtilisateurService utilisateurService;
    private final PasswordResetService passwordResetService;

    @PostMapping("/login")
    @Operation(summary = "Connexion utilisateur")
    public ResponseEntity<Map<String, Object>> login(@RequestBody AuthRequest authRequest) {
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(
                        authRequest.getUsername(),
                        authRequest.getPassword()
                )
        );

        UserDetails userDetails = (UserDetails) authentication.getPrincipal();

        // Récupérer l'utilisateur complet
        Utilisateur utilisateur = utilisateurService.obtenirUtilisateurParUsername(userDetails.getUsername());

        // Utiliser la nouvelle méthode avec l'ID dans le token
        String token = jwtUtil.generateTokenWithId(
                userDetails.getUsername(),
                userDetails.getAuthorities().iterator().next().getAuthority(),
                utilisateur.getId()
        );

        Map<String, Object> response = new HashMap<>();
        response.put("token", token);
        response.put("username", userDetails.getUsername());
        response.put("role", userDetails.getAuthorities().iterator().next().getAuthority());
        response.put("nomComplet", utilisateur.getNomComplet());
        response.put("email", utilisateur.getEmail());
        response.put("telephone", utilisateur.getTelephone());
        response.put("id", utilisateur.getId());
        response.put("photo", utilisateur.getPhoto() != null ? utilisateur.getPhoto() : "");
        response.put("superAdmin", utilisateur.isSuperAdmin());

        return ResponseEntity.ok(response);
    }

    @PostMapping("/logout")
    @Operation(summary = "Déconnexion")
    public ResponseEntity<Void> logout() {
        return ResponseEntity.ok().build();
    }

    @GetMapping("/me")
    @PreAuthorize("hasAnyRole('ADMIN', 'VENDEUR')")
    @Operation(summary = "Obtenir les informations de l'utilisateur connecté")
    public ResponseEntity<Map<String, Object>> getCurrentUser(Principal principal) {
        String username = principal.getName();
        Utilisateur utilisateur = utilisateurService.obtenirUtilisateurParUsername(username);

        Map<String, Object> response = new HashMap<>();
        response.put("id", utilisateur.getId());
        response.put("username", utilisateur.getUsername());
        response.put("nomComplet", utilisateur.getNomComplet());
        response.put("email", utilisateur.getEmail());
        response.put("telephone", utilisateur.getTelephone());
        response.put("role", utilisateur.getRole().name());
        response.put("actif", utilisateur.isActif());
        response.put("photo", utilisateur.getPhoto() != null ? utilisateur.getPhoto() : "");
        response.put("superAdmin", utilisateur.isSuperAdmin());

        return ResponseEntity.ok(response);
    }

    @GetMapping("/profil")
    @PreAuthorize("hasAnyRole('ADMIN', 'VENDEUR')")
    @Operation(summary = "Obtenir le profil de l'utilisateur connecté")
    public ResponseEntity<Utilisateur> obtenirMonProfil(Principal principal) {
        String username = principal.getName();
        Utilisateur utilisateur = utilisateurService.obtenirUtilisateurParUsername(username);
        return ResponseEntity.ok(utilisateur);
    }

    @PostMapping("/mot-de-passe-oublie")
    @Operation(summary = "Demander la réinitialisation du mot de passe")
    public ResponseEntity<Map<String, String>> motDePasseOublie(@RequestBody Map<String, String> body) {
        String email = body.get("email");
        if (email == null || email.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("message", "L'email est obligatoire"));
        }
        try {
            passwordResetService.demanderReset(email.trim());
            return ResponseEntity.ok(Map.of("message", "Un lien de réinitialisation a été envoyé à votre adresse email"));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("message", MessageErreurUtil.messageClient(e)));
        }
    }

    @GetMapping("/verifier-token-reset")
    @Operation(summary = "Vérifier si un token de reset est valide")
    public ResponseEntity<Map<String, Boolean>> verifierTokenReset(@RequestParam String token) {
        return ResponseEntity.ok(Map.of("valide", passwordResetService.validerToken(token)));
    }

    @PostMapping("/reinitialiser-password")
    @Operation(summary = "Réinitialiser le mot de passe avec un token")
    public ResponseEntity<Map<String, String>> reinitialiserPassword(@RequestBody Map<String, String> body) {
        String token = body.get("token");
        String password = body.containsKey("nouveauPassword") ? body.get("nouveauPassword") : body.get("password");
        try {
            passwordResetService.reinitialiserPassword(token, password);
            return ResponseEntity.ok(Map.of("message", "Mot de passe modifié avec succès"));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("message", MessageErreurUtil.messageClient(e)));
        }
    }

    @PutMapping("/profil")
    @PreAuthorize("hasAnyRole('ADMIN', 'VENDEUR')")
    @Operation(summary = "Modifier le profil de l'utilisateur connecté")
    public ResponseEntity<Utilisateur> modifierMonProfil(
            Principal principal,
            @RequestBody Utilisateur utilisateurDetails) {
        String username = principal.getName();
        Utilisateur utilisateur = utilisateurService.obtenirUtilisateurParUsername(username);

        // Empêcher la modification du rôle via le profil (le rôle vient de l'entité existante)
        utilisateurDetails.setRole(null);

        // Empêcher l'enregistrement d'un mot de passe vide
        if (utilisateurDetails.getPassword() != null && utilisateurDetails.getPassword().trim().isEmpty()) {
            utilisateurDetails.setPassword(null);
        }

        Utilisateur updated = utilisateurService.modifierUtilisateur(utilisateur.getId(), utilisateurDetails);
        return ResponseEntity.ok(updated);
    }
}