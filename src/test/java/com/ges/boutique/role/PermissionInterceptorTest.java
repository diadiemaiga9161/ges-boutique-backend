package com.ges.boutique.role;

import com.ges.boutique.exception.PermissionRefuseeException;
import com.ges.boutique.utilisateur.RoleUtilisateur;
import com.ges.boutique.utilisateur.Utilisateur;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

/**
 * Rôles personnalisés : le contrôle central ne doit bloquer que ce que le rôle n'a pas
 * coché, et ne jamais changer le comportement quand la fonctionnalité est désactivée.
 */
@ExtendWith(MockitoExtension.class)
class PermissionInterceptorTest {

    @Mock private RoleBoutiqueService roleService;
    @InjectMocks private PermissionInterceptor interceptor;

    @AfterEach
    void nettoyer() {
        SecurityContextHolder.clearContext();
    }

    private Utilisateur connecter(RoleUtilisateur role) {
        Utilisateur u = new Utilisateur();
        u.setId(7L);
        u.setUsername("caissier");
        u.setRole(role);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(u, null, u.getAuthorities()));
        return u;
    }

    private boolean appeler(String methode, String chemin) {
        MockHttpServletRequest req = new MockHttpServletRequest(methode, chemin);
        return interceptor.preHandle(req, new MockHttpServletResponse(), new Object());
    }

    @Test
    void choisitLaPartieLaPlusPrecise() {
        assertEquals(ModulePermission.VENTES, PermissionInterceptor.trouverModule("/api/caisse/credits/reglement"));
        assertEquals(ModulePermission.CAISSE, PermissionInterceptor.trouverModule("/api/caisse/solde"));
        assertEquals(ModulePermission.PRODUITS, PermissionInterceptor.trouverModule("/api/produits/12/image"));
        assertNull(PermissionInterceptor.trouverModule("/api/public/produits/12/image"));
        assertNull(PermissionInterceptor.trouverModule("/api/ventesxyz"));
    }

    @Test
    void sansRestrictionToutPasse() {
        Utilisateur u = connecter(RoleUtilisateur.VENDEUR);
        when(roleService.permissionsEffectives(u)).thenReturn(null);
        assertTrue(appeler("DELETE", "/api/commandes/3"));
    }

    @Test
    void bloqueUneActionNonCochee() {
        Utilisateur u = connecter(RoleUtilisateur.VENDEUR);
        when(roleService.permissionsEffectives(u)).thenReturn(Set.of("VENTES_VOIR", "VENTES_GERER"));
        assertTrue(appeler("POST", "/api/ventes"));
        assertThrows(PermissionRefuseeException.class, () -> appeler("POST", "/api/ventes/5/annuler"));
        assertThrows(PermissionRefuseeException.class, () -> appeler("POST", "/api/commandes"));
    }

    @Test
    void uneAdresseCodeeNeContournePasLeControle() {
        Utilisateur u = connecter(RoleUtilisateur.VENDEUR);
        when(roleService.permissionsEffectives(u)).thenReturn(Set.of("VENTES_VOIR"));
        assertThrows(PermissionRefuseeException.class, () -> appeler("POST", "/api/%63ommandes"));
        assertThrows(PermissionRefuseeException.class, () -> appeler("POST", "/api/commandes;x=1"));
    }

    @Test
    void laLectureDesDonneesCommunesResteOuverte() {
        Utilisateur u = connecter(RoleUtilisateur.VENDEUR);
        when(roleService.permissionsEffectives(u)).thenReturn(Set.of("VENTES_VOIR"));
        // La page Ventes a besoin de lire produits et clients, même sans les droits "Voir".
        assertTrue(appeler("GET", "/api/produits"));
        assertTrue(appeler("GET", "/api/clients"));
    }

    @Test
    void lesPartiesSensiblesSontProtegeesEnLecture() {
        Utilisateur u = connecter(RoleUtilisateur.ADMIN);
        when(roleService.permissionsEffectives(u)).thenReturn(Set.of("VENTES_VOIR"));
        assertThrows(PermissionRefuseeException.class, () -> appeler("GET", "/api/benefices"));
        assertThrows(PermissionRefuseeException.class, () -> appeler("GET", "/api/employes"));
        // Lectures toujours libres (infos boutique, liste du personnel, ses propres primes).
        assertTrue(appeler("GET", "/api/boutique"));
        assertTrue(appeler("GET", "/api/commissions/mes-primes"));
    }

    @Test
    void unVendeurGardeSonAccesAuxPartiesHorsTableauNonReservees() {
        Utilisateur u = connecter(RoleUtilisateur.VENDEUR);
        when(roleService.permissionsEffectives(u)).thenReturn(Set.of("VENTES_VOIR"));
        // Prévisions IA de l'accueil mobile : hors tableau, décidé par les @PreAuthorize.
        assertTrue(appeler("GET", "/api/ia/insights"));
        // Mais les parties du tableau restent contrôlées.
        assertThrows(PermissionRefuseeException.class, () -> appeler("POST", "/api/ventes"));
    }

    @Test
    void unVendeurNeLitJamaisLesDonneesReserveesAuGerant() {
        // Avec ou sans rôles personnalisés (permissionsEffectives = null quand désactivés).
        Utilisateur u = connecter(RoleUtilisateur.VENDEUR);
        when(roleService.permissionsEffectives(u)).thenReturn(null);
        assertThrows(PermissionRefuseeException.class, () -> appeler("GET", "/api/employes"));
        assertThrows(PermissionRefuseeException.class, () -> appeler("GET", "/api/paiements-employe"));
        assertThrows(PermissionRefuseeException.class, () -> appeler("GET", "/api/depenses"));
        assertThrows(PermissionRefuseeException.class, () -> appeler("GET", "/api/benefices/aujourdhui"));
        assertThrows(PermissionRefuseeException.class, () -> appeler("GET", "/api/caisse/solde"));
        assertThrows(PermissionRefuseeException.class, () -> appeler("GET", "/api/fournisseur-achats"));
        assertThrows(PermissionRefuseeException.class, () -> appeler("POST", "/api/depenses"));
        // Ses écrans habituels restent ouverts : crédits, paiements groupés, lectures libres.
        assertTrue(appeler("GET", "/api/caisse/credits/non-regles"));
        assertTrue(appeler("GET", "/api/caisse/paiements-groupes"));
        assertTrue(appeler("GET", "/api/utilisateurs"));
    }
}
