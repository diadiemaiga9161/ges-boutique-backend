package com.ges.boutique.push;

import com.ges.boutique.utilisateur.Utilisateur;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/** Abonnement d'un téléphone aux notifications (tout utilisateur connecté : gérant ou vendeur). */
@RestController
@RequestMapping("/api/push")
@RequiredArgsConstructor
public class PushController {

    private final PushService pushService;

    @GetMapping("/cle-publique")
    public Map<String, String> clePublique() {
        return Map.of("clePublique", pushService.clePublique());
    }

    @PostMapping("/abonnement")
    public ResponseEntity<Map<String, Object>> abonner(@RequestBody Map<String, Object> corps,
                                                       @RequestHeader(value = "User-Agent", required = false) String appareil) {
        String endpoint = (String) corps.get("endpoint");
        Object cles = corps.get("keys");
        String p256dh = cles instanceof Map<?, ?> m ? (String) m.get("p256dh") : null;
        String auth = cles instanceof Map<?, ?> m ? (String) m.get("auth") : null;
        pushService.abonner(endpoint, p256dh, auth, utilisateurConnecte(), appareil);
        return ResponseEntity.ok(Map.of("success", true));
    }

    @PostMapping("/desabonnement")
    public ResponseEntity<Map<String, Object>> desabonner(@RequestBody Map<String, Object> corps) {
        pushService.desabonner((String) corps.get("endpoint"));
        return ResponseEntity.ok(Map.of("success", true));
    }

    private Long utilisateurConnecte() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null && auth.getPrincipal() instanceof Utilisateur u ? u.getId() : null;
    }
}
