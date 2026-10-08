package com.ges.boutique.utilisateur;

import com.ges.boutique.employe.Employe;
import com.ges.boutique.employe.EmployeRepository;
import com.ges.boutique.employe.StatutEmploye;
import com.ges.boutique.exception.RessourceIntrouvableException;
import com.ges.boutique.journalaudit.JournalAuditService;
import com.ges.boutique.journalaudit.TypeActionAudit;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

@Service
@RequiredArgsConstructor
public class UtilisateurServiceImpl implements UtilisateurService {

    private final UtilisateurRepository utilisateurRepository;
    private final EmployeRepository employeRepository;
    private final PasswordEncoder passwordEncoder;
    private final JournalAuditService journalAuditService;

    @Override
    @Transactional
    public Utilisateur creerUtilisateur(Utilisateur utilisateur) {
        if (utilisateurRepository.existsByUsername(utilisateur.getUsername())) {
            throw new RuntimeException("Le nom d'utilisateur existe déjà");
        }
        if (utilisateurRepository.existsByEmail(utilisateur.getEmail())) {
            throw new RuntimeException("L'email existe déjà");
        }

        // Le statut super admin ne se donne jamais via cet endpoint (accessible à tout
        // ADMIN de boutique) — seul le compte créé automatiquement au démarrage
        // (DataInitializer) ou un accès direct à la base peut l'obtenir, pour qu'un
        // admin classique ne puisse pas se l'auto-attribuer en le glissant dans le JSON.
        utilisateur.setSuperAdmin(false);
        // Le rôle personnalisé s'attribue ensuite via PUT /api/roles/utilisateurs/{id}.
        utilisateur.setRoleBoutiqueId(null);
        // VENDEUR par défaut si le formulaire ne précise pas de rôle (le champ n'a plus
        // de valeur par défaut sur l'entité elle-même — voir Utilisateur.java).
        if (utilisateur.getRole() == null) {
            utilisateur.setRole(RoleUtilisateur.VENDEUR);
        }
        utilisateur.setPassword(passwordEncoder.encode(utilisateur.getPassword()));
        return utilisateurRepository.save(utilisateur);
    }

    @Override
    @Transactional
    public Utilisateur modifierUtilisateur(Long id, Utilisateur utilisateurDetails) {
        Utilisateur utilisateur = utilisateurRepository.findById(id)
                .orElseThrow(() -> new RessourceIntrouvableException("Utilisateur non trouvé avec l'ID: " + id));
        bloquerSiSuperAdminProtege(utilisateur);

        if (utilisateurDetails.getNomComplet() != null) {
            utilisateur.setNomComplet(utilisateurDetails.getNomComplet());
        }
        if (utilisateurDetails.getUsername() != null && !utilisateurDetails.getUsername().equals(utilisateur.getUsername())) {
            if (utilisateurRepository.existsByUsername(utilisateurDetails.getUsername())) {
                throw new RuntimeException("Ce nom d'utilisateur existe déjà");
            }
            utilisateur.setUsername(utilisateurDetails.getUsername());
        }
        if (utilisateurDetails.getEmail() != null && !utilisateurDetails.getEmail().equals(utilisateur.getEmail())) {
            if (utilisateurRepository.existsByEmail(utilisateurDetails.getEmail())) {
                throw new RuntimeException("L'email existe déjà");
            }
            utilisateur.setEmail(utilisateurDetails.getEmail());
        }
        if (utilisateurDetails.getTelephone() != null) {
            utilisateur.setTelephone(utilisateurDetails.getTelephone());
        }
        // Mot de passe vide = « ne pas changer » : les formulaires de modification envoient ""
        // quand la case est laissée vide (sinon la personne ne pouvait plus se connecter).
        if (utilisateurDetails.getPassword() != null && !utilisateurDetails.getPassword().isBlank()) {
            utilisateur.setPassword(passwordEncoder.encode(utilisateurDetails.getPassword()));
        }
        RoleUtilisateur ancienRole = utilisateur.getRole();
        boolean roleChange = utilisateurDetails.getRole() != null && utilisateurDetails.getRole() != ancienRole;
        if (utilisateurDetails.getRole() != null) {
            utilisateur.setRole(utilisateurDetails.getRole());
        }
        // Un rôle personnalisé dépend du rôle de base : s'il change, on revient au rôle
        // système (Gérant/Vendeur) plutôt que de garder un rôle devenu incohérent.
        if (roleChange) {
            utilisateur.setRoleBoutiqueId(null);
        }
        // NB: "actif" n'est volontairement pas repris ici — c'est un booléen primitif
        // (jamais absent du JSON désérialisé, toujours true ou false), donc l'appliquer
        // sans condition écraserait le statut à chaque modification, y compris pour les
        // formulaires qui ne connaissent pas ce champ. Le gel/dégel passe par un endpoint
        // dédié (changerStatutUtilisateur) qui ne touche que ce champ.

        Utilisateur saved = utilisateurRepository.save(utilisateur);

        if (roleChange) {
            Utilisateur auteur = getUtilisateurCourantAudit();
            journalAuditService.enregistrer(
                    auteur != null ? auteur.getId() : null,
                    auteur != null ? auteur.getNomComplet() : null,
                    TypeActionAudit.MODIFICATION_ROLE_UTILISATEUR,
                    "Utilisateur #" + saved.getId() + " (" + saved.getUsername() + ") : rôle " + ancienRole
                            + " -> " + saved.getRole());
        }

        return saved;
    }

