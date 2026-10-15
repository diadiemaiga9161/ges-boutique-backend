package com.ges.boutique.employe;

import com.ges.boutique.securite.UtilisateurConnecte;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/paiements-employe")
@RequiredArgsConstructor
public class PaiementEmployeController {

    private final PaiementEmployeService paiementService;

    @PostMapping
    public ResponseEntity<PaiementEmployeDto> payer(@RequestBody PaiementEmployeRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(paiementService.payerEmploye(request));
    }

    @PatchMapping("/{id}/annuler")
    public ResponseEntity<PaiementEmployeDto> annuler(
            @PathVariable Long id,
            @RequestParam(required = false) String motif,
            @RequestParam(required = false) Long utilisateurId) {
        return ResponseEntity.ok(paiementService.annulerPaiement(id, motif, UtilisateurConnecte.idOu(utilisateurId)));
    }

    @GetMapping("/{id}")
    public ResponseEntity<PaiementEmployeDto> getById(@PathVariable Long id) {
        return ResponseEntity.ok(paiementService.getPaiementById(id));
    }

    @GetMapping
    public ResponseEntity<List<PaiementEmployeDto>> getTous() {
        return ResponseEntity.ok(paiementService.getTousLesPaiements());
    }

    @GetMapping("/employe/{employeId}")
    public ResponseEntity<List<PaiementEmployeDto>> getParEmploye(@PathVariable Long employeId) {
        return ResponseEntity.ok(paiementService.getPaiementsParEmploye(employeId));
    }

    @GetMapping("/actifs")
    public ResponseEntity<List<PaiementEmployeDto>> getActifs() {
        return ResponseEntity.ok(paiementService.getPaiementsActifs());
    }

    @GetMapping("/statistiques")
    public ResponseEntity<Map<String, Object>> getStatistiques() {
        return ResponseEntity.ok(paiementService.getStatistiques());
    }
}
