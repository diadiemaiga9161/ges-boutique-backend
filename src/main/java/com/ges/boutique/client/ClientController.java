package com.ges.boutique.client;

import com.ges.boutique.commande.CommandeService;
import com.ges.boutique.email.QrCodeService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.beans.factory.annotation.Value;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Slf4j
@RestController
@RequestMapping("/api/clients")
@RequiredArgsConstructor
@CrossOrigin("*")
public class ClientController {

    private final ClientService clientService;
    private final ClientReleveService clientReleveService;
    private final ClientReleveApiService clientReleveApiService;
    private final QrCodeService qrCodeService;
    private final CommandeService commandeService;

    @Value("${app.base-url:http://localhost:8080}")
    private String baseUrl;

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'VENDEUR')")
    public ResponseEntity<Map<String, Object>> creerClient(@RequestBody Client client) {
        Client nouveauClient = clientService.creerClient(client);
        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("message", "Client créé avec succès");
        response.put("client", nouveauClient);
        return ResponseEntity.ok(response);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'VENDEUR')")
    public ResponseEntity<Map<String, Object>> modifierClient(@PathVariable Long id, @RequestBody Client client) {
        Client clientModifie = clientService.modifierClient(id, client);
        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("message", "Client modifié avec succès");
        response.put("client", clientModifie);
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, Object>> supprimerClient(@PathVariable Long id) {
        clientService.supprimerClient(id);
        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("message", "Client supprimé avec succès");
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'VENDEUR')")
    public ResponseEntity<Map<String, Object>> trouverParId(@PathVariable Long id) {
        Optional<Client> client = clientService.trouverParId(id);
        if (client.isPresent()) {
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("client", client.get());
            return ResponseEntity.ok(response);
        }
        Map<String, Object> response = new HashMap<>();
        response.put("success", false);
        response.put("message", "Client non trouvé");
        return ResponseEntity.status(404).body(response);
    }

    @GetMapping("/telephone/{numeroTelephone}")
    @PreAuthorize("hasAnyRole('ADMIN', 'VENDEUR')")
    public ResponseEntity<Map<String, Object>> trouverParNumeroTelephone(@PathVariable String numeroTelephone) {
        Optional<Client> client = clientService.trouverParNumeroTelephone(numeroTelephone);
        if (client.isPresent()) {
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("client", client.get());
            return ResponseEntity.ok(response);
        }
        Map<String, Object> response = new HashMap<>();
        response.put("success", false);
        response.put("message", "Client non trouvé");
        return ResponseEntity.status(404).body(response);
    }

    /** Ids des clients ayant déjà commandé depuis le portail (badge + filtre de la liste).
     *  À part de la liste elle-même : son contenu reste exactement le même qu'avant. */
    @GetMapping("/portail-ids")
    @PreAuthorize("hasAnyRole('ADMIN', 'VENDEUR')")
    public ResponseEntity<Map<String, Object>> clientsDuPortail() {
        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("clientIds", commandeService.clientsAyantCommandeEnLigne());
        return ResponseEntity.ok(response);
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'VENDEUR')")
    public ResponseEntity<Map<String, Object>> trouverTous() {
        List<Client> clients = clientService.trouverTous();
        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("clients", clients);
        response.put("nombreClients", clients.size());
        return ResponseEntity.ok(response);
    }

    @GetMapping("/recherche")
    @PreAuthorize("hasAnyRole('ADMIN', 'VENDEUR')")
    public ResponseEntity<Map<String, Object>> rechercher(@RequestParam String query) {
        List<Client> clients = clientService.rechercherParNomOuPrenom(query, query);
        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("clients", clients);
        response.put("nombreResultats", clients.size());
        return ResponseEntity.ok(response);
    }

    @GetMapping("/partenaires")
    @PreAuthorize("hasAnyRole('ADMIN', 'VENDEUR')")
    public ResponseEntity<Map<String, Object>> getPartenaires() {
        List<Client> partenaires = clientService.getPartenaires();
        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("clients", partenaires);
        response.put("nombreClients", partenaires.size());
        return ResponseEntity.ok(response);
    }

    @GetMapping("/top-clients")
    @PreAuthorize("hasAnyRole('ADMIN', 'VENDEUR')")
    public ResponseEntity<Map<String, Object>> trouverTopClients() {
        List<Object[]> results = clientService.trouverTopClientsParMontant();
        List<Map<String, Object>> topClients = results.stream().map(result -> {
            Client client = (Client) result[0];
            Long nombreAchats = (Long) result[1];
            Double montantTotal = (Double) result[2];
            Map<String, Object> clientInfo = new HashMap<>();
            clientInfo.put("id", client.getId());
            clientInfo.put("nom", client.getNom());
            clientInfo.put("prenom", client.getPrenom());
            clientInfo.put("numeroTelephone", client.getNumeroTelephone());
            clientInfo.put("nombreAchats", nombreAchats);
            clientInfo.put("montantTotal", montantTotal);
            return clientInfo;
        }).toList();

        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("topClients", topClients);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{id}/releve-pdf")
    public ResponseEntity<byte[]> telechargerReleve(
            @PathVariable Long id,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateDebut,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFin,
            @RequestParam(required = false) String type) {
        try {
            byte[] pdf = clientReleveService.genererReleve(id, dateDebut, dateFin, type);
            Client client = clientService.trouverParId(id)
                    .orElseThrow(() -> new RuntimeException("Client introuvable"));
            String nomComplet = (client.getNom() + " " + client.getPrenom()).trim();
            String nomFichier = "releve-" + nomComplet.replaceAll("[^a-zA-Z0-9]", "-") + ".pdf";
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_PDF);
            headers.setContentDispositionFormData("attachment", nomFichier);
            return new ResponseEntity<>(pdf, headers, HttpStatus.OK);
        } catch (Exception e) {
            return ResponseEntity.notFound().build();
        }
    }

    /**
     * Relevé client / situation client — JSON paginé, historique chronologique complet
     * (ventes + versements + retours) avec reliquat cumulé calculé côté serveur.
     * Socle unique consommé par Angular, Ionic et React Native.
     */
    @GetMapping("/{id}/releve")
    @PreAuthorize("hasAnyRole('ADMIN', 'VENDEUR')")
    public ResponseEntity<Map<String, Object>> obtenirReleve(
            @PathVariable Long id,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateDebut,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFin,
            @RequestParam(required = false) String type) {
        try {
            Map<String, Object> releve = clientReleveApiService.genererReleve(id, page, size, dateDebut, dateFin, type);
            return ResponseEntity.ok(releve);
        } catch (Exception e) {
            log.error("Erreur génération relevé JSON client {}: {}", id, e.getMessage());
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", "Client introuvable ou erreur lors du calcul du relevé");
            return ResponseEntity.status(404).body(response);
        }
    }

    @GetMapping("/{id}/qrcode")
    public ResponseEntity<byte[]> obtenirQrCode(@PathVariable Long id) {
        try {
            String url = baseUrl + "/api/clients/" + id + "/releve-pdf";
            byte[] qr = qrCodeService.genererQrCode(url, 250, 250);
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.IMAGE_PNG);
            return new ResponseEntity<>(qr, headers, HttpStatus.OK);
        } catch (Exception e) {
            return ResponseEntity.notFound().build();
        }
    }
}