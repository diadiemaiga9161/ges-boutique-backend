package com.ges.boutique.parametres;

import com.ges.boutique.caisse.Caisse;
import com.ges.boutique.exception.MessageErreurUtil;
import com.ges.boutique.caisse.CaisseRepository;
import com.ges.boutique.caisse.OperationCaisse;
import com.ges.boutique.caisse.OperationCaisseRepository;
import com.ges.boutique.caisse.TypeOperationCaisse;
import com.ges.boutique.vente.RetourVenteRepository;
import com.ges.boutique.vente.Vente;
import com.ges.boutique.vente.VenteRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@RestController
@RequestMapping("/api/parametres")
@RequiredArgsConstructor
public class ParametresController {

    private final CaisseRepository caisseRepository;
    private final OperationCaisseRepository operationCaisseRepository;
    private final VenteRepository venteRepository;
    private final RetourVenteRepository retourVenteRepository;

    // ==================== STATUT ====================

    /**
     * GET /api/parametres/statut
     * Retourne les compteurs pour chaque categorie nettoyable.
     */
    @GetMapping("/statut")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, Object>> getStatut() {
        try {
            long nombreOperationsCaisse = operationCaisseRepository.count();

            Double soldeCaisseActuel = 0.0;
            Optional<Caisse> caisseOpt = caisseRepository.findCaisseOuverte();
            if (caisseOpt.isEmpty()) {
                caisseOpt = caisseRepository.findFirstByOrderByIdDesc();
            }
            if (caisseOpt.isPresent()) {
                Double solde = caisseOpt.get().getSoldeActuel();
                soldeCaisseActuel = solde != null ? solde : 0.0;
            }

            Long nombreCreditsRegles = venteRepository.countCreditsRegles();
            Long nombreVentesAnnulees = venteRepository.countVentesAnnulees();

            Map<String, Object> response = new LinkedHashMap<>();
            response.put("success", true);
            response.put("nombreOperationsCaisse", nombreOperationsCaisse);
            response.put("soldeCaisseActuel", soldeCaisseActuel);
            response.put("nombreCreditsRegles", nombreCreditsRegles != null ? nombreCreditsRegles : 0L);
            response.put("nombreVentesAnnulees", nombreVentesAnnulees != null ? nombreVentesAnnulees : 0L);
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Erreur recuperation statut parametres: {}", e.getMessage(), e);
            Map<String, Object> err = new LinkedHashMap<>();
            err.put("success", false);
            err.put("message", "Erreur: " + MessageErreurUtil.messageClient(e));
            return ResponseEntity.internalServerError().body(err);
        }
    }

    // ==================== REINITIALISER ====================

    /**
     * POST /api/parametres/reinitialiser
     * Reinitialise sans supprimer l'historique :
     *   soldeCaisse               -> cree une operation REINITIALISATION, solde remis a 0
     *   historiqueOperationsCaisse -> supprime les operations > 30 jours (sauf credits actifs)
     *   creditsRegles             -> supprime les ventes credit reglees et leurs dependances
     *   historiqueVentesAnnulees  -> supprime les ventes annulees et leurs dependances
     */
    @PostMapping("/reinitialiser")
    @PreAuthorize("hasRole('ADMIN')")
    @Transactional
    public ResponseEntity<Map<String, Object>> reinitialiser(@RequestBody ParametresRequest request) {
        Map<String, Object> details = new LinkedHashMap<>();
        try {
            if (request.isSoldeCaisse()) {
                details.put("soldeCaisse", reinitialiserSoldeCaisse());
            }
            if (request.isHistoriqueOperationsCaisse()) {
                details.put("historiqueOperationsCaisse", nettoyerAnciennesOperations());
            }
            if (request.isCreditsRegles()) {
                details.put("creditsRegles", supprimerVentesParListe(
                        venteRepository.findCreditsRegles(), "credit(s) regle(s)"));
            }
            if (request.isHistoriqueVentesAnnulees()) {
                details.put("historiqueVentesAnnulees", supprimerVentesParListe(
                        venteRepository.findAllVentesAnnulees(), "vente(s) annulee(s)"));
            }

            Map<String, Object> response = new LinkedHashMap<>();
            response.put("success", true);
            response.put("message", "Reinitialisation effectuee avec succes");
            response.put("details", details);
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Erreur reinitialisation parametres: {}", e.getMessage(), e);
            Map<String, Object> response = new LinkedHashMap<>();
            response.put("success", false);
            response.put("message", "Erreur lors de la reinitialisation: " + MessageErreurUtil.messageClient(e));
            response.put("details", details);
            return ResponseEntity.internalServerError().body(response);
        }
    }

