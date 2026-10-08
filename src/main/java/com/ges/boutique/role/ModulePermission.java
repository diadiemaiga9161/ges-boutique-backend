package com.ges.boutique.role;

import java.util.List;

/**
 * Parties de l'application sur lesquelles un rôle personnalisé peut recevoir ou perdre des
 * droits (voir RoleBoutique). Chaque partie regroupe des préfixes d'URL d'API : le contrôle
 * est fait de façon centrale par PermissionInterceptor, sans toucher aux @PreAuthorize déjà
 * en place sur les contrôleurs (qui restent le plafond imposé par le rôle de base
 * ADMIN/VENDEUR).
 *
 * lectureProtegee = false : la consultation (GET) reste ouverte même sans le droit VOIR, car
 * d'autres écrans en ont besoin (ex: la page Ventes lit la liste des produits et des clients).
 * Le droit VOIR masque alors seulement l'entrée de menu et la page. Pour les parties
 * sensibles (finances, paie, journal...), lectureProtegee = true bloque aussi l'API.
 *
 * toutEnLecture = true : les POST de cette partie servent à consulter (générer un rapport,
 * poser une question à l'IA), ils demandent donc VOIR et pas GERER.
 */
public enum ModulePermission {
    VENTES("Ventes, factures et crédits",
            List.of("/api/ventes", "/api/retours-ventes", "/api/caisse/factures", "/api/caisse/credits",
                    "/api/caisse/paiements-groupes"),
            false, false, true),
    PRODUITS("Produits, catégories et photos",
            List.of("/api/produits", "/api/promotions"),
            false, false, true),
    STOCK("Inventaire et transferts de stock",
            List.of("/api/inventaire", "/api/transferts", "/api/previsions"),
            false, false, true),
    CLIENTS("Clients, partenaires et dettes",
            List.of("/api/clients", "/api/avances", "/api/depot-clients", "/api/dettes-anciennes", "/api/fidelite"),
            false, false, true),
    COMMANDES("Commandes",
            List.of("/api/commandes"),
            false, false, true),
    CAISSE("Caisse, comptes et Mobile Money",
            List.of("/api/caisse", "/api/mobile-money", "/api/depots-garde", "/api/comptes"),
            true, false, false),
    // Séparée de FINANCES : un caissier peut noter les dépenses sans voir les bénéfices.
    DEPENSES("Dépenses",
            List.of("/api/depenses", "/api/types-depense"),
            true, false, false),
    FINANCES("Bénéfices et résultat net",
            List.of("/api/benefices", "/api/resultat-net"),
            true, false, false),
    RAPPORTS("Rapports",
            List.of("/api/rapports"),
            false, true, true),
    FOURNISSEURS("Fournisseurs et achats",
            List.of("/api/fournisseur-achats", "/api/avances-fournisseurs", "/api/retours-achats",
                    "/api/objectifs-fournisseur", "/api/bonus-fournisseurs"),
            true, false, false),
    PERSONNEL("Employés, vendeurs, paie et rôles",
            List.of("/api/employes", "/api/paiements-employe", "/api/utilisateurs", "/api/objectifs-vendeur", "/api/roles"),
            true, false, false),
    PRIMES("Primes sur produits",
            List.of("/api/commissions"),
            true, false, false),
    ADMINISTRATION("Paramètres, journal et sauvegardes",
            List.of("/api/boutique", "/api/parametres", "/api/journal-audit", "/api/backup"),
            true, false, false),
    IA("Intelligence artificielle",
            List.of("/api/ia"),
            true, true, false);

    private final String libelle;
    private final List<String> prefixes;
    private final boolean lectureProtegee;
    private final boolean toutEnLecture;
    /** Partie accessible à un rôle de base VENDEUR (sinon seul un rôle basé sur Gérant en profite). */
    private final boolean accessibleVendeur;

    ModulePermission(String libelle, List<String> prefixes, boolean lectureProtegee,
                     boolean toutEnLecture, boolean accessibleVendeur) {
        this.libelle = libelle;
        this.prefixes = prefixes;
        this.lectureProtegee = lectureProtegee;
        this.toutEnLecture = toutEnLecture;
        this.accessibleVendeur = accessibleVendeur;
    }

    public String getLibelle() { return libelle; }
    public List<String> getPrefixes() { return prefixes; }
    public boolean isLectureProtegee() { return lectureProtegee; }
    public boolean isToutEnLecture() { return toutEnLecture; }
    public boolean isAccessibleVendeur() { return accessibleVendeur; }

    /** Données de gestion que le rôle Vendeur ne doit jamais lire ni modifier (salaires,
     *  dépenses, caisse, achats) — refusées par PermissionInterceptor même sans rôles
     *  personnalisés. PRIMES / ADMINISTRATION / IA restent décidés par les @PreAuthorize. */
    public boolean isReserveeGerant() {
        return this == CAISSE || this == DEPENSES || this == FINANCES || this == FOURNISSEURS || this == PERSONNEL;
    }

    /** Code stocké dans RoleBoutique.permissions, ex: "VENTES_VOIR". */
    public String code(ActionPermission action) {
        return name() + "_" + action.name();
    }
}
