package com.ges.boutique.commande;

/**
 * Suivi après confirmation (statut VALIDEE) : volontairement SÉPARÉ de StatutCommande, qui
 * pilote la création de la vente, les filtres et les écrans existants. null = confirmée,
 * pas encore préparée.
 */
public enum EtapeLivraison {
    PRETE,
    LIVREE
}