    // ==================== SUPPRIMER ====================

    /**
     * DELETE /api/parametres/supprimer
     * Suppression permanente :
     *   soldeCaisse               -> supprime TOUTES les operations + remet les soldes a 0
     *   historiqueOperationsCaisse -> supprime TOUTES les operations caisse
     *   creditsRegles             -> supprime definitivement tous les credits regles
     *   historiqueVentesAnnulees  -> supprime definitivement toutes les ventes annulees
     */
    @DeleteMapping("/supprimer")
    @PreAuthorize("hasRole('ADMIN')")
    @Transactional
    public ResponseEntity<Map<String, Object>> supprimer(
            @RequestBody(required = false) ParametresRequest request) {

        if (request == null) {
            Map<String, Object> err = new LinkedHashMap<>();
            err.put("success", false);
            err.put("message", "Body de la requete manquant");
            return ResponseEntity.badRequest().body(err);
        }

        Map<String, Object> details = new LinkedHashMap<>();
        try {
            // Credits et ventes supprimes en premier (avant deleteAllInBatch sur les ops caisse)
            if (request.isCreditsRegles()) {
                details.put("creditsRegles", supprimerVentesParListe(
                        venteRepository.findCreditsRegles(), "credit(s) regle(s)"));
            }
            if (request.isHistoriqueVentesAnnulees()) {
                details.put("historiqueVentesAnnulees", supprimerVentesParListe(
                        venteRepository.findAllVentesAnnulees(), "vente(s) annulee(s)"));
            }

            // Suppression des operations (soldeCaisse OU historiqueOperationsCaisse)
            boolean supprimerToutesOps = request.isSoldeCaisse() || request.isHistoriqueOperationsCaisse();
            if (supprimerToutesOps) {
                details.put("operationsCaisse",
                        supprimerToutesOperations(request.isSoldeCaisse()));
            }

            Map<String, Object> response = new LinkedHashMap<>();
            response.put("success", true);
            response.put("message", "Suppression definitive effectuee avec succes");
            response.put("details", details);
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Erreur suppression parametres: {}", e.getMessage(), e);
            Map<String, Object> response = new LinkedHashMap<>();
            response.put("success", false);
            response.put("message", "Erreur lors de la suppression: " + MessageErreurUtil.messageClient(e));
            response.put("details", details);
            return ResponseEntity.internalServerError().body(response);
        }
    }

    // ==================== METHODES PRIVEES ====================

    /**
     * Cree une operation REINITIALISATION qui ramene le solde de la caisse a 0.
     * Ne supprime aucune operation existante.
     */
    private String reinitialiserSoldeCaisse() {
        Optional<Caisse> caisseOpt = caisseRepository.findCaisseOuverte();
        if (caisseOpt.isEmpty()) {
            caisseOpt = caisseRepository.findFirstByOrderByIdDesc();
        }
        if (caisseOpt.isEmpty()) {
            log.warn("Reinitialisation solde: aucune caisse trouvee");
            return "Aucune caisse trouvee";
        }

        Caisse caisse = caisseOpt.get();
        double soldeActuel = caisse.getSoldeActuel() != null ? caisse.getSoldeActuel() : 0.0;

        if (soldeActuel == 0.0) {
            return "Solde deja a zero, aucune operation creee";
        }

        OperationCaisse op = new OperationCaisse();
        op.setCaisse(caisse);
        op.setType(TypeOperationCaisse.REINITIALISATION);
        op.setMontant(-soldeActuel);
        op.setSoldeAvant(soldeActuel);
        op.setSoldeApres(0.0);
        op.setMotif("Reinitialisation manuelle du solde caisse via Parametres");
        op.setEstReglee(true);
        op.setDateOperation(LocalDateTime.now());
        operationCaisseRepository.save(op);

        caisse.setSoldeActuel(0.0);
        caisse.setSoldeSysteme(0.0);
        caisseRepository.save(caisse);

        log.info("Solde caisse reinitialise: {} -> 0.0", soldeActuel);
        return String.format("Solde reinitialise (%.2f -> 0,00), operation REINITIALISATION creee", soldeActuel);
    }

