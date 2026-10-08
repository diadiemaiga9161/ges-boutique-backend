package com.ges.boutique.role;

import com.ges.boutique.exception.PermissionRefuseeException;
import com.ges.boutique.utilisateur.RoleUtilisateur;
import com.ges.boutique.utilisateur.Utilisateur;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.lang.NonNull;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.util.UrlPathHelper;

import java.util.List;
import java.util.Set;

/**
 * Applique les rôles personnalisés à toutes les routes /api/** d'un seul endroit, en plus
 * (jamais à la place) des @PreAuthorize existants. Ne fait rien tant que le super admin n'a
 * pas activé ROLES_PERSONNALISES, ni pour le super admin, ni pour le Gérant système.
 */
@Component
@RequiredArgsConstructor
public class PermissionInterceptor implements HandlerInterceptor {

    private final RoleBoutiqueService roleService;

    /** Lectures toujours ouvertes : nécessaires au fonctionnement de base de toutes les pages. */
    private static final List<String> LECTURES_LIBRES = List.of(
            "/api/roles/mes-permissions",
            "/api/commissions/mes-primes",
            "/api/utilisateurs",
            "/api/boutique",
            "/api/parametres"
    );

    @Override
    public boolean preHandle(@NonNull HttpServletRequest request,
                             @NonNull HttpServletResponse response,
                             @NonNull Object handler) {
        String methode = request.getMethod();
        if ("OPTIONS".equalsIgnoreCase(methode)) return true;

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof Utilisateur utilisateur)) return true;

        // Chemin décodé et nettoyé (%xx, ";param"), le même que celui utilisé par Spring MVC
        // pour choisir le contrôleur : sinon "/api/%63ommandes" atteindrait /api/commandes
        // sans être reconnu ici.
        String chemin = UrlPathHelper.defaultInstance.getPathWithinApplication(request);
        // Photo de profil de la personne connectée : toujours permise.
        if (chemin.equals("/api/utilisateurs/me") || chemin.equals("/api/utilisateurs/me/photo")) return true;

        ModulePermission module = trouverModule(chemin);
        if (module == null) return true;
        if (utilisateur.getRole() == RoleUtilisateur.VENDEUR && !module.isAccessibleVendeur()) {
            // Parties réservées aux rôles basés sur Gérant : plusieurs contrôleurs (employés,
            // paie, dépenses, caisse, achats fournisseurs, objectifs vendeur) acceptaient aussi
            // VENDEUR dans leurs @PreAuthorize → un vendeur lisait salaires et dépenses par l'API.
            // Refusé ici, que les rôles personnalisés soient activés ou non. Un vendeur qui doit
            // gérer la caisse/les dépenses reçoit un rôle basé sur Gérant, limité à ces parties.
            if (module.isReserveeGerant()) {
                if (("GET".equalsIgnoreCase(methode) || "HEAD".equalsIgnoreCase(methode))
                        && commencePar(chemin, LECTURES_LIBRES)) return true;
                throw new PermissionRefuseeException("La partie « " + module.getLibelle()
                        + " » est réservée au gérant. Demandez-lui un rôle qui la permet.");
            }
            // Autres parties absentes du tableau d'un rôle Vendeur (ex: prévisions IA de
            // l'accueil mobile) : comportement d'avant, décidé par les @PreAuthorize.
            return true;
        }

        Set<String> permissions = roleService.permissionsEffectives(utilisateur);
        if (permissions == null) return true;

        boolean lecture = "GET".equalsIgnoreCase(methode) || "HEAD".equalsIgnoreCase(methode);
        ActionPermission action;
        if (lecture || module.isToutEnLecture()) {
            if (lecture && commencePar(chemin, LECTURES_LIBRES)) return true;
            if (lecture && !module.isLectureProtegee()) return true;
            action = ActionPermission.VOIR;
        } else if ("DELETE".equalsIgnoreCase(methode) || chemin.contains("/annuler")) {
            action = ActionPermission.SUPPRIMER;
        } else {
            action = ActionPermission.GERER;
        }

        if (!permissions.contains(module.code(action))) {
            throw new PermissionRefuseeException("Votre rôle ne permet pas de « " + action.getLibelle().toLowerCase()
                    + " » dans la partie « " + module.getLibelle() + " ». Demandez au gérant.");
        }
        return true;
    }

    /** Partie de l'application concernée : préfixe le plus long qui correspond. */
    static ModulePermission trouverModule(String chemin) {
        ModulePermission trouve = null;
        int longueur = -1;
        for (ModulePermission m : ModulePermission.values()) {
            for (String p : m.getPrefixes()) {
                if (correspond(chemin, p) && p.length() > longueur) {
                    trouve = m;
                    longueur = p.length();
                }
            }
        }
        return trouve;
    }

    private static boolean commencePar(String chemin, List<String> prefixes) {
        return prefixes.stream().anyMatch(p -> correspond(chemin, p));
    }

    private static boolean correspond(String chemin, String prefixe) {
        return chemin.equals(prefixe) || chemin.startsWith(prefixe + "/");
    }
}
