package com.ges.boutique.feature;

/**
 * Fonctionnalités activables/désactivables individuellement par le super admin
 * (voir FonctionnaliteAvanceeController) — distinct du système Transferts/Vitrine
 * existant (colonnes dédiées sur Boutique), pour ne pas toucher à ce qui marche déjà.
 * Chaque clé correspond à un ou plusieurs contrôleurs annotés @RequireFeature.
 */
public enum CleFonctionnalite {
    DEPOT_GARDE("Dépôt de garde"),
    DETTES_ANCIENNES("Dettes anciennes"),
    COMPTES_BANCAIRES("Comptes bancaires"),
    PROMOTIONS("Promotions"),
    MOBILE_MONEY("Mobile Money"),
    BONUS_FOURNISSEURS("Bonus fournisseurs"),
    OBJECTIFS_FOURNISSEUR("Objectifs fournisseurs"),
    OBJECTIFS_VENDEUR("Primes vendeurs"),
    RAPPORTS("Rapports"),
    IA("Intelligence artificielle"),
    RESULTAT_NET("Résultat net"),
    PROGRAMME_FIDELITE("Programme de fidélité"),
    VENTE_GROS_DETAIL("Vente en gros et au détail"),
    IMPRESSION_TICKET("Impression de reçu (imprimante thermique)"),
    VOIR_PERSONNES_EN_LIGNE("Voir les personnes en ligne"),
    ENVOI_SAUVEGARDES_EMAIL("Recevoir les sauvegardes par email"),
    IMAGES_PRODUITS("Photos des produits"),
    ROLES_PERSONNALISES("Rôles et permissions personnalisés"),
    PRIMES_PRODUITS("Primes sur produits (vendeurs)"),
    VENTE_A_LA_MESURE("Vente au poids / au litre / au mètre");

    private final String libelle;

    CleFonctionnalite(String libelle) {
        this.libelle = libelle;
    }

    public String getLibelle() {
        return libelle;
    }
}
