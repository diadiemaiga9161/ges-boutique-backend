package com.ges.boutique.utilisateur;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

@Entity
@Table(name = "utilisateurs")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Utilisateur implements UserDetails {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String username;

    @Column(nullable = false)
    private String password;

    @Column(nullable = false)
    private String nomComplet;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(nullable = false)
    private String telephone;

    // BUG CORRIGÉ : une valeur par défaut ici (= RoleUtilisateur.VENDEUR) faisait qu'un
    // JSON de modification n'incluant pas "role" désérialisait quand même le champ à
    // VENDEUR (jamais null) au lieu de rester absent — modifierUtilisateur() écrasait
    // alors silencieusement le rôle réel de l'utilisateur (ex: un ADMIN repassait
    // VENDEUR après une simple modification de mot de passe). Le défaut est appliqué
    // explicitement à la création (voir UtilisateurServiceImpl.creerUtilisateur), pas ici.
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private RoleUtilisateur role;

    private boolean actif = true;

    @Column(name = "date_creation")
    private LocalDateTime dateCreation;

    @Column(name = "date_modification")
    private LocalDateTime dateModification;

    @Column(columnDefinition = "LONGTEXT")
    private String photo;

    // Lien optionnel vers la fiche Employé (paie) — coché depuis le formulaire vendeur
    // via l'endpoint dédié /employe (voir UtilisateurServiceImpl.gererLienEmploye).
    @Column(name = "employe_id")
    private Long employeId;

    // Super admin : privilège additionnel réservé au propriétaire de l'app, activé
    // manuellement en base sur un compte ADMIN existant (jamais via l'UI normale).
    // Volontairement PAS un rôle séparé (pas de RoleUtilisateur.SUPER_ADMIN) : le
    // compte garde role=ADMIN, donc tous les contrôles isAdmin()/hasRole('ADMIN')
    // déjà en place (Angular/Ionic/RN, 18 contrôleurs backend) continuent de
    // fonctionner sans rien changer. Ce flag ne débloque QUE les endpoints qui le
    // vérifient explicitement (voir BoutiqueController.modifierFonctionnalites).
    @Column(name = "super_admin", columnDefinition = "TINYINT(1) DEFAULT 0")
    private boolean superAdmin = false;

    // Rôle personnalisé (Caissier, Magasinier...) — voir com.ges.boutique.role. Null = rôle
    // système correspondant à "role" (Gérant ou Vendeur). Ne se modifie que via
    // PUT /api/roles/utilisateurs/{id}, jamais via le formulaire utilisateur classique.
    @Column(name = "role_boutique_id")
    private Long roleBoutiqueId;

    @PrePersist
    protected void onCreate() {
        dateCreation = LocalDateTime.now();
        dateModification = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        dateModification = LocalDateTime.now();
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_" + role.name()));
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return actif;
    }

    // Getters supplémentaires pour assurer la compatibilité
    public String getNomComplet() {
        return this.nomComplet;
    }

    public boolean isActif() {
        return this.actif;
    }
}