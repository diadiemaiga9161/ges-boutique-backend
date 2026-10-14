package com.ges.boutique.vente;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/retours-ventes")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class RetourVenteController {

    private final RetourVenteServiceImpl retourVenteService;  // Directement le service impl

    @PostMapping
    public ResponseEntity<Map<String, Object>> effectuerRetour(@RequestBody RetourVenteRequest request) {
        log.info("=== REQUETE RETOUR RECUE ===");
        log.info("Vente ID: {}", request.getVenteId());

        RetourVente retour = retourVenteService.effectuerRetour(request);

        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("message", "Retour effectué avec succès");
        response.put("retour", retour);  // Retourne l'entité directement
        return ResponseEntity.ok(response);
    }

    @PostMapping("/apercu")
    public ResponseEntity<Map<String, Object>> apercu(@RequestBody RetourVenteRequest request) {
        return ResponseEntity.ok(retourVenteService.apercu(request));
    }

    @GetMapping("/vente/{venteId}")
    public ResponseEntity<Map<String, Object>> getRetoursByVente(@PathVariable Long venteId) {
        List<RetourVente> retours = retourVenteService.getRetoursByVente(venteId);
        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("retours", retours);
        return ResponseEntity.ok(response);
    }

    @GetMapping
    public ResponseEntity<Map<String, Object>> getAllRetours() {
        List<RetourVente> retours = retourVenteService.getAllRetours();
        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("retours", retours);
        return ResponseEntity.ok(response);
    }
}