package com.ges.boutique.role;

import com.ges.boutique.exception.RessourceIntrouvableException;
import com.ges.boutique.feature.CleFonctionnalite;
import com.ges.boutique.feature.FeatureToggleService;
import com.ges.boutique.utilisateur.RoleUtilisateur;
import com.ges.boutique.utilisateur.Utilisateur;
import com.ges.boutique.utilisateur.UtilisateurRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Service
@RequiredArgsConstructor
public class RoleBoutiqueService {

    private final RoleBoutiqueRepository repository;
    private final UtilisateurRepository utilisateurRepository;
    private final FeatureToggleService featureToggleService;

    // ---------------------------------------------------------------- catalogue

    /** Tous les codes possibles (module x action), pour un rôle de base donné. */
    public static Set<String> tousLesCodes(RoleUtilisateur roleBase) {
        Set<String> codes = new LinkedHashSet<>();
        for (ModulePermission m : ModulePermission.values()) {
            if (roleBase == RoleUtilisateur.VENDEUR && !m.isAccessibleVendeur()) continue;
            for (ActionPermission a : ActionPermission.values()) {
                if (m.isToutEnLecture() && a != ActionPermission.VOIR) continue;
                codes.add(m.code(a));
            }
        }
        return codes;
    }

    public List<Map<String, Object>> catalogue() {
        List<Map<String, Object>> liste = new ArrayList<>();
        for (ModulePermission m : ModulePermission.values()) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("module", m.name());
            item.put("libelle", m.getLibelle());
            item.put("accessibleVendeur", m.isAccessibleVendeur());
            List<Map<String, String>> actions = new ArrayList<>();
            for (ActionPermission a : ActionPermission.values()) {
                if (m.isToutEnLecture() && a != ActionPermission.VOIR) continue;
                actions.add(Map.of("action", a.name(), "libelle", a.getLibelle(), "code", m.code(a)));
            }
            item.put("actions", actions);
            liste.add(item);
        }
        return liste;
    }

    // ---------------------------------------------------------------- rôles système

    /** Crée Gérant et Vendeur au premier besoin (pas de seed SQL : marche sur une base neuve). */
    @Transactional
    public void initialiserRolesSysteme() {
        if (repository.findFirstBySystemeTrueAndRoleBase(RoleUtilisateur.ADMIN).isEmpty()) {
            RoleBoutique gerant = new RoleBoutique();
            gerant.setNom("Gérant");
            gerant.setDescription("Tous les droits sur la boutique");
            gerant.setRoleBase(RoleUtilisateur.ADMIN);
            gerant.setSysteme(true);
            gerant.setPermissionsSet(tousLesCodes(RoleUtilisateur.ADMIN));
            gerant.setDepensesSeparees(true);
            repository.save(gerant);
        }
        if (repository.findFirstBySystemeTrueAndRoleBase(RoleUtilisateur.VENDEUR).isEmpty()) {
            RoleBoutique vendeur = new RoleBoutique();
            vendeur.setNom("Vendeur");
            vendeur.setDescription("Vente au comptoir");
            vendeur.setRoleBase(RoleUtilisateur.VENDEUR);
            vendeur.setSysteme(true);
            vendeur.setPermissionsSet(sansMouvementsStock(tousLesCodes(RoleUtilisateur.VENDEUR)));
            vendeur.setDroitsStockAjustes(true);
            vendeur.setDepensesSeparees(true);
            repository.save(vendeur);
        }
    }

    // ---------------------------------------------------------------- entrées / sorties de stock

    private static final Set<String> MOUVEMENTS_STOCK = Set.of("STOCK_GERER", "STOCK_SUPPRIMER");

    private static Set<String> sansMouvementsStock(Set<String> codes) {
        Set<String> r = new LinkedHashSet<>(codes);
        r.removeAll(MOUVEMENTS_STOCK);
        return r;
    }

    /**
     * Une seule fois : le rôle Vendeur système avait « Inventaire : Tout » par défaut, sans
     * effet (entrées/sorties réservées au gérant). Ces cases donnant maintenant vraiment les
     * entrées/sorties, on les ramène à « Voir » pour que rien ne s'ouvre sans décision du
     * gérant. Il pourra ensuite remettre « Modifier »/« Tout » s'il le souhaite.
     */
    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    @CacheEvict(value = "rolesBoutique", allEntries = true)
    public void ajusterDroitsStockVendeur() {
        repository.findFirstBySystemeTrueAndRoleBase(RoleUtilisateur.VENDEUR).ifPresent(r -> {
            if (Boolean.TRUE.equals(r.getDroitsStockAjustes())) return;
            r.setPermissionsSet(sansMouvementsStock(r.getPermissionsSet()));
            r.setDroitsStockAjustes(true);
            repository.save(r);
        });
    }

    /**
     * Une seule fois par rôle : « Finances » contenait les dépenses. Elles ont maintenant leur
     * propre case « Dépenses » ; chaque rôle existant reçoit Dépenses au même niveau que
     * Finances, pour que personne ne perde rien. Le gérant peut ensuite retirer les bénéfices.
     */
    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    @CacheEvict(value = "rolesBoutique", allEntries = true)
    public void separerDepenses() {
        for (RoleBoutique r : repository.findAll()) {
            if (Boolean.TRUE.equals(r.getDepensesSeparees())) continue;
            Set<String> p = r.getPermissionsSet();
            for (ActionPermission a : ActionPermission.values()) {
                if (p.contains(ModulePermission.FINANCES.code(a))) p.add(ModulePermission.DEPENSES.code(a));
            }
            r.setPermissionsSet(p);
            r.setDepensesSeparees(true);
            repository.save(r);
        }
    }

    /**
     * Un vendeur peut-il faire cette action de stock ? Seulement si les rôles personnalisés
     * sont activés et que son rôle (Vendeur ou rôle créé) a la case cochée. Utilisé par les
     * @PreAuthorize de l'inventaire (entrées, sorties, ajustements).
     */
    public boolean vendeurPeut(Object principal, String code) {
        if (!(principal instanceof Utilisateur u) || u.getRole() != RoleUtilisateur.VENDEUR) return false;
        if (!fonctionnaliteActive()) return false;
        RoleBoutique systeme = repository.findFirstBySystemeTrueAndRoleBase(RoleUtilisateur.VENDEUR).orElse(null);
        // Sécurité : tant que l'ajustement unique n'est pas fait, le rôle Vendeur système
        // ne donne pas les mouvements de stock.
        if (u.getRoleBoutiqueId() == null && MOUVEMENTS_STOCK.contains(code)
                && (systeme == null || !Boolean.TRUE.equals(systeme.getDroitsStockAjustes()))) return false;
        Set<String> p = permissionsEffectives(u);
        return p != null && p.contains(code);
    }

    // ---------------------------------------------------------------- lecture

    public List<Map<String, Object>> lister() {
        initialiserRolesSysteme();
        List<Map<String, Object>> liste = new ArrayList<>();
        for (RoleBoutique r : repository.findAllByOrderBySystemeDescNomAsc()) {
            liste.add(toMap(r));
        }
        return liste;
    }

    private Map<String, Object> toMap(RoleBoutique r) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", r.getId());
        m.put("nom", r.getNom());
        m.put("description", r.getDescription());
        m.put("roleBase", r.getRoleBase().name());
        m.put("systeme", r.isSysteme());
        m.put("verrouille", r.estGerantSysteme());
        m.put("permissions", r.estGerantSysteme() ? tousLesCodes(RoleUtilisateur.ADMIN) : r.getPermissionsSet());
        long nb = r.isSysteme()
                ? utilisateurRepository.findByRole(r.getRoleBase()).stream()
                    .filter(u -> u.getRoleBoutiqueId() == null && !u.isSuperAdmin()).count()
                : utilisateurRepository.countByRoleBoutiqueId(r.getId());
        m.put("nombreUtilisateurs", nb);
        return m;
    }

    // ---------------------------------------------------------------- écriture

    @Transactional
    @CacheEvict(value = "rolesBoutique", allEntries = true)
    public Map<String, Object> creer(RoleBoutiqueRequest req) {
        valider(req, null);
        RoleBoutique r = new RoleBoutique();
        r.setNom(req.getNom().trim());
        r.setDescription(req.getDescription());
        r.setRoleBase(req.getRoleBase());
        r.setSysteme(false);
        r.setPermissionsSet(filtrerCodes(req.getPermissions(), req.getRoleBase()));
        r.setDepensesSeparees(true);
        return toMap(repository.save(r));
    }

    @Transactional
    @CacheEvict(value = "rolesBoutique", allEntries = true)
    public Map<String, Object> modifier(Long id, RoleBoutiqueRequest req, Utilisateur auteur) {
        RoleBoutique r = repository.findById(id)
                .orElseThrow(() -> new RessourceIntrouvableException("Rôle introuvable"));
        if (r.estGerantSysteme()) {
            throw new IllegalStateException("Le rôle Gérant garde toujours tous les droits, il ne peut pas être modifié.");
        }
        if (!auteur.isSuperAdmin() && Objects.equals(roleEffectifId(auteur), r.getId())) {
            throw new IllegalStateException("Vous ne pouvez pas modifier votre propre rôle.");
        }
        if (r.isSysteme()) {
            // Rôle système Vendeur : seul le contenu des cases change, pas le nom ni la base.
            r.setPermissionsSet(filtrerCodes(req.getPermissions(), r.getRoleBase()));
            r.setDepensesSeparees(true);
        } else {
            valider(req, r.getId());
            if (req.getRoleBase() != r.getRoleBase() && utilisateurRepository.countByRoleBoutiqueId(r.getId()) > 0) {
                throw new IllegalStateException("Ce rôle est attribué à des personnes : son rôle de base ne peut plus changer.");
            }
            r.setNom(req.getNom().trim());
            r.setDescription(req.getDescription());
            r.setRoleBase(req.getRoleBase());
            r.setPermissionsSet(filtrerCodes(req.getPermissions(), req.getRoleBase()));
        r.setDepensesSeparees(true);
        }
        return toMap(repository.save(r));
    }

    @Transactional
    @CacheEvict(value = "rolesBoutique", allEntries = true)
    public void supprimer(Long id) {
        RoleBoutique r = repository.findById(id)
                .orElseThrow(() -> new RessourceIntrouvableException("Rôle introuvable"));
        if (r.isSysteme()) {
            throw new IllegalStateException("Les rôles Gérant et Vendeur ne peuvent pas être supprimés.");
        }
        long nb = utilisateurRepository.countByRoleBoutiqueId(id);
        if (nb > 0) {
            throw new IllegalStateException("Ce rôle est attribué à " + nb + " personne(s). Changez d'abord leur rôle.");
        }
        repository.delete(r);
    }

    /** Attribue un rôle à une personne. roleId null ou rôle système = retour au rôle de base. */
    @Transactional
    @CacheEvict(value = "rolesBoutique", allEntries = true)
    public Utilisateur attribuer(Long utilisateurId, Long roleId, Utilisateur auteur) {
        Utilisateur u = utilisateurRepository.findById(utilisateurId)
                .orElseThrow(() -> new RessourceIntrouvableException("Utilisateur introuvable"));
        if (u.isSuperAdmin() && !auteur.isSuperAdmin()) {
            throw new RessourceIntrouvableException("Utilisateur introuvable");
        }
        if (!auteur.isSuperAdmin() && Objects.equals(u.getId(), auteur.getId())) {
            throw new IllegalStateException("Vous ne pouvez pas changer votre propre rôle.");
        }
        if (roleId == null) {
            u.setRoleBoutiqueId(null);
            return utilisateurRepository.save(u);
        }
        RoleBoutique r = repository.findById(roleId)
                .orElseThrow(() -> new RessourceIntrouvableException("Rôle introuvable"));
        // Le rôle de base suit le rôle choisi : c'est lui qui fixe le plafond des droits.
        u.setRole(r.getRoleBase());
        u.setRoleBoutiqueId(r.isSysteme() ? null : r.getId());
        return utilisateurRepository.save(u);
    }

    private void valider(RoleBoutiqueRequest req, Long idExistant) {
        if (req.getNom() == null || req.getNom().isBlank()) {
            throw new IllegalArgumentException("Le nom du rôle est obligatoire.");
        }
        if (req.getRoleBase() == null) {
            throw new IllegalArgumentException("Choisissez si ce rôle est basé sur Gérant ou Vendeur.");
        }
        String nom = req.getNom().trim();
        boolean doublon = repository.findAll().stream()
                .anyMatch(r -> r.getNom().equalsIgnoreCase(nom) && !Objects.equals(r.getId(), idExistant));
        if (doublon) {
            throw new IllegalArgumentException("Un rôle porte déjà ce nom.");
        }
    }

    /** Ne garde que des codes connus et compatibles avec le rôle de base. */
    private Set<String> filtrerCodes(Collection<String> demandes, RoleUtilisateur roleBase) {
        Set<String> permis = tousLesCodes(roleBase);
        Set<String> result = new LinkedHashSet<>();
        if (demandes != null) {
            for (String c : demandes) if (permis.contains(c)) result.add(c);
        }
        return result;
    }

    // ---------------------------------------------------------------- vérification

    private Long roleEffectifId(Utilisateur u) {
        if (u.getRoleBoutiqueId() != null) return u.getRoleBoutiqueId();
        return repository.findFirstBySystemeTrueAndRoleBase(u.getRole()).map(RoleBoutique::getId).orElse(null);
    }

    public boolean fonctionnaliteActive() {
        return featureToggleService.estActive(CleFonctionnalite.ROLES_PERSONNALISES);
    }

    /**
     * Permissions effectives d'une personne, ou null = aucune restriction (fonctionnalité
     * désactivée, super admin, Gérant système). Mise en cache (30s) car appelée à chaque
     * requête API via PermissionInterceptor.
     */
    @Cacheable(value = "rolesBoutique", key = "#u.id + ':' + #u.role + ':' + #u.roleBoutiqueId")
    public Set<String> permissionsEffectives(Utilisateur u) {
        if (u.isSuperAdmin() || !fonctionnaliteActive()) return null;
        RoleBoutique r = null;
        if (u.getRoleBoutiqueId() != null) {
            r = repository.findById(u.getRoleBoutiqueId()).orElse(null);
            // Rôle supprimé ou incohérent : on retombe sur le rôle système, jamais "tout bloqué".
            if (r != null && r.getRoleBase() != u.getRole()) r = null;
        }
        if (r == null) {
            r = repository.findFirstBySystemeTrueAndRoleBase(u.getRole()).orElse(null);
        }
        if (r == null || r.estGerantSysteme()) return null;
        return r.getPermissionsSet();
    }

    public String nomRoleEffectif(Utilisateur u) {
        if (u.getRoleBoutiqueId() != null) {
            Optional<RoleBoutique> r = repository.findById(u.getRoleBoutiqueId());
            if (r.isPresent() && r.get().getRoleBase() == u.getRole()) return r.get().getNom();
        }
        return u.getRole() == RoleUtilisateur.ADMIN ? "Gérant" : "Vendeur";
    }
}
