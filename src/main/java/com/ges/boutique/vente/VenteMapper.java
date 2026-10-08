package com.ges.boutique.vente;

import com.ges.boutique.utilisateur.UtilisateurMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class VenteMapper {

    private final UtilisateurMapper utilisateurMapper;

    public VenteDto toVenteDto(Vente vente) {
        VenteDto dto = new VenteDto();
        dto.setId(vente.getId());
        dto.setNumeroVente(vente.getNumeroVente());
        dto.setVendeurId(vente.getVendeurId());
        dto.setVendeurNom(getVendeurNom(vente));
        dto.setClientId(vente.getClientId());
        dto.setClientNom(getClientNom(vente));
        dto.setClientPrenom(getClientPrenom(vente));
        dto.setClientTelephone(getClientTelephone(vente));
        dto.setClientDivers(vente.getClientDivers());
        dto.setMontantTotal(vente.getMontantTotal());
        dto.setMontantRemiseTotal(vente.getMontantRemiseTotal());
        dto.setMontantApresRemise(vente.getMontantApresRemise());
        dto.setRemiseGlobale(vente.getRemiseGlobale());
        dto.setTypeRemiseGlobale(vente.getTypeRemiseGlobale());
        dto.setModePaiement(vente.getModePaiement());
        dto.setReferencePaiement(vente.getReferencePaiement());
        dto.setDateVente(vente.getDateVente());
        dto.setEstCredit(vente.getEstCredit());
        dto.setMontantVerse(vente.getMontantVerse());
        dto.setMontantRestant(vente.getMontantRestant());
        dto.setCreditRegle(vente.getCreditRegle());

        // Champs d'annulation et retour
        dto.setAnnulee(vente.getAnnulee());
        dto.setMotifAnnulation(vente.getMotifAnnulation());
        dto.setDateAnnulation(vente.getDateAnnulation());
        dto.setEstRetourne(vente.getEstRetourne());
        dto.setRetourPartiel(vente.getRetourPartiel());
        dto.setMontantRetourne(vente.getMontantRetourne());

        if (vente.getLignes() != null) {
            dto.setLignes(vente.getLignes().stream()
                    .map(this::toLigneVenteDto)
                    .collect(Collectors.toList()));
        }

        if (vente.getVendeur() != null) {
            dto.setVendeur(utilisateurMapper.toVendeurDto(vente.getVendeur()));
        }

        return dto;
    }

    public LigneVenteDto toLigneVenteDto(LigneVente ligne) {
        LigneVenteDto dto = new LigneVenteDto();
        dto.setProduitId(ligne.getProduitId());
        dto.setProduitNom(ligne.getProduitNom());
        dto.setQuantite(ligne.getQuantite());
        dto.setModeMesure(ligne.getProduit() != null ? ligne.getProduit().getModeMesure() : null);
        dto.setPrixUnitaire(ligne.getPrixUnitaire());
        dto.setRemisePourcentage(ligne.getRemisePourcentage());
        dto.setRemiseMontant(ligne.getRemiseMontant());
        dto.setPrixApresRemise(ligne.getPrixApresRemise());
        dto.setSousTotal(ligne.getSousTotal());
        dto.setMontantRemise(ligne.getMontantRemise());
        return dto;
    }

    public Map<String, Object> toVenteMap(Vente vente) {
        Map<String, Object> map = new HashMap<>();
        map.put("id", vente.getId());
        map.put("numeroVente", vente.getNumeroVente());
        map.put("vendeurId", vente.getVendeurId());
        map.put("vendeurNom", getVendeurNom(vente));
        map.put("clientId", vente.getClientId());
        map.put("clientNom", getClientNom(vente));
        map.put("clientPrenom", getClientPrenom(vente));
        map.put("clientTelephone", getClientTelephone(vente));
        map.put("clientDivers", vente.getClientDivers());
        map.put("montantTotal", vente.getMontantTotal());
        map.put("montantRemiseTotal", vente.getMontantRemiseTotal());
        map.put("montantApresRemise", vente.getMontantApresRemise());
        map.put("beneficeTotal", vente.getBeneficeTotal());
        map.put("remiseGlobale", vente.getRemiseGlobale());
        map.put("typeRemiseGlobale", vente.getTypeRemiseGlobale());
        map.put("modePaiement", vente.getModePaiement().toString());
        map.put("referencePaiement", vente.getReferencePaiement());
        map.put("dateVente", vente.getDateVente());
        map.put("estCredit", vente.getEstCredit());
        map.put("montantVerse", vente.getMontantVerse());
        map.put("montantRestant", vente.getMontantRestant());
        map.put("dateEcheance", vente.getDateEcheance());
        map.put("dateReglement", vente.getDateReglement());
        map.put("creditRegle", vente.getCreditRegle());
        map.put("regleParNom", vente.getRegleParNom());
        map.put("regleParId", vente.getReglePar() != null ? vente.getReglePar().getId() : null);

        // Champs d'annulation et retour
        map.put("annulee", vente.getAnnulee());
        map.put("motifAnnulation", vente.getMotifAnnulation());
        map.put("dateAnnulation", vente.getDateAnnulation());
        map.put("estRetourne", vente.getEstRetourne());
        map.put("retourPartiel", vente.getRetourPartiel());
        map.put("montantRetourne", vente.getMontantRetourne());

        map.put("nombreProduits", vente.getLignes() != null ? vente.getLignes().size() : 0);

        if (vente.getLignes() != null) {
            map.put("lignes", vente.getLignes().stream()
                    .map(this::toLigneMap)
                    .collect(Collectors.toList()));
        }

        if (vente.getVendeur() != null) {
            map.put("vendeur", utilisateurMapper.toVendeurDto(vente.getVendeur()));
        }

        return map;
    }

    public Map<String, Object> toLigneMap(LigneVente ligne) {
        Map<String, Object> map = new HashMap<>();
        map.put("produitId", ligne.getProduitId());
        map.put("produitNom", ligne.getProduitNom());
        map.put("quantite", ligne.getQuantite());
        map.put("modeMesure", ligne.getProduit() != null ? ligne.getProduit().getModeMesure() : null);
        map.put("prixUnitaire", ligne.getPrixUnitaire());
        map.put("prixOriginalProduit", ligne.getPrixOriginalProduit());
        map.put("prixAchat", ligne.getPrixAchat());
        map.put("prixApresRemise", ligne.getPrixApresRemise());
        map.put("sousTotal", ligne.getSousTotal());
        map.put("remisePourcentage", ligne.getRemisePourcentage());
        map.put("remiseMontant", ligne.getRemiseMontant());
        map.put("montantRemise", ligne.getMontantRemise());
        map.put("benefice", ligne.getBenefice());
        return map;
    }

    public List<Map<String, Object>> toVenteMapList(List<Vente> ventes) {
        return ventes.stream()
                .map(this::toVenteMap)
                .collect(Collectors.toList());
    }

    private String getVendeurNom(Vente vente) {
        if (vente.getVendeur() == null) return "Inconnu";
        try {
            return vente.getVendeur().getNomComplet();
        } catch (Exception e) {
            return "Vendeur #" + vente.getVendeurId();
        }
    }

    private String getClientNom(Vente vente) {
        if (vente.getClient() != null) return vente.getClient().getNom();
        return vente.getClientNom();
    }

    private String getClientPrenom(Vente vente) {
        if (vente.getClient() != null) return vente.getClient().getPrenom();
        return vente.getClientPrenom();
    }

    private String getClientTelephone(Vente vente) {
        if (vente.getClient() != null) return vente.getClient().getNumeroTelephone();
        return vente.getClientTelephone();
    }
}