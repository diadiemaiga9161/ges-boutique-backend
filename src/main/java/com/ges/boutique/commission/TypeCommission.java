package com.ges.boutique.commission;

/** Les trois façons de calculer une prime sur produit (chacune activable par le gérant). */
public enum TypeCommission {
    /** Montant fixe gagné pour chaque unité vendue. */
    MONTANT_FIXE("Montant fixe par unité vendue"),
    /** Pourcentage du montant encaissé sur le produit. */
    POURCENTAGE("Pourcentage du montant vendu"),
    /** Prime unique quand le vendeur atteint une quantité sur la période de la règle. */
    OBJECTIF("Prime sur objectif de quantité");

    private final String libelle;

    TypeCommission(String libelle) {
        this.libelle = libelle;
    }

    public String getLibelle() {
        return libelle;
    }
}
