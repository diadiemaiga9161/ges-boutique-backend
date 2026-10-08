package com.ges.boutique.role;

import com.ges.boutique.utilisateur.RoleUtilisateur;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Rôle de la boutique (Gérant, Vendeur, Caissier...). Chaque rôle s'appuie sur un rôle de
 * base technique (ADMIN ou VENDEUR) qui fixe le plafond : les permissions cochées ici ne
 * peuvent que restreindre ce plafond, jamais le dépasser.
 *
 * Les deux rôles "système" (Gérant, Vendeur) sont créés automatiquement et s'appliquent à
 * tout utilisateur sans rôle personnalisé. Le Gérant garde toujours tous les droits.
 */
@Entity
@Table(name = "role_boutique")
@Getter
@Setter
@NoArgsConstructor
public class RoleBoutique {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 60)
    private String nom;

    @Column(length = 255)
    private String description;

    // VARCHAR plutôt qu'ENUM MySQL natif (voir PermissionVendeur pour la raison).
    @Enumerated(EnumType.STRING)
    @Column(name = "role_base", nullable = false, columnDefinition = "VARCHAR(20)")
    private RoleUtilisateur roleBase;

    @Column(name = "systeme", columnDefinition = "TINYINT(1) DEFAULT 0")
    private boolean systeme = false;

    /** Codes accordés séparés par des virgules, ex: "VENTES_VOIR,VENTES_GERER". */
    @Column(name = "permissions", columnDefinition = "TEXT")
    private String permissions = "";

    @Column(name = "date_creation")
    private LocalDateTime dateCreation;

    /** Rôle Vendeur système : droits « Inventaire » déjà ramenés à « Voir » (une seule fois,
     *  voir RoleBoutiqueService.ajusterDroitsStockVendeur). null = pas encore fait. */
    @Column(name = "droits_stock_ajustes")
    private Boolean droitsStockAjustes;

    /** Rôle déjà passé à la case « Dépenses » séparée de « Finances » (une seule fois, voir
     *  RoleBoutiqueService.separerDepenses). null = rôle créé avant la séparation. */
    @Column(name = "depenses_separees")
    private Boolean depensesSeparees;

    @PrePersist
    protected void onCreate() {
        dateCreation = LocalDateTime.now();
    }

    public Set<String> getPermissionsSet() {
        if (permissions == null || permissions.isBlank()) return new LinkedHashSet<>();
        return Arrays.stream(permissions.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    public void setPermissionsSet(Set<String> codes) {
        this.permissions = codes == null ? "" : String.join(",", codes);
    }

    /** Le Gérant système ne peut jamais être restreint (évite qu'une boutique se bloque elle-même). */
    public boolean estGerantSysteme() {
        return systeme && roleBase == RoleUtilisateur.ADMIN;
    }
}
