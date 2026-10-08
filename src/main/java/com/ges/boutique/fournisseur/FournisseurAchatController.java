package com.ges.boutique.fournisseur;

import com.ges.boutique.produit.Produit;
import com.ges.boutique.produit.ProduitRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@RestController
@RequestMapping("/api/fournisseur-achats")
@RequiredArgsConstructor
@Tag(name = "Fournisseur - Achats & Paiements")
public class FournisseurAchatController {

    private final FournisseurComptableService fournisseurComptableService;
    private final ProduitRepository produitRepository;
    private final AchatFournisseurRepository achatFournisseurRepository;

    @PostMapping("/achat")
    @PreAuthorize("hasAnyRole('ADMIN', 'STOCK')")
    @Operation(summary = "Enregistrer un achat chez un fournisseur (avec mise à jour du stock et de la dette)")
    public ResponseEntity<Map<String, Object>> creerAchat(@RequestBody AchatFournisseurRequest request) {
        AchatFournisseur achat = fournisseurComptableService.creerAchat(request);

        // Construire une réponse simplifiée sans références circulaires
        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("message", "Achat enregistré, stock mis à jour");

        Map<String, Object> achatData = new HashMap<>();
        achatData.put("id", achat.getId());
        achatData.put("dateAchat", achat.getDateAchat());
        achatData.put("montantTotal", achat.getMontantTotal());
        achatData.put("montantPaye", achat.getMontantPaye());
        achatData.put("montantRestant", achat.getMontantRestant());
        achatData.put("statut", achat.getStatut().toString());
        achatData.put("commentaire", achat.getCommentaire());
        achatData.put("dateCreation", achat.getDateCreation());

        // Informations fournisseur simplifiées
        if (achat.getFournisseur() != null) {
            Map<String, Object> fournisseurData = new HashMap<>();
            fournisseurData.put("id", achat.getFournisseur().getId());
            fournisseurData.put("nom", achat.getFournisseur().getNom());
            fournisseurData.put("code", achat.getFournisseur().getCode());
            achatData.put("fournisseur", fournisseurData);
        }

        // Lignes d'achat simplifiées
        List<Map<String, Object>> lignesData = achat.getLignes().stream()
                .map(ligne -> {
                    Map<String, Object> ligneMap = new HashMap<>();
                    ligneMap.put("id", ligne.getId());
                    ligneMap.put("quantite", ligne.getQuantite());
                    ligneMap.put("modeMesure", ligne.getProduit() != null ? ligne.getProduit().getModeMesure() : null);
                    ligneMap.put("prixAchatUnitaire", ligne.getPrixAchatUnitaire());
                    ligneMap.put("sousTotal", ligne.getSousTotal());

                    if (ligne.getProduit() != null) {
                        Map<String, Object> produitData = new HashMap<>();
                        produitData.put("id", ligne.getProduit().getId());
                        produitData.put("nom", ligne.getProduit().getNom());
                        produitData.put("codeBarre", ligne.getProduit().getCodeBarre());
                        produitData.put("uniteMesure", ligne.getProduit().getUniteMesure());
                        ligneMap.put("produit", produitData);
                    }
                    return ligneMap;
                })
                .collect(Collectors.toList());

        achatData.put("lignes", lignesData);
        response.put("achat", achatData);

        return ResponseEntity.ok(response);
    }

