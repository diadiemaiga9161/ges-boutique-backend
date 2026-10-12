package com.ges.boutique.push;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ges.boutique.utilisateur.UtilisateurRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.interfaces.ECPrivateKey;
import java.security.interfaces.ECPublicKey;
import java.security.KeyPair;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Notifications affichées sur l'écran du téléphone, même application fermée (iPhone avec
 * l'appli sur l'écran d'accueil, Android avec l'appli installée depuis Chrome).
 *
 * Envoi après l'enregistrement en base (rien ne part si l'opération est annulée) et dans un fil
 * à part : une vente n'attend jamais Apple ou Google. Un échec d'envoi est noté dans le journal
 * sans rien bloquer ; un téléphone qui s'est désabonné (réponse 404/410) est oublié.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PushService {

    private static final String CONTACT = "mailto:contact@mg-consulting.site";
    private static final String ICONE = "/mobile/assets/icons/icon-192x192.png";
    private static final String BADGE = "/mobile/assets/icons/icon-72x72.png";

    private final PushAbonnementRepository abonnementRepository;
    private final PushCleVapidRepository cleRepository;
    private final ObjectMapper objectMapper;
    private final UtilisateurRepository utilisateurRepository;

    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    private final ExecutorService envois = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "notifications-push");
        t.setDaemon(true);
        return t;
    });

    private volatile PushCleVapid cle;

    /** Clé publique du serveur, demandée par le téléphone pour s'abonner (créée au premier appel). */
    public synchronized String clePublique() {
        return cle().getClePublique();
    }

    public void abonner(String endpoint, String p256dh, String auth, Long utilisateurId, String appareil) {
        if (!adresseAutorisee(endpoint) || p256dh == null || auth == null) {
            throw new IllegalArgumentException("Abonnement aux notifications invalide");
        }
        PushAbonnement a = abonnementRepository.findByEndpoint(endpoint).orElseGet(PushAbonnement::new);
        a.setEndpoint(endpoint);
        a.setP256dh(p256dh);
        a.setAuth(auth);
        a.setUtilisateurId(utilisateurId);
        if (appareil != null) a.setAppareil(appareil.length() > 250 ? appareil.substring(0, 250) : appareil);
        abonnementRepository.save(a);
    }

    public void desabonner(String endpoint) {
        if (endpoint != null) abonnementRepository.deleteByEndpoint(endpoint);
    }

    /** Envoie à tous les téléphones abonnés (gérants et vendeurs) ; url = page ouverte au toucher. */
    public void envoyerATous(String titre, String message, String url) {
        Runnable envoi = () -> envois.submit(() -> envoyerMaintenant(titre, message, url));
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    envoi.run();
                }
            });
        } else {
            envoi.run();
        }
    }

    private void envoyerMaintenant(String titre, String message, String url) {
        try {
            List<PushAbonnement> abonnes = abonnementRepository.findAll();
            if (abonnes.isEmpty()) return;
            byte[] contenu = contenu(titre, message, url);
            for (PushAbonnement a : abonnes) {
                // Compte supprimé ou désactivé : ce téléphone ne doit plus recevoir les alertes.
                boolean compteActif = a.getUtilisateurId() != null && utilisateurRepository.findById(a.getUtilisateurId())
                        .map(u -> u.isActif()).orElse(false);
                if (!compteActif) {
                    abonnementRepository.deleteByEndpoint(a.getEndpoint());
                    continue;
                }
                envoyer(a, contenu);
            }
        } catch (Exception e) {
            log.warn("Notifications push non envoyées : {}", e.getMessage());
        }
    }

    private void envoyer(PushAbonnement a, byte[] contenu) {
        try {
            if (!adresseAutorisee(a.getEndpoint())) {
                abonnementRepository.deleteByEndpoint(a.getEndpoint());
                log.warn("Adresse de notification non autorisée, retirée de la liste");
                return;
            }
            PushCleVapid c = cle();
            URI cible = URI.create(a.getEndpoint());
            String audience = cible.getScheme() + "://" + cible.getHost() + (cible.getPort() > 0 ? ":" + cible.getPort() : "");
            String jeton = WebPushChiffrement.jetonVapid(audience, CONTACT,
                    WebPushChiffrement.priveeDepuisBrute(WebPushChiffrement.deB64(c.getClePrivee())),
                    System.currentTimeMillis() / 1000 + 12 * 3600);
            byte[] corps = WebPushChiffrement.chiffrer(contenu,
                    WebPushChiffrement.deB64(a.getP256dh()), WebPushChiffrement.deB64(a.getAuth()));

            HttpRequest requete = HttpRequest.newBuilder(cible)
                    .timeout(Duration.ofSeconds(20))
                    .header("TTL", "86400")
                    .header("Urgency", "normal")
                    .header("Content-Encoding", "aes128gcm")
                    .header("Content-Type", "application/octet-stream")
                    .header("Authorization", "vapid t=" + jeton + ", k=" + c.getClePublique())
                    .POST(HttpRequest.BodyPublishers.ofByteArray(corps))
                    .build();
            HttpResponse<String> reponse = http.send(requete, HttpResponse.BodyHandlers.ofString());
            int code = reponse.statusCode();
            boolean adresseInvalide = code == 400 && reponse.body() != null
                    && (reponse.body().contains("BadWebPushToken") || reponse.body().contains("BadDeviceToken"));
            if (code == 404 || code == 410 || adresseInvalide) {
                abonnementRepository.deleteByEndpoint(a.getEndpoint());
                log.info("Téléphone désabonné des notifications, retiré de la liste");
            } else if (code >= 300) {
                log.warn("Notification push refusée par le service de notifications (code {})", code);
            } else {
                a.setDernierEnvoi(LocalDateTime.now());
                abonnementRepository.save(a);
            }
        } catch (Exception e) {
            log.warn("Notification push non envoyée à un téléphone : {}", e.getMessage());
        }
    }

    /** Services de notification officiels (Google, Apple, Mozilla, Microsoft). */
    private static final List<String> HOTES_AUTORISES = List.of(
            "fcm.googleapis.com", "push.apple.com", "push.services.mozilla.com", "notify.windows.com");

    /**
     * Le serveur n'envoie qu'à ces services : une adresse fournie par un utilisateur ne peut pas
     * lui faire appeler une machine interne ou un site quelconque. HTTPS, port standard, pas
     * d'identifiant dans l'adresse ; vérifié à l'abonnement ET avant chaque envoi.
     */
    static boolean adresseAutorisee(String endpoint) {
        if (endpoint == null || endpoint.length() > 700) return false;
        try {
            URI u = new URI(endpoint);
            if (!"https".equalsIgnoreCase(u.getScheme()) || u.getRawUserInfo() != null) return false;
            if (u.getPort() != -1 && u.getPort() != 443) return false;
            String hote = u.getHost();
            if (hote == null) return false;
            hote = hote.toLowerCase(java.util.Locale.ROOT);
            if (hote.endsWith(".")) hote = hote.substring(0, hote.length() - 1);
            for (String autorise : HOTES_AUTORISES) {
                if (hote.equals(autorise) || hote.endsWith("." + autorise)) return true;
            }
            return false;
        } catch (Exception e) {
            return false;
        }
    }

    /** Format attendu par le service worker Angular du mobile (affichage + page ouverte au toucher). */
    private byte[] contenu(String titre, String message, String url) throws Exception {
        Map<String, Object> clic = Map.of("default", Map.of(
                "operation", "navigateLastFocusedOrOpen",
                "url", url != null ? url : "/mobile/tabs/notifications"));
        Map<String, Object> notification = new LinkedHashMap<>();
        notification.put("title", titre);
        notification.put("body", message);
        notification.put("icon", ICONE);
        notification.put("badge", BADGE);
        notification.put("data", Map.of("onActionClick", clic));
        return objectMapper.writeValueAsString(Map.of("notification", notification)).getBytes(StandardCharsets.UTF_8);
    }

    private synchronized PushCleVapid cle() {
        if (cle != null) return cle;
        cle = cleRepository.findFirstByOrderByIdAsc().orElseGet(() -> {
            try {
                KeyPair paire = WebPushChiffrement.nouvellePaire();
                PushCleVapid c = new PushCleVapid();
                c.setClePublique(WebPushChiffrement.b64(WebPushChiffrement.publiqueBrute((ECPublicKey) paire.getPublic())));
                c.setClePrivee(WebPushChiffrement.b64(WebPushChiffrement.priveeBrute((ECPrivateKey) paire.getPrivate())));
                return cleRepository.save(c);
            } catch (Exception e) {
                throw new IllegalStateException("Création des clés de notification impossible", e);
            }
        });
        return cle;
    }
}
