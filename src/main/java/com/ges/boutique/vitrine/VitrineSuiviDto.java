package com.ges.boutique.vitrine;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * État d'une commande vitrine vu par le client. ATTENTION SÉCURITÉ : exposé sans
 * authentification — seulement ce que le client a lui-même commandé (pas de prix d'achat,
 * de vendeur, de livreur ni de notes internes).
 */
@Data
public class VitrineSuiviDto {
    private String numero;
    /** BROUILLON (reçue, en attente de la boutique), VALIDEE ou ANNULEE. */
    private String statut;
    /** Après confirmation : PRETE ou LIVREE (null = pas encore prête). */
    private String etapeLivraison;
    private LocalDateTime dateCommande;
    private Double montantTotal;
    private List<Ligne> lignes = new ArrayList<>();

    @Data
    public static class Ligne {
        private String nom;
        private Integer quantite;
    }
}
