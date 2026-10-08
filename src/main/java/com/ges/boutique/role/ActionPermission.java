package com.ges.boutique.role;

/** Ce qu'un rôle peut faire dans une partie de l'application (voir ModulePermission). */
public enum ActionPermission {
    VOIR("Voir"),
    GERER("Ajouter / modifier"),
    SUPPRIMER("Supprimer / annuler");

    private final String libelle;

    ActionPermission(String libelle) {
        this.libelle = libelle;
    }

    public String getLibelle() {
        return libelle;
    }
}
