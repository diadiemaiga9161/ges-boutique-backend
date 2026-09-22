package com.ges.boutique.ia;

import com.ges.boutique.exception.MessageErreurUtil;
import com.ges.boutique.feature.CleFonctionnalite;
import com.ges.boutique.feature.RequireFeature;
import com.ges.boutique.ia.dto.AnalyseIAResult;
import com.ges.boutique.ia.dto.RecommandationIA;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/ia")
@RequiredArgsConstructor
@Slf4j
@RequireFeature(CleFonctionnalite.IA)
public class IAController {

    private final AnalyseIAService analyseService;
    private final ProfilIAService profilService;
    private final FeedbackRecommandationRepository feedbackRepository;
    private final PrevisionService previsionService;

    // -------------------------------------------------------
    // Profil IA
    // -------------------------------------------------------

    @GetMapping("/profil")
    public ResponseEntity<?> obtenirProfil() {
        Optional<ProfilIA> profil = profilService.obtenirProfil();
        if (profil.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(profil.get());
    }

    @PostMapping("/profil")
    public ResponseEntity<ProfilIA> sauvegarderProfil(@RequestBody ProfilIA profil) {
        ProfilIA saved = profilService.sauvegarder(profil);
        return ResponseEntity.ok(saved);
    }

    // -------------------------------------------------------
    // Analyse complète
    // -------------------------------------------------------

    @GetMapping("/analyse")
    public ResponseEntity<?> analyser() {
        Optional<ProfilIA> profil = profilService.obtenirProfil();
        if (profil.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of(
                    "erreur", "Profil IA non configuré",
                    "action", "Configurez le profil via POST /api/ia/profil avant de lancer l'analyse."
            ));
        }
        try {
            AnalyseIAResult result = analyseService.analyser(profil.get());
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("Erreur lors de l'analyse IA", e);
            return ResponseEntity.internalServerError().body(Map.of("erreur", MessageErreurUtil.messageClient(e)));
        }
    }

    // -------------------------------------------------------
    // Recommandations uniquement (appel léger)
    // -------------------------------------------------------

    @GetMapping("/recommandations")
    public ResponseEntity<?> getRecommandations() {
        Optional<ProfilIA> profil = profilService.obtenirProfil();
        if (profil.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of(
                    "erreur", "Profil IA non configuré"
            ));
        }
        try {
            AnalyseIAResult result = analyseService.analyser(profil.get());
            List<RecommandationIA> recs = result.getRecommandations();
            return ResponseEntity.ok(recs);
        } catch (Exception e) {
            log.error("Erreur lors de la génération des recommandations", e);
            return ResponseEntity.internalServerError().body(Map.of("erreur", MessageErreurUtil.messageClient(e)));
        }
    }

    // -------------------------------------------------------
    // Score santé (appel ultra-léger, pour le dashboard)
    // -------------------------------------------------------

    @GetMapping("/sante")
    public ResponseEntity<Map<String, Object>> getScoreSante() {
        Optional<ProfilIA> profil = profilService.obtenirProfil();
        if (profil.isEmpty()) {
            return ResponseEntity.ok(Map.of(
                    "scoreGlobal", 0,
                    "tendanceCA",  "STABLE",
                    "configure",   false
            ));
        }
        try {
            AnalyseIAResult result = analyseService.analyser(profil.get());
            Map<String, Object> sante = new HashMap<>();
            sante.put("scoreGlobal",   result.getScoreGlobal());
            sante.put("tendanceCA",    result.getTendanceCA());
            sante.put("tauxCroissance", result.getTauxCroissanceMensuel());
            sante.put("precisionModele", result.getPrecisionModele());
            sante.put("configure",     true);
            return ResponseEntity.ok(sante);
        } catch (Exception e) {
            log.error("Erreur score santé IA", e);
            return ResponseEntity.ok(Map.of(
                    "scoreGlobal", 0,
                    "tendanceCA",  "STABLE",
                    "erreur",      MessageErreurUtil.messageClient(e)
            ));
        }
    }

    // -------------------------------------------------------
    // Prévisions de stock (rupture, vélocité, réappro)
    // -------------------------------------------------------

    @GetMapping("/previsions")
    public ResponseEntity<List<Map<String, Object>>> getPrevisions() {
        try {
            List<Map<String, Object>> previsions = previsionService.genererPrevisions();
            return ResponseEntity.ok(previsions);
        } catch (Exception e) {
            log.error("Erreur lors de la génération des prévisions", e);
            return ResponseEntity.internalServerError().build();
        }
    }

    // -------------------------------------------------------
    // Feedback sur une recommandation
    // -------------------------------------------------------

    /**
     * Enregistre le feedback (SUIVIE ou IGNOREE) pour une recommandation identifiée par son UUID.
     * Body attendu : { "statut": "SUIVIE", "type": "REAPPRO" }
     *   - statut : obligatoire
     *   - type   : optionnel (défaut "STOCK")
     */
    @PostMapping("/feedback/{id}")
    @Transactional
    public ResponseEntity<?> enregistrerFeedback(
            @PathVariable String id,
            @RequestBody Map<String, String> body) {

        String statutStr = body.get("statut");
        if (statutStr == null) {
            return ResponseEntity.badRequest().body(Map.of("erreur", "Champ 'statut' obligatoire (SUIVIE | IGNOREE)"));
        }

        StatutFeedback statut;
        try {
            statut = StatutFeedback.valueOf(statutStr.toUpperCase());
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("erreur", "Valeur 'statut' invalide. Attendu: SUIVIE | IGNOREE"));
        }

        String type = body.getOrDefault("type", "STOCK").toUpperCase();

        // Trouver ou créer le FeedbackRecommandation référencé par cet UUID
        FeedbackRecommandation feedback = feedbackRepository.findByReferenceId(id)
                .orElseGet(() -> {
                    FeedbackRecommandation fb = new FeedbackRecommandation();
                    fb.setReferenceId(id);
                    fb.setTypeRecommandation(type);
                    fb.setDateCreation(LocalDateTime.now());
                    return fb;
                });

        feedback.setStatut(statut);
        feedback.setDateRetour(LocalDateTime.now());
        feedbackRepository.save(feedback);

        // Auto-apprentissage : recalibrer le modèle
        try {
            analyseService.mettreAJourModele();
        } catch (Exception e) {
            log.warn("Erreur lors de la mise à jour du modèle IA: {}", e.getMessage());
        }

        return ResponseEntity.ok(Map.of(
                "message",  "Feedback enregistré avec succès",
                "referenceId", id,
                "statut",   statut.name(),
                "type",     type
        ));
    }
}
