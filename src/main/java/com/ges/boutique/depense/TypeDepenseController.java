package com.ges.boutique.depense;

import com.ges.boutique.exception.MessageErreurUtil;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/types-depense")
public class TypeDepenseController {

    private final TypeDepenseService typeDepenseService;

    public TypeDepenseController(TypeDepenseService typeDepenseService) {
        this.typeDepenseService = typeDepenseService;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'VENDEUR')")
    public ResponseEntity<List<TypeDepense>> getAll() {
        return ResponseEntity.ok(typeDepenseService.getAll());
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'VENDEUR')")
    public ResponseEntity<?> creer(@RequestBody Map<String, String> body) {
        try {
            String nom = body.get("nom");
            if (nom == null || nom.isBlank()) {
                return ResponseEntity.badRequest().body("Le champ 'nom' est obligatoire");
            }
            return ResponseEntity.ok(typeDepenseService.creer(nom));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(MessageErreurUtil.messageClient(e));
        }
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'VENDEUR')")
    public ResponseEntity<?> modifier(@PathVariable Long id, @RequestBody Map<String, String> body) {
        try {
            String nom = body.get("nom");
            if (nom == null || nom.isBlank()) {
                return ResponseEntity.badRequest().body("Le champ 'nom' est obligatoire");
            }
            return ResponseEntity.ok(typeDepenseService.modifier(id, nom));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(MessageErreurUtil.messageClient(e));
        }
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> supprimer(@PathVariable Long id) {
        try {
            typeDepenseService.supprimer(id);
            return ResponseEntity.ok().build();
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(MessageErreurUtil.messageClient(e));
        }
    }
}