    @PostMapping("/paiement")
    @PreAuthorize("hasAnyRole('ADMIN', 'CAISSE')")
    @Operation(summary = "Effectuer un paiement à un fournisseur (espèces = sortie de caisse, virement/chèque = hors caisse)")
    public ResponseEntity<Map<String, Object>> payerFournisseur(@RequestBody PaiementFournisseurRequest request) {
        PaiementFournisseur paiement = fournisseurComptableService.payerFournisseur(request);

        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("message", "Paiement enregistré");

        Map<String, Object> paiementData = new HashMap<>();
        paiementData.put("id", paiement.getId());
        paiementData.put("datePaiement", paiement.getDatePaiement());
        paiementData.put("montant", paiement.getMontant());
        paiementData.put("modePaiement", paiement.getModePaiement().toString());
        paiementData.put("reference", paiement.getReference());
        paiementData.put("observation", paiement.getObservation());

        if (paiement.getFournisseur() != null) {
            Map<String, Object> fournisseurData = new HashMap<>();
            fournisseurData.put("id", paiement.getFournisseur().getId());
            fournisseurData.put("nom", paiement.getFournisseur().getNom());
            paiementData.put("fournisseur", fournisseurData);
        }

        response.put("paiement", paiementData);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/situation/{fournisseurId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'STOCK', 'CAISSE')")
    @Operation(summary = "Obtenir la situation comptable d'un fournisseur (total achats, payé, solde, historiques). " +
            "Filtre optionnel par période (dateDebut/dateFin au format yyyy-MM-dd) appliqué uniquement aux listes achatsRecents/paiementsRecents, pas aux totaux.")
    public ResponseEntity<FournisseurCompteDto> getSituationFournisseur(
            @PathVariable Long fournisseurId,
            @RequestParam(required = false) String dateDebut,
            @RequestParam(required = false) String dateFin) {
        FournisseurCompteDto situation = fournisseurComptableService.getSituationFournisseur(fournisseurId, dateDebut, dateFin);
        return ResponseEntity.ok(situation);
    }

    @GetMapping("/achats/{fournisseurId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'STOCK')")
    @Transactional(readOnly = true)
    @Operation(summary = "Historique des achats d'un fournisseur avec lignes produits. Filtre optionnel par période (dateDebut/dateFin au format yyyy-MM-dd)")
    public ResponseEntity<Map<String, Object>> getHistoriqueAchats(
            @PathVariable Long fournisseurId,
            @RequestParam(required = false) String dateDebut,
            @RequestParam(required = false) String dateFin) {
        List<AchatFournisseur> achats = fournisseurComptableService.getHistoriqueAchats(fournisseurId, dateDebut, dateFin);

        List<Map<String, Object>> achatsData = achats.stream()
                .map(achat -> {
                    Map<String, Object> achatMap = new HashMap<>();
                    achatMap.put("id", achat.getId());
                    achatMap.put("dateAchat", achat.getDateAchat());
                    achatMap.put("montantTotal", achat.getMontantTotal());
                    achatMap.put("montantPaye", achat.getMontantPaye());
                    achatMap.put("montantRestant", achat.getMontantRestant());
                    achatMap.put("statut", achat.getStatut().toString());
                    achatMap.put("commentaire", achat.getCommentaire());

                    List<Map<String, Object>> lignesData = achat.getLignes().stream()
                            .map(ligne -> {
                                Map<String, Object> ligneMap = new HashMap<>();
                                ligneMap.put("quantite", ligne.getQuantite());
                                ligneMap.put("modeMesure", ligne.getProduit() != null ? ligne.getProduit().getModeMesure() : null);
                                ligneMap.put("prixAchatUnitaire", ligne.getPrixAchatUnitaire());
                                ligneMap.put("sousTotal", ligne.getSousTotal());
                                if (ligne.getProduit() != null) {
                                    Map<String, Object> p = new HashMap<>();
                                    p.put("id", ligne.getProduit().getId());
                                    p.put("nom", ligne.getProduit().getNom());
                                    ligneMap.put("produit", p);
                                }
                                return ligneMap;
                            })
                            .collect(Collectors.toList());
                    achatMap.put("lignes", lignesData);

                    return achatMap;
                })
                .collect(Collectors.toList());

        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("achats", achatsData);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/achats-non-payes/{fournisseurId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'STOCK')")
    @Transactional(readOnly = true)
    @Operation(summary = "Liste des achats non payés (statut EN_COURS) d'un fournisseur avec lignes produits")
    public ResponseEntity<Map<String, Object>> getAchatsNonPayes(@PathVariable Long fournisseurId) {
        List<AchatFournisseur> achats = fournisseurComptableService.getAchatsNonPayes(fournisseurId);

        List<Map<String, Object>> achatsData = achats.stream()
                .map(achat -> {
                    Map<String, Object> achatMap = new HashMap<>();
                    achatMap.put("id", achat.getId());
                    achatMap.put("dateAchat", achat.getDateAchat());
                    achatMap.put("montantTotal", achat.getMontantTotal());
                    achatMap.put("montantPaye", achat.getMontantPaye());
                    achatMap.put("montantRestant", achat.getMontantRestant());
                    achatMap.put("statut", achat.getStatut().toString());
                    achatMap.put("commentaire", achat.getCommentaire());

                    List<Map<String, Object>> lignesData = achat.getLignes().stream()
                            .map(ligne -> {
                                Map<String, Object> ligneMap = new HashMap<>();
                                ligneMap.put("quantite", ligne.getQuantite());
                                ligneMap.put("modeMesure", ligne.getProduit() != null ? ligne.getProduit().getModeMesure() : null);
                                ligneMap.put("prixAchatUnitaire", ligne.getPrixAchatUnitaire());
                                ligneMap.put("sousTotal", ligne.getSousTotal());
                                if (ligne.getProduit() != null) {
                                    Map<String, Object> p = new HashMap<>();
                                    p.put("id", ligne.getProduit().getId());
                                    p.put("nom", ligne.getProduit().getNom());
                                    ligneMap.put("produit", p);
                                }
                                return ligneMap;
                            })
                            .collect(Collectors.toList());
                    achatMap.put("lignes", lignesData);

                    return achatMap;
                })
                .collect(Collectors.toList());

        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("achats", achatsData);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/paiements/{fournisseurId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'CAISSE')")
    @Operation(summary = "Historique des paiements effectués à un fournisseur. Filtre optionnel par période (dateDebut/dateFin au format yyyy-MM-dd)")
    public ResponseEntity<Map<String, Object>> getHistoriquePaiements(
            @PathVariable Long fournisseurId,
            @RequestParam(required = false) String dateDebut,
            @RequestParam(required = false) String dateFin) {
        List<PaiementFournisseur> paiements = fournisseurComptableService.getHistoriquePaiements(fournisseurId, dateDebut, dateFin);

        // Construire une réponse simplifiée
        List<Map<String, Object>> paiementsData = paiements.stream()
                .map(paiement -> {
                    Map<String, Object> paiementMap = new HashMap<>();
                    paiementMap.put("id", paiement.getId());
                    paiementMap.put("datePaiement", paiement.getDatePaiement());
                    paiementMap.put("montant", paiement.getMontant());
                    paiementMap.put("modePaiement", paiement.getModePaiement().toString());
                    paiementMap.put("reference", paiement.getReference());
                    paiementMap.put("observation", paiement.getObservation());
                    return paiementMap;
                })
                .collect(Collectors.toList());

        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("paiements", paiementsData);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/achat/{achatId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'STOCK')")
    @Transactional(readOnly = true)
    @Operation(summary = "Détails d'un achat avec ses lignes (produits, quantités, prix)")
    public ResponseEntity<Map<String, Object>> getAchatById(@PathVariable Long achatId) {
        AchatFournisseur achat = achatFournisseurRepository.findById(achatId)
                .orElseThrow(() -> new RuntimeException("Achat non trouvé: " + achatId));

        Map<String, Object> achatData = new HashMap<>();
        achatData.put("id", achat.getId());
        achatData.put("dateAchat", achat.getDateAchat());
        achatData.put("montantTotal", achat.getMontantTotal());
        achatData.put("montantPaye", achat.getMontantPaye());
        achatData.put("montantRestant", achat.getMontantRestant());
        achatData.put("statut", achat.getStatut().toString());
        achatData.put("commentaire", achat.getCommentaire());

        if (achat.getFournisseur() != null) {
            Map<String, Object> f = new HashMap<>();
            f.put("id", achat.getFournisseur().getId());
            f.put("nom", achat.getFournisseur().getNom());
            achatData.put("fournisseur", f);
        }

        List<Map<String, Object>> lignesData = achat.getLignes().stream()
                .map(ligne -> {
                    Map<String, Object> ligneMap = new HashMap<>();
                    ligneMap.put("id", ligne.getId());
                    ligneMap.put("quantite", ligne.getQuantite());
                    ligneMap.put("modeMesure", ligne.getProduit() != null ? ligne.getProduit().getModeMesure() : null);
                    ligneMap.put("prixAchatUnitaire", ligne.getPrixAchatUnitaire());
                    ligneMap.put("sousTotal", ligne.getSousTotal());
                    if (ligne.getProduit() != null) {
                        Map<String, Object> p = new HashMap<>();
                        p.put("id", ligne.getProduit().getId());
                        p.put("nom", ligne.getProduit().getNom());
                        ligneMap.put("produit", p);
                    }
                    return ligneMap;
                })
                .collect(Collectors.toList());
        achatData.put("lignes", lignesData);

        return ResponseEntity.ok(achatData);
    }

    @PostMapping("/achat/{achatId}/annuler")
    @PreAuthorize("hasAnyRole('ADMIN', 'STOCK')")
    @Operation(summary = "Annuler un achat fournisseur (retire le stock ajouté et corrige le solde fournisseur)")
    public ResponseEntity<Map<String, Object>> annulerAchat(
            @PathVariable Long achatId,
            @RequestParam(required = false) Long utilisateurId) {
        AchatFournisseur achat = fournisseurComptableService.annulerAchat(achatId, utilisateurId);
        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("message", "Achat annulé, stock corrigé");
        response.put("achatId", achat.getId());
        response.put("statut", achat.getStatut().toString());
        return ResponseEntity.ok(response);
    }

    @GetMapping("/paiements")
    @PreAuthorize("hasAnyRole('ROLE_ADMIN', 'ROLE_CAISSE')")
    @Operation(summary = "Lister tous les paiements fournisseur (optionnel: filtré par période)")
    public ResponseEntity<?> getPaiementsParPeriode(
            @RequestParam(required = false) String dateDebut,
            @RequestParam(required = false) String dateFin) {
        return ResponseEntity.ok(fournisseurComptableService.getPaiementsParPeriode(dateDebut, dateFin));
    }

    @PostMapping("/paiement/{paiementId}/annuler")
    @PreAuthorize("hasAnyRole('ROLE_ADMIN', 'ROLE_CAISSE')")
    @Operation(summary = "Annuler un paiement fournisseur (restitue le montant en caisse ou sur le compte)")
    public ResponseEntity<?> annulerPaiement(
            @PathVariable Long paiementId,
            @RequestParam Long utilisateurId) {
        return ResponseEntity.ok(fournisseurComptableService.annulerPaiementFournisseur(paiementId, utilisateurId));
    }

    @GetMapping("/produits-simples")
    @PreAuthorize("hasAnyRole('ADMIN', 'STOCK')")
    @Operation(summary = "Récupérer la liste simplifiée des produits (id, nom, codeBarre, uniteMesure, prixAchat)")
    public ResponseEntity<List<Map<String, Object>>> getProduitsSimples() {
        List<Produit> produits = produitRepository.findAll();
        List<Map<String, Object>> result = produits.stream()
                .map(p -> {
                    Map<String, Object> map = new HashMap<>();
                    map.put("id", p.getId());
                    map.put("nom", p.getNom());
                    map.put("codeBarre", p.getCodeBarre());
                    map.put("uniteMesure", p.getUniteMesure());
                    map.put("prixAchat", p.getPrixAchat());
                    map.put("prixVente", p.getPrixVente());
                    map.put("quantite", p.getQuantite());
                    map.put("modeMesure", p.getModeMesure());
                    return map;
                })
                .collect(Collectors.toList());
        return ResponseEntity.ok(result);
    }
}