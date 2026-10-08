package com.ges.boutique.vitrine;

import lombok.Data;

import java.util.List;

/**
 * Suivi des commandes depuis la vitrine, SANS compte : le téléphone du client garde le
 * numéro de chaque commande et son code secret (remis à la commande). Le serveur ne
 * répond que si le numéro ET son code correspondent : le numéro seul se devine.
 */
@Data
public class VitrineSuiviRequest {
    private List<Suivi> commandes;

    @Data
    public static class Suivi {
        private String numero;
        private String code;
    }
}
