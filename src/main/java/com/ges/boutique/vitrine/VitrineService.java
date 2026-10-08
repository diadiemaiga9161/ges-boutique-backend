package com.ges.boutique.vitrine;

import java.util.List;

public interface VitrineService {

    List<VitrineProduitDto> obtenirProduitsVitrine();

    VitrineInfoDto obtenirInfosVitrine();

    /** Commandes vitrine dont le numéro ET le téléphone correspondent (sinon ignorées). */
    List<VitrineSuiviDto> suivreCommandes(VitrineSuiviRequest request);
}
