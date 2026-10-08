package com.ges.boutique.vitrine;

import lombok.Data;

import java.util.List;

/**
 * Suivi des commandes depuis la vitrine, SANS compte : le téléphone de la boutique garde
 * les numéros de ses commandes ; le serveur ne répond que si numéro ET téléphone
 * correspondent (envoyé en POST pour ne pas laisser le téléphone dans les adresses/logs).
 */
@Data
public class VitrineSuiviRequest {
    private String telephone;
    private List<String> numeros;
}