    /**
     * Supprime les operations caisse de plus de 30 jours,
     * en conservant les VENTE_CREDIT non encore reglees (credits actifs).
     */
    private String nettoyerAnciennesOperations() {
        LocalDateTime limite = LocalDateTime.now().minusDays(30);
        int supprimees = operationCaisseRepository.deleteOldOperationsExceptActiveCredits(limite);
        log.info("Nettoyage historique caisse: {} operation(s) supprimee(s) anterieures a {}", supprimees, limite);
        return supprimees + " operation(s) supprimee(s) (> 30 jours, credits actifs conserves)";
    }

    /**
     * Supprime une liste de ventes en respectant l'ordre des contraintes FK :
     *   1. lignes_retour_vente (FK -> retours_vente)
     *   2. retours_vente       (FK -> ventes, NOT NULL)
     *   3. operations_caisse   (FK -> ventes, nullable -> mis a NULL)
     *   4. lignes_vente        (FK -> ventes)
     *   5. ventes
     */
    private String supprimerVentesParListe(List<Vente> ventes, String libelle) {
        if (ventes == null || ventes.isEmpty()) {
            return "Aucun(e) " + libelle + " a supprimer";
        }

        List<Long> ids = ventes.stream()
                .map(Vente::getId)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());

        if (ids.isEmpty()) {
            return "Aucun identifiant valide trouve";
        }

        log.info("Suppression de {} {} (ids: {})", ids.size(), libelle, ids);

        // 1. Lignes des retours (FK -> retours_vente)
        retourVenteRepository.deleteRetourLignesByVenteIds(ids);

        // 2. Retours de vente (FK -> ventes, NOT NULL)
        retourVenteRepository.deleteRetoursByVenteIds(ids);

        // 3. Operations caisse : vente_id mis a NULL (colonne nullable)
        operationCaisseRepository.nullOutVenteReferences(ids);

        // 4. Lignes de vente
        venteRepository.deleteLignesByVenteIds(ids);

        // 5. Ventes
        venteRepository.deleteVentesByIds(ids);

        return ids.size() + " " + libelle + " supprime(s) definitivement";
    }

    /**
     * Supprime TOUTES les operations caisse en une seule passe.
     * Si reinitialiserSolde=true, remet egalement tous les soldes a 0.
     */
    private String supprimerToutesOperations(boolean reinitialiserSolde) {
        long nbAvant = operationCaisseRepository.count();
        operationCaisseRepository.deleteAllInBatch();

        if (reinitialiserSolde) {
            List<Caisse> caisses = caisseRepository.findAll();
            for (Caisse caisse : caisses) {
                caisse.setSoldeActuel(0.0);
                caisse.setSoldeSysteme(0.0);
                caisse.setSoldeReel(0.0);
                caisse.setTotalEntrees(0.0);
                caisse.setTotalSorties(0.0);
                caisse.setNombreOperations(0);
            }
            caisseRepository.saveAll(caisses);
            log.info("Toutes operations supprimees ({}) + {} caisse(s) remise(s) a zero", nbAvant, caisses.size());
            return nbAvant + " operation(s) supprimee(s), " + caisses.size() + " caisse(s) remise(s) a zero";
        }

        log.info("Toutes operations caisse supprimees: {} operation(s)", nbAvant);
        return nbAvant + " operation(s) supprimee(s)";
    }
}