    /**
     * Utilisateur actuellement authentifié (contexte de sécurité Spring), pour les besoins
     * du journal d'audit — même mécanisme que celui déjà utilisé ailleurs dans le projet
     * (ex: DepenseController.getUserId(), ProduitNiveauController.getUserId()).
     */
    private Utilisateur getUtilisateurCourantAudit() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof Utilisateur u) {
            return u;
        }
        return null;
    }

    /**
     * Le compte super admin (flag superAdmin, voir Utilisateur.java) doit être invisible
     * et intouchable pour un admin de boutique classique — un admin normal ne doit même
     * pas pouvoir déduire qu'il existe. On renvoie donc la même exception "introuvable"
     * que pour un ID qui n'existe pas, plutôt qu'un refus d'accès qui révélerait sa présence.
     */
    private void bloquerSiSuperAdminProtege(Utilisateur cible) {
        if (!cible.isSuperAdmin()) return;
        Utilisateur appelant = getUtilisateurCourantAudit();
        if (appelant != null && appelant.isSuperAdmin()) return;
        throw new RessourceIntrouvableException("Utilisateur non trouvé avec l'ID: " + cible.getId());
    }

    @Override
    @Transactional
    public Utilisateur changerStatutUtilisateur(Long id, boolean actif) {
        Utilisateur utilisateur = utilisateurRepository.findById(id)
                .orElseThrow(() -> new RessourceIntrouvableException("Utilisateur non trouvé avec l'ID: " + id));
        bloquerSiSuperAdminProtege(utilisateur);
        utilisateur.setActif(actif);
        return utilisateurRepository.save(utilisateur);
    }

    @Override
    @Transactional
    public Utilisateur gererLienEmploye(Long id, LienEmployeRequest request) {
        Utilisateur utilisateur = utilisateurRepository.findById(id)
                .orElseThrow(() -> new RessourceIntrouvableException("Utilisateur non trouvé avec l'ID: " + id));
        bloquerSiSuperAdminProtege(utilisateur);

        if (request.isEstEmploye()) {
            Employe employe;
            if (utilisateur.getEmployeId() != null) {
                // Déjà lié une fois : on met à jour la même fiche (pas de doublon) et on la réactive.
                employe = employeRepository.findById(utilisateur.getEmployeId())
                        .orElse(null);
            } else {
                employe = null;
            }
            if (employe == null) {
                employe = new Employe();
                employe.setDateEmbauche(LocalDate.now());
            }
            employe.setNom(utilisateur.getNomComplet());
            employe.setTelephone(utilisateur.getTelephone());
            employe.setPoste(request.getPoste() != null && !request.getPoste().isBlank() ? request.getPoste() : "Vendeur");
            employe.setSalaireMensuel(request.getSalaireMensuel() != null ? request.getSalaireMensuel() : 0.0);
            employe.setStatut(StatutEmploye.ACTIF);
            employe = employeRepository.save(employe);
            utilisateur.setEmployeId(employe.getId());
        } else if (utilisateur.getEmployeId() != null) {
            // Case décochée : on ne supprime pas l'historique de paie, on désactive juste la fiche
            // (même logique que "désactiver un employé" ailleurs dans l'appli).
            employeRepository.findById(utilisateur.getEmployeId()).ifPresent(employe -> {
                employe.setStatut(StatutEmploye.INACTIF);
                employeRepository.save(employe);
            });
        }

        return utilisateurRepository.save(utilisateur);
    }

    @Override
    @Transactional
    public void supprimerUtilisateur(Long id) {
        Utilisateur utilisateur = utilisateurRepository.findById(id)
                .orElseThrow(() -> new RessourceIntrouvableException("Utilisateur non trouvé avec l'ID: " + id));
        bloquerSiSuperAdminProtege(utilisateur);
        utilisateur.setActif(false);
        utilisateurRepository.save(utilisateur);
    }

    @Override
    public Utilisateur obtenirUtilisateurParId(Long id) {
        Utilisateur utilisateur = utilisateurRepository.findById(id)
                .orElseThrow(() -> new RessourceIntrouvableException("Utilisateur non trouvé avec l'ID: " + id));
        bloquerSiSuperAdminProtege(utilisateur);
        return utilisateur;
    }

    @Override
    public List<Utilisateur> obtenirTousLesUtilisateurs() {
        List<Utilisateur> tous = utilisateurRepository.findAll();
        Utilisateur appelant = getUtilisateurCourantAudit();
        if (appelant != null && appelant.isSuperAdmin()) return tous;
        // Le compte super admin n'apparaît pas dans la liste du personnel pour un admin
        // classique — voir bloquerSiSuperAdminProtege pour les autres opérations.
        return tous.stream().filter(u -> !u.isSuperAdmin()).toList();
    }

    @Override
    public Utilisateur obtenirUtilisateurParUsername(String username) {
        return utilisateurRepository.findByUsername(username)
                .orElseThrow(() -> new RessourceIntrouvableException("Utilisateur non trouvé avec le nom d'utilisateur: " + username));
    }

    @Override
    public long compterUtilisateurs() {
        return utilisateurRepository.count();
    }

    @Override
    @Transactional
    public Utilisateur mettreAJourPhoto(Long id, String photo) {
        Utilisateur utilisateur = utilisateurRepository.findById(id)
                .orElseThrow(() -> new RessourceIntrouvableException("Utilisateur non trouvé avec l'ID: " + id));
        utilisateur.setPhoto(photo);
        return utilisateurRepository.save(utilisateur);
    }

    @Override
    public UserDetails loadUserByUsername(String identifier) throws UsernameNotFoundException {
        // Chercher par username, puis email, puis téléphone
        return utilisateurRepository.findByUsername(identifier)
                .or(() -> utilisateurRepository.findByEmail(identifier))
                .or(() -> utilisateurRepository.findByTelephone(identifier))
                .orElseThrow(() -> new UsernameNotFoundException("Utilisateur non trouvé: " + identifier));
    }
}