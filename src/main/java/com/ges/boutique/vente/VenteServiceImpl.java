package com.ges.boutique.vente;

import com.ges.boutique.avance.AvanceClientService;
import com.ges.boutique.vente.dto.VenteAnnuleeDTO;
import com.ges.boutique.caisse.CaisseService;
import com.ges.boutique.client.Client;
import com.ges.boutique.client.ClientRepository;
import com.ges.boutique.config.NotificationService;
import com.ges.boutique.exception.RessourceIntrouvableException;
import com.ges.boutique.websocket.StockWebSocketService;
import com.ges.boutique.exception.StockInsuffisantException;
import com.ges.boutique.fidelite.FideliteService;
import com.ges.boutique.inventaire.InventaireService;
import com.ges.boutique.inventaire.MouvementStock;
import com.ges.boutique.inventaire.MouvementStockRepository;
import com.ges.boutique.inventaire.TypeMouvement;
import com.ges.boutique.journalaudit.JournalAuditService;
import com.ges.boutique.journalaudit.TypeActionAudit;
import com.ges.boutique.produit.Produit;
import com.ges.boutique.produit.ProduitNiveau;
import com.ges.boutique.produit.ProduitNiveauRepository;
import com.ges.boutique.produit.ProduitRepository;
import com.ges.boutique.utilisateur.Utilisateur;
import com.ges.boutique.utilisateur.UtilisateurRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.context.annotation.Lazy;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class VenteServiceImpl implements VenteService {

    private static final String CLIENT_DIVERS_NOM = "Client divers";

    private final VenteRepository venteRepository;
    private final ProduitRepository produitRepository;
    private final ProduitNiveauRepository niveauRepository;
    private final UtilisateurRepository utilisateurRepository;
    private final InventaireService inventaireService;
    private final LigneVenteRepository ligneVenteRepository;
    private final CaisseService caisseService;
    private final ClientRepository clientRepository;
    private final AvanceClientService avanceClientService;
    private final NotificationService notificationService;
    private final MouvementStockRepository mouvementStockRepository;
    private final StockWebSocketService stockWebSocketService;
    private final JournalAuditService journalAuditService;
    private final FideliteService fideliteService;

    /** Identifiant de la boutique courante (1 instance = 1 boutique dans cette architecture). */
    private static final Long BOUTIQUE_ID = 1L;

    // ==================== CRÉATION VENTES ====================

    @Override
    @Transactional
    @CacheEvict(value = "produits", allEntries = true)
    public Vente creerVente(VenteRequest request) {
        Vente savedVente = preparerVenteBase(request);

        if (Boolean.TRUE.equals(savedVente.getEstCredit())) {
            log.info("Création d'une vente CRÉDIT via commande pour le vendeur ID: {}", request.getVendeurId());
            // Enregistrement caisse en tant que VENTE_CREDIT pour que l'annulation fonctionne
            caisseService.enregistrerVenteCredit(savedVente, request.getVendeurId(),
                    savedVente.getClientNom(), savedVente.getClientTelephone(), request.getDateEcheance());
            notificationService.notifierNouvelleVente(Map.of(
                    "id", savedVente.getId(),
                    "numeroVente", savedVente.getNumeroVente(),
                    "montantTotal", savedVente.getMontantTotal(),
                    "type", "CREDIT"
            ));
        } else {
            log.info("Création d'une vente COMPTANT pour le vendeur ID: {}", request.getVendeurId());
            caisseService.enregistrerVente(savedVente, request.getVendeurId(),
                    request.getModePaiement().toString(), request.getReferencePaiement());
            notificationService.notifierNouvelleVente(Map.of(
                    "id", savedVente.getId(),
                    "numeroVente", savedVente.getNumeroVente(),
                    "montantTotal", savedVente.getMontantTotal(),
                    "type", "COMPTANT"
            ));
        }

        notificationService.notifierMiseAJourDashboard();
        return savedVente;
    }

    @Override
    @Transactional
    @CacheEvict(value = "produits", allEntries = true)
    public Vente creerVenteCredit(VenteCreditRequest request) {
        log.info("=== CRÉATION D'UN CRÉDIT UNIQUEMENT ===");
        log.info("Client: {}", request.getClientNom());

        request.setEstCredit(true);

        if (request.getClientId() == null && (request.getClientNom() == null || request.getClientNom().trim().isEmpty())) {
            throw new IllegalArgumentException("Le nom du client est requis pour un crédit");
        }

        if (request.getDateEcheance() == null) {
            request.setDateEcheance(LocalDate.now().plusDays(30));
        }

        if (request.getDateEcheance().isBefore(LocalDate.now())) {
            throw new IllegalArgumentException("La date d'échéance ne peut pas être dans le passé");
        }

        if (request.getClientId() == null) {
            request.setCreerClient(true);
            request.setClientDivers(false);
        }

        validerRequeteVente(request);

        Utilisateur vendeur = utilisateurRepository.findById(request.getVendeurId())
                .orElseThrow(() -> new RessourceIntrouvableException("Vendeur non trouvé: " + request.getVendeurId()));

        Vente vente = new Vente();
        vente.setVendeur(vendeur);
        vente.setModePaiement(request.getModePaiement());
        vente.setReferencePaiement(request.getReferencePaiement());
        vente.setEstCredit(true);
        vente.setMontantVerse(0.0);
        vente.setMontantRestant(0.0);
        vente.setCreditRegle(false);

        gererClientVente(vente, request);

        for (LigneVenteRequest ligneRequest : request.getLignes()) {
            LigneVente ligne = creerLigneVente(ligneRequest);
            vente.ajouterLigne(ligne);
        }

        vente.calculerTotal();

        if (request.getRemiseGlobale() != null && request.getRemiseGlobale() > 0 && request.getTypeRemiseGlobale() != null) {
            if (request.getTypeRemiseGlobale() == RemiseType.POURCENTAGE) {
                vente.appliquerRemiseGlobalePourcentage(request.getRemiseGlobale());
            } else {
                vente.appliquerRemiseGlobaleMontant(request.getRemiseGlobale());
            }
        }

        vente.setDateEcheance(request.getDateEcheance());

        double avanceUtilisee = request.getMontantAvanceUtilise() != null ? request.getMontantAvanceUtilise() : 0.0;
        double montantVerseTotal = (request.getMontantVerse() != null ? request.getMontantVerse() : 0.0) + avanceUtilisee;
        vente.setMontantAvanceUtilise(avanceUtilisee);
        vente.setMontantVerse(montantVerseTotal);
        vente.setMontantRestant(vente.getMontantTotal() - montantVerseTotal);
        vente.setCreditRegle(vente.getMontantRestant() <= 0);

        if (vente.getCreditRegle()) {
            vente.setDateReglement(LocalDate.now());
        }

        if (request.getClientRequestId() != null && !request.getClientRequestId().isBlank()) {
            vente.setClientRequestId(request.getClientRequestId());
        }

        Vente savedVente = venteRepository.save(vente);
        mettreAJourStockVente(savedVente);
        caisseService.enregistrerVenteCredit(savedVente, request.getVendeurId(),
                request.getClientNom(), request.getClientTelephone(), request.getDateEcheance());

        if (avanceUtilisee > 0) {
            avanceClientService.utiliserAvance(request.getClientNom(), avanceUtilisee);
        }

        fideliteService.gagnerPoints(savedVente.getClient(), savedVente.getId(), savedVente.getMontantTotal());

        log.info("✅ CRÉDIT créé - Numéro: {}, Client: {}, Montant: {}, estCredit: {}",
                savedVente.getNumeroVente(), savedVente.getClientNom(),
                savedVente.getMontantTotal(), savedVente.getEstCredit());

        // Notification temps réel
        notificationService.notifierNouvelleVente(Map.of(
                "id", savedVente.getId(),
                "numeroVente", savedVente.getNumeroVente(),
                "montantTotal", savedVente.getMontantTotal(),
                "clientNom", savedVente.getClientNom() != null ? savedVente.getClientNom() : "",
                "type", "CREDIT"
        ));
        notificationService.notifierMiseAJourDashboard();

        return savedVente;
    }

    // ==================== LECTURE VENTES ====================

    @Override
    public Vente obtenirVenteParId(Long id) {
        return venteRepository.findById(id)
                .orElseThrow(() -> new RessourceIntrouvableException("Vente non trouvée: " + id));
    }

    @Override
    public Vente obtenirVenteCreditParId(Long id) {
        Vente vente = obtenirVenteParId(id);
        if (!Boolean.TRUE.equals(vente.getEstCredit())) {
            throw new IllegalArgumentException("Cette vente n'est pas un crédit");
        }
        return vente;
    }

    @Override
    public List<Vente> obtenirToutesVentes() {
        return venteRepository.findAllNonAnnulees();
    }

    @Override
    public List<Vente> obtenirToutesVentes(boolean inclureAnnulees) {
        if (inclureAnnulees) {
            return venteRepository.findAllOrderByDateVenteDesc();
        }
        return obtenirToutesVentes();
    }

    @Override
    public List<Vente> obtenirTousCredits() {
        return venteRepository.findAllCredits();
    }

    @Override
    public List<Vente> obtenirCreditsNonRegles() {
        return venteRepository.findCreditsNonRegles();
    }

    @Override
    public List<Vente> obtenirCreditsRegles() {
        return venteRepository.findCreditsRegles();
    }

    @Override
    public List<Vente> obtenirCreditsEnRetard() {
        return venteRepository.findCreditsEnRetard();
    }

    @Override
    public List<Vente> obtenirCreditsParClient(String clientNom) {
        return venteRepository.findCreditsByClientNom(clientNom);
    }

    @Override
    public List<Vente> obtenirCreditsParClientId(Long clientId) {
        return venteRepository.findByClientId(clientId).stream()
                .filter(v -> Boolean.TRUE.equals(v.getEstCredit()))
                .collect(Collectors.toList());
    }

    @Override
    public List<Vente> obtenirVentesParClientId(Long clientId) {
        // BUG FIX (page "Ventes du client" Angular) : le front chargeait auparavant TOUTES
        // les ventes de la boutique via /api/ventes puis filtrait côté JS par client — lent
        // sur une boutique avec beaucoup d'historique. findByClientId filtre déjà côté
        // serveur (comptant + crédit, non annulées, triées par date desc).
        return venteRepository.findByClientId(clientId);
    }

    @Override
    public List<Vente> obtenirVentesParVendeur(Long vendeurId) {
        return venteRepository.findByVendeurId(vendeurId).stream()
                .filter(v -> !Boolean.TRUE.equals(v.getAnnulee()))
                .collect(Collectors.toList());
    }

    @Override
    public List<Vente> obtenirVentesParDateRange(LocalDate dateDebut, LocalDate dateFin) {
        LocalDateTime debut = dateDebut.atStartOfDay();
        LocalDateTime fin = dateFin.atTime(LocalTime.MAX);
        return venteRepository.findByDateRange(debut, fin).stream()
                .filter(v -> !Boolean.TRUE.equals(v.getAnnulee()))
                .collect(Collectors.toList());
    }

    @Override
    public List<Vente> obtenirVentesDuJour() {
        LocalDateTime debut = LocalDate.now().atStartOfDay();
        LocalDateTime fin = LocalDate.now().atTime(LocalTime.MAX);
        return venteRepository.findTodayVentes(debut, fin).stream()
                .filter(v -> !Boolean.TRUE.equals(v.getAnnulee()))
                .collect(Collectors.toList());
    }

    // ==================== MODIFICATION VENTES ====================

    @Override
    @Transactional
    @CacheEvict(value = "produits", allEntries = true)
    public Vente modifierVente(Long venteId, VenteRequest request) {
        log.info("Modification de la vente ID: {}", venteId);

        Vente venteExistante = obtenirVenteParId(venteId);

        if (Boolean.TRUE.equals(venteExistante.getEstCredit())) {
            throw new IllegalArgumentException("Utilisez modifierVenteCredit pour modifier un crédit");
        }

        retablirStockAncienneVente(venteExistante);
        venteExistante.getLignes().clear();

        Utilisateur vendeur = utilisateurRepository.findById(request.getVendeurId())
                .orElseThrow(() -> new RessourceIntrouvableException("Vendeur non trouvé: " + request.getVendeurId()));

        venteExistante.setVendeur(vendeur);
        venteExistante.setModePaiement(request.getModePaiement());
        venteExistante.setReferencePaiement(request.getReferencePaiement());
        gererClientVente(venteExistante, request);

        for (LigneVenteRequest ligneRequest : request.getLignes()) {
            LigneVente ligne = creerLigneVente(ligneRequest);
            venteExistante.ajouterLigne(ligne);
        }

        venteExistante.setRemiseGlobale(0.0);
        venteExistante.setTypeRemiseGlobale(null);

        if (request.getRemiseGlobale() != null && request.getRemiseGlobale() > 0 && request.getTypeRemiseGlobale() != null) {
            if (request.getTypeRemiseGlobale() == RemiseType.POURCENTAGE) {
                venteExistante.appliquerRemiseGlobalePourcentage(request.getRemiseGlobale());
            } else {
                venteExistante.appliquerRemiseGlobaleMontant(request.getRemiseGlobale());
            }
        } else {
            venteExistante.calculerTotal();
        }

        Vente venteModifiee = venteRepository.save(venteExistante);
        mettreAJourStockVente(venteModifiee);

        return venteModifiee;
    }

    @Override
    @Transactional
    @CacheEvict(value = "produits", allEntries = true)
    public Vente modifierVenteCredit(Long venteId, VenteCreditRequest request) {
        log.info("Modification du crédit ID: {}", venteId);

        Vente venteExistante = obtenirVenteParId(venteId);

        if (!Boolean.TRUE.equals(venteExistante.getEstCredit())) {
            throw new IllegalArgumentException("Cette vente n'est pas un crédit");
        }

        if (Boolean.TRUE.equals(venteExistante.getCreditRegle())) {
            throw new IllegalStateException("Impossible de modifier un crédit déjà réglé");
        }

        retablirStockAncienneVente(venteExistante);
        venteExistante.getLignes().clear();

        Utilisateur vendeur = utilisateurRepository.findById(request.getVendeurId())
                .orElseThrow(() -> new RessourceIntrouvableException("Vendeur non trouvé: " + request.getVendeurId()));

        venteExistante.setVendeur(vendeur);
        venteExistante.setModePaiement(request.getModePaiement());
        venteExistante.setReferencePaiement(request.getReferencePaiement());

        if (request.getClientId() != null) {
            Client client = clientRepository.findById(request.getClientId()).orElse(null);
            if (client != null) {
                venteExistante.setClient(client);
                venteExistante.setClientNom(client.getNom());
                venteExistante.setClientPrenom(client.getPrenom());
                venteExistante.setClientTelephone(client.getNumeroTelephone());
                venteExistante.setClientDivers(false);
            }
        } else {
            venteExistante.setClient(null);
            venteExistante.setClientNom(request.getClientNom());
            venteExistante.setClientPrenom(request.getClientPrenom());
            venteExistante.setClientTelephone(request.getClientTelephone());
            venteExistante.setClientDivers(false);
        }

        for (LigneVenteRequest ligneRequest : request.getLignes()) {
            LigneVente ligne = creerLigneVente(ligneRequest);
            venteExistante.ajouterLigne(ligne);
        }

        venteExistante.setRemiseGlobale(0.0);
        venteExistante.setTypeRemiseGlobale(null);

        if (request.getRemiseGlobale() != null && request.getRemiseGlobale() > 0 && request.getTypeRemiseGlobale() != null) {
            if (request.getTypeRemiseGlobale() == RemiseType.POURCENTAGE) {
                venteExistante.appliquerRemiseGlobalePourcentage(request.getRemiseGlobale());
            } else {
                venteExistante.appliquerRemiseGlobaleMontant(request.getRemiseGlobale());
            }
        } else {
            venteExistante.calculerTotal();
        }

        venteExistante.setDateEcheance(request.getDateEcheance());
        venteExistante.setMontantVerse(request.getMontantVerse() != null ? request.getMontantVerse() : 0.0);
        venteExistante.setMontantRestant(venteExistante.getMontantTotal() - venteExistante.getMontantVerse());
        venteExistante.setCreditRegle(venteExistante.getMontantRestant() <= 0);

        if (venteExistante.getCreditRegle()) {
            venteExistante.setDateReglement(LocalDate.now());
        }

        Vente venteModifiee = venteRepository.save(venteExistante);
        mettreAJourStockVente(venteModifiee);

        return venteModifiee;
    }

    // ==================== SUPPRESSION ET ANNULATION ====================

    @Override
    @Transactional
    public void supprimerVente(Long venteId) {
        log.info("Suppression de la vente ID: {}", venteId);

        Vente vente = obtenirVenteParId(venteId);

        if (Boolean.TRUE.equals(vente.getEstCredit())) {
            throw new IllegalArgumentException("Utilisez supprimerVenteCredit pour supprimer un crédit");
        }

        retablirStockAncienneVente(vente);
        caisseService.annulerVente(vente, null, "Suppression vente");

        vente.setAnnulee(true);
        vente.setMotifAnnulation("Suppression vente");
        vente.setDateAnnulation(LocalDateTime.now());
        venteRepository.save(vente);

        Utilisateur auteur = getUtilisateurCourantAudit();
        journalAuditService.enregistrer(
                auteur != null ? auteur.getId() : null,
                auteur != null ? auteur.getNomComplet() : null,
                TypeActionAudit.SUPPRESSION_VENTE,
                "Vente #" + vente.getId() + " (" + vente.getNumeroVente() + ") supprimée définitivement (montant "
                        + vente.getMontantTotal() + " F)");
    }

    @Override
    @Transactional
    public void supprimerVenteCredit(Long venteId) {
        log.info("Suppression du crédit ID: {}", venteId);

        Vente vente = obtenirVenteParId(venteId);

        if (!Boolean.TRUE.equals(vente.getEstCredit())) {
            throw new IllegalArgumentException("Cette vente n'est pas un crédit");
        }

        if (Boolean.TRUE.equals(vente.getCreditRegle())) {
            throw new IllegalStateException("Impossible de supprimer un crédit déjà réglé");
        }

        retablirStockAncienneVente(vente);
        caisseService.annulerVenteCredit(vente, null, "Suppression crédit");

        vente.setAnnulee(true);
        vente.setMotifAnnulation("Suppression crédit");
        vente.setDateAnnulation(LocalDateTime.now());
        venteRepository.save(vente);

        Utilisateur auteurCredit = getUtilisateurCourantAudit();
        journalAuditService.enregistrer(
                auteurCredit != null ? auteurCredit.getId() : null,
                auteurCredit != null ? auteurCredit.getNomComplet() : null,
                TypeActionAudit.SUPPRESSION_VENTE,
                "Crédit #" + vente.getId() + " (" + vente.getNumeroVente() + ") supprimé définitivement (montant "
                        + vente.getMontantTotal() + " F, restant dû " + vente.getMontantRestant() + " F)");
    }

    /**
     * Utilisateur actuellement authentifié (contexte de sécurité Spring), pour les besoins
     * du journal d'audit — même mécanisme que celui déjà utilisé ailleurs dans le projet
     * (ex: DepenseController.getUserId(), ProduitNiveauController.getUserId()) : le principal
     * JWT est directement l'entité Utilisateur. Retourne null si non disponible (ex: appel
     * système) plutôt que d'échouer.
     */
    private Utilisateur getUtilisateurCourantAudit() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof Utilisateur u) {
            return u;
        }
        return null;
    }

    @Override
    @Transactional
    @CacheEvict(value = "produits", allEntries = true)
    public Vente annulerVente(Long venteId, Long utilisateurId, String motif) {
        log.info("Annulation de la vente ID: {} par utilisateur: {}", venteId, utilisateurId);

        Vente vente = obtenirVenteParId(venteId);

        if (Boolean.TRUE.equals(vente.getAnnulee())) {
            throw new IllegalStateException("Cette vente est déjà annulée");
        }

        if (Boolean.TRUE.equals(vente.getEstCredit())) {
            double montantRegle = vente.getMontantVerse() != null ? vente.getMontantVerse() : 0;

            // Crédit non réglé du tout → restaurer stock uniquement, ne pas toucher la caisse
            // (aucun argent n'a été encaissé, il n'y a rien à rembourser)
            if (montantRegle <= 0) {
                retablirStockAncienneVente(vente);
                vente.setAnnulee(true);
                vente.setMotifAnnulation(motif);
                vente.setDateAnnulation(LocalDateTime.now());
                vente.setUtilisateurAnnulation(utilisateurId);
                Vente venteAnnuleeNonReglee = venteRepository.save(vente);
                fideliteService.annulerMouvementsPourVente(venteId);
                notificationService.notifierVenteAnnulee(Map.of("venteId", venteId, "montant", vente.getMontantTotal()));
                notificationService.notifierMiseAJourDashboard();
                return venteAnnuleeNonReglee;
            }

            // Crédit réglé (partiellement ou totalement) : l'argent effectivement versé a été
            // encaissé → procéder à l'annulation normale avec remboursement caisse du montant
            // réellement versé (montantVerse, pas montantTotal) — géré par
            // CaisseServiceImpl.annulerVenteCreditAvecRepercussion, déjà correct sur ce point.
        }

        retablirStockAncienneVente(vente);

        if (!Boolean.TRUE.equals(vente.getEstCredit())) {
            caisseService.annulerVente(vente, utilisateurId, motif != null ? motif : "Annulation vente");
        } else {
            caisseService.annulerVenteCredit(vente, utilisateurId, motif != null ? motif : "Annulation crédit");

            // BUG FIX (audit comptable) : CaisseServiceImpl.annulerVenteCreditAvecRepercussion
            // rembourse depuis la caisse tout vente.montantVerse (cash + avance confondus),
            // mais n'a aucun moyen de recréditer l'avance du client (AvanceClientService
            // n'est pas visible depuis CaisseServiceImpl sans dépendance circulaire :
            // AvanceClientServiceImpl dépend déjà de CaisseService). Résultat avant ce
            // correctif : la part de la vente payée via avance disparaissait du système
            // (ni rendue en cash traçable, ni recréditée au client) — contrairement au
            // flux de retour (RetourVenteServiceImpl) qui gère déjà correctement cette
            // distinction avec avanceClientService.remettreAvance(). On applique ici le
            // même traitement, au niveau du service appelant qui a bien les deux dépendances.
            double avanceUtiliseeSurCetteVente = vente.getMontantAvanceUtilise() != null ? vente.getMontantAvanceUtilise() : 0.0;
            String clientNomPourAvance = vente.getClientNom() != null ? vente.getClientNom() :
                    (vente.getClient() != null ? vente.getClient().getNom() : null);
            if (avanceUtiliseeSurCetteVente > 0 && clientNomPourAvance != null) {
                avanceClientService.remettreAvance(clientNomPourAvance, avanceUtiliseeSurCetteVente);
                vente.setMontantAvanceUtilise(0.0);
                log.info("Avance restituée suite à annulation crédit : {} F pour {} (vente {})",
                        avanceUtiliseeSurCetteVente, clientNomPourAvance, vente.getNumeroVente());
            }
        }

        vente.setAnnulee(true);
        vente.setMotifAnnulation(motif);
        vente.setDateAnnulation(LocalDateTime.now());
        vente.setUtilisateurAnnulation(utilisateurId);

        Vente saved = venteRepository.save(vente);
        fideliteService.annulerMouvementsPourVente(venteId);
        notificationService.notifierVenteAnnulee(Map.of("venteId", venteId, "montant", vente.getMontantTotal()));
        notificationService.notifierMiseAJourDashboard();
        return saved;
    }

    @Override
    @Transactional
    @CacheEvict(value = "produits", allEntries = true)
    public Vente annulerVenteCredit(Long venteId, Long utilisateurId, String motif) {
        return annulerVente(venteId, utilisateurId, motif);
    }

    // ==================== RÈGLEMENTS CRÉDIT ====================

    @Override
    @Transactional
    public Vente enregistrerReglementCredit(Long venteId, ReglementCreditRequest request) {
        log.info("Enregistrement règlement crédit - Vente ID: {}, Montant: {}", venteId, request.getMontantRegle());

        Vente vente = obtenirVenteParId(venteId);

        if (!Boolean.TRUE.equals(vente.getEstCredit())) {
            throw new IllegalArgumentException("Cette vente n'est pas un crédit");
        }

        if (Boolean.TRUE.equals(vente.getCreditRegle())) {
            throw new IllegalStateException("Ce crédit est déjà réglé");
        }

        if (request.getMontantRegle() == null || request.getMontantRegle() <= 0) {
            throw new IllegalArgumentException("Le montant du règlement doit être supérieur à 0");
        }

        if (request.getMontantRegle() > vente.getMontantRestant()) {
            throw new IllegalArgumentException("Le montant réglé ne peut pas dépasser le montant restant (" + vente.getMontantRestant() + ")");
        }

        LocalDate dateReglement = request.getDateReglement() != null ? request.getDateReglement() : LocalDate.now();
        String modePaiement = request.getModePaiement() != null ? request.getModePaiement() : "ESPECES";

        caisseService.reglementCredit(venteId, request.getMontantRegle(), request.getUtilisateurId(), modePaiement, request.getReferencePaiement(), null, null);

        vente.enregistrerReglement(request.getMontantRegle(), dateReglement);

        // Traçabilité : qui a réglé le crédit
        if (request.getUtilisateurId() != null) {
            utilisateurRepository.findById(request.getUtilisateurId()).ifPresent(u -> {
                vente.setReglePar(u);
                vente.setRegleParNom(u.getNomComplet() != null ? u.getNomComplet() : u.getUsername());
            });
        }

        return venteRepository.save(vente);
    }

    // ==================== REMISES ====================

    @Override
    @Transactional
    public Vente appliquerRemiseGlobale(Long venteId, Double remise, RemiseType type) {
        Vente vente = obtenirVenteParId(venteId);

        if (remise == null || remise < 0) {
            throw new IllegalArgumentException("Le montant de la remise doit être positif");
        }

        if (type == RemiseType.POURCENTAGE && remise > 100) {
            throw new IllegalArgumentException("Le pourcentage de remise ne peut pas dépasser 100%");
        }

        if (type == RemiseType.POURCENTAGE) {
            vente.appliquerRemiseGlobalePourcentage(remise);
        } else {
            vente.appliquerRemiseGlobaleMontant(remise);
        }

        return venteRepository.save(vente);
    }

    @Override
    @Transactional
    public LigneVente appliquerRemiseLigne(Long ligneId, Double remise, RemiseType type) {
        LigneVente ligne = ligneVenteRepository.findById(ligneId)
                .orElseThrow(() -> new RessourceIntrouvableException("Ligne non trouvée: " + ligneId));

        if (remise == null || remise < 0) {
            throw new IllegalArgumentException("Le montant de la remise doit être positif");
        }

        if (type == RemiseType.POURCENTAGE && remise > 100) {
            throw new IllegalArgumentException("Le pourcentage de remise ne peut pas dépasser 100%");
        }

        Double prixMax = ligne.getPrixUnitaire() * ligne.getQuantite();
        if (type == RemiseType.MONTANT_FIXE && remise > prixMax) {
            throw new IllegalArgumentException("La remise ne peut pas dépasser le sous-total (" + prixMax + ")");
        }

        if (type == RemiseType.POURCENTAGE) {
            ligne.appliquerRemisePourcentage(remise);
        } else {
            ligne.appliquerRemiseMontant(remise);
        }

        ligne.getVente().calculerTotal();
        venteRepository.save(ligne.getVente());

        return ligneVenteRepository.save(ligne);
    }

    @Override
    @Transactional
    public Vente annulerRemiseGlobale(Long venteId) {
        Vente vente = obtenirVenteParId(venteId);
        vente.setRemiseGlobale(0.0);
        vente.setTypeRemiseGlobale(null);
        vente.calculerTotal();
        return venteRepository.save(vente);
    }

    @Override
    @Transactional
    public LigneVente annulerRemiseLigne(Long ligneId) {
        LigneVente ligne = ligneVenteRepository.findById(ligneId)
                .orElseThrow(() -> new RessourceIntrouvableException("Ligne non trouvée: " + ligneId));

        ligne.setRemisePourcentage(0.0);
        ligne.setRemiseMontant(0.0);
        ligne.calculerSousTotal();

        ligne.getVente().calculerTotal();
        venteRepository.save(ligne.getVente());

        return ligneVenteRepository.save(ligne);
    }

    // ==================== STATISTIQUES ====================

    @Override
    public Map<String, Object> obtenirStatistiquesChiffreAffaire() {
        LocalDate today = LocalDate.now();
        LocalDateTime debutJour = today.atStartOfDay();
        LocalDateTime finJour = today.atTime(LocalTime.MAX);
        LocalDateTime debutSemaine = today.minusDays(today.getDayOfWeek().getValue() - 1).atStartOfDay();
        LocalDateTime debutMois = today.withDayOfMonth(1).atStartOfDay();

        Map<String, Object> stats = new HashMap<>();
        Double caJour = venteRepository.getChiffreAffaireJournalier(debutJour, finJour);
        stats.put("chiffreAffaireJournalier", caJour != null ? caJour : 0);
        Double caHebdo = venteRepository.getChiffreAffaireHebdomadaire(debutSemaine, finJour);
        stats.put("chiffreAffaireHebdomadaire", caHebdo != null ? caHebdo : 0);
        Double caMensuel = venteRepository.getChiffreAffaireMensuel(debutMois, finJour);
        stats.put("chiffreAffaireMensuel", caMensuel != null ? caMensuel : 0);
        stats.put("totalCreditsNonRegles", venteRepository.getTotalCreditsNonRegles() != null ? venteRepository.getTotalCreditsNonRegles() : 0);
        Double reglementsJour = venteRepository.getTotalReglementsDuJour(today, today);
        stats.put("reglementsDuJour", reglementsJour != null ? reglementsJour : 0);
        return stats;
    }

    @Override
    public Map<String, Object> obtenirStatistiquesJournalieres(LocalDate date) {
        Map<String, Object> stats = new HashMap<>();
        LocalDateTime debut = date.atStartOfDay();
        LocalDateTime fin = date.atTime(LocalTime.MAX);

        List<Vente> ventes = venteRepository.findByDateRange(debut, fin).stream()
                .filter(v -> !Boolean.TRUE.equals(v.getAnnulee()))
                .collect(Collectors.toList());

        double totalCA = ventes.stream().mapToDouble(Vente::getMontantTotal).sum();
        double totalBenefice = ventes.stream().mapToDouble(Vente::getBeneficeTotal).sum();

        stats.put("date", date);
        stats.put("nombreVentes", ventes.size());
        stats.put("chiffreAffaire", totalCA);
        stats.put("beneficeTotal", totalBenefice);
        stats.put("margeMoyenne", ventes.size() > 0 ? (totalBenefice / totalCA) * 100 : 0);

        return stats;
    }

    @Override
    public Map<String, Object> obtenirStatistiquesHebdomadaires() {
        LocalDate aujourdhui = LocalDate.now();
        LocalDate debutSemaine = aujourdhui.minusDays(aujourdhui.getDayOfWeek().getValue() - 1);
        LocalDate finSemaine = debutSemaine.plusDays(6);

        Map<String, Object> stats = new HashMap<>();
        stats.put("debutSemaine", debutSemaine);
        stats.put("finSemaine", finSemaine);
        stats.put("statistiquesParJour", new HashMap<>());

        for (int i = 0; i < 7; i++) {
            LocalDate jour = debutSemaine.plusDays(i);
            stats.put("jour" + (i + 1), obtenirStatistiquesJournalieres(jour));
        }

        return stats;
    }

    @Override
    public Map<String, Object> obtenirStatistiquesMensuelles() {
        LocalDate aujourdhui = LocalDate.now();
        LocalDate debutMois = aujourdhui.withDayOfMonth(1);
        LocalDate finMois = aujourdhui.withDayOfMonth(aujourdhui.lengthOfMonth());

        List<Vente> ventes = obtenirVentesParDateRange(debutMois, finMois);

        Map<String, Object> stats = new HashMap<>();
        stats.put("mois", aujourdhui.getMonth().toString());
        stats.put("annee", aujourdhui.getYear());
        stats.put("nombreVentes", ventes.size());
        stats.put("chiffreAffaire", ventes.stream().mapToDouble(Vente::getMontantTotal).sum());
        stats.put("beneficeTotal", ventes.stream().mapToDouble(Vente::getBeneficeTotal).sum());

        return stats;
    }

    @Override
    public Map<String, Object> getStatistiquesCredits() {
        Map<String, Object> stats = new HashMap<>();
        List<Vente> creditsNonRegles = obtenirCreditsNonRegles();
        List<Vente> creditsEnRetard = obtenirCreditsEnRetard();

        stats.put("nombreCreditsNonRegles", creditsNonRegles.size());
        stats.put("montantTotalCreditsNonRegles", creditsNonRegles.stream().mapToDouble(Vente::getMontantRestant).sum());
        stats.put("nombreCreditsEnRetard", creditsEnRetard.size());
        stats.put("montantTotalCreditsEnRetard", creditsEnRetard.stream().mapToDouble(Vente::getMontantRestant).sum());

        return stats;
    }

    @Override
    public Long compterVentesParDateRange(LocalDateTime debut, LocalDateTime fin) {
        return (long) venteRepository.findByDateRange(debut, fin).stream()
                .filter(v -> !Boolean.TRUE.equals(v.getAnnulee()))
                .count();
    }

    @Override
    public Double obtenirChiffreAffaireVendeur(Long vendeurId) {
        return obtenirVentesParVendeur(vendeurId).stream()
                .mapToDouble(Vente::getMontantTotal)
                .sum();
    }

    @Override
    public List<Map<String, Object>> obtenirTopClients() {
        Map<String, Double> topClients = new HashMap<>();
        for (Vente vente : obtenirToutesVentes()) {
            String clientNom = vente.getClientNom() != null ? vente.getClientNom() : "Client divers";
            topClients.merge(clientNom, vente.getMontantTotal(), Double::sum);
        }

        return topClients.entrySet().stream()
                .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
                .limit(10)
                .map(e -> Map.<String, Object>of("client", e.getKey(), "montantTotal", e.getValue()))
                .collect(Collectors.toList());
    }

    @Override
    public List<Map<String, Object>> obtenirTopProduitsParQuantite() {
        Map<String, Integer> topProduits = new HashMap<>();
        for (Vente vente : obtenirToutesVentes()) {
            for (LigneVente ligne : vente.getLignes()) {
                String produitNom = ligne.getProduitNom();
                topProduits.merge(produitNom, ligne.getQuantite(), Integer::sum);
            }
        }

        return topProduits.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .limit(10)
                .map(e -> Map.<String, Object>of("produit", e.getKey(), "quantite", e.getValue()))
                .collect(Collectors.toList());
    }

    @Override
    public List<Map<String, Object>> obtenirTopProduitsParChiffreAffaire() {
        Map<String, Double> topProduits = new HashMap<>();
        for (Vente vente : obtenirToutesVentes()) {
            for (LigneVente ligne : vente.getLignes()) {
                String produitNom = ligne.getProduitNom();
                topProduits.merge(produitNom, ligne.getSousTotal(), Double::sum);
            }
        }

        return topProduits.entrySet().stream()
                .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
                .limit(10)
                .map(e -> Map.<String, Object>of("produit", e.getKey(), "chiffreAffaire", e.getValue()))
                .collect(Collectors.toList());
    }

    // ==================== VENTES ANNULÉES ET CRÉDITS ACTIFS ====================

    /**
     * Retourne les ventes annulées enrichies avec le nom du vendeur et de l'annuleur.
     * Un Map<Long, String> évite les requêtes en double pour le même utilisateur.
     * Le paramètre boutiqueId est ignoré (architecture une-instance-par-boutique).
     */
    @Override
    public List<VenteAnnuleeDTO> obtenirVentesAnnulees(Long boutiqueId) {
        List<Vente> ventes = venteRepository.findAllVentesAnnulees();

        // Cache local ID utilisateur → nomComplet pour éviter les doublons de requêtes
        Map<Long, String> nomParUtilisateurId = new HashMap<>();

        return ventes.stream().map(v -> {
            VenteAnnuleeDTO dto = new VenteAnnuleeDTO();
            dto.setId(v.getId());
            dto.setNumeroVente(v.getNumeroVente());
            dto.setDateVente(v.getDateVente());
            dto.setDateAnnulation(v.getDateAnnulation());
            dto.setMontantTotal(v.getMontantTotal());
            dto.setMotifAnnulation(v.getMotifAnnulation());

            // Nom du client : champ direct sur la vente
            dto.setClientNom(v.getClientNom());

            // Nom du vendeur : relation ManyToOne EAGER, disponible sans requête supplémentaire
            if (v.getVendeur() != null) {
                String vendeurNom = v.getVendeur().getNomComplet() != null
                        ? v.getVendeur().getNomComplet()
                        : v.getVendeur().getUsername();
                dto.setVendeurNom(vendeurNom);
            }

            // Nom de l'annuleur : Long ID stocké, résolution via cache
            Long annuleurId = v.getUtilisateurAnnulation();
            if (annuleurId != null) {
                String annuleurNom = nomParUtilisateurId.computeIfAbsent(annuleurId, id ->
                        utilisateurRepository.findById(id)
                                .map(u -> u.getNomComplet() != null ? u.getNomComplet() : u.getUsername())
                                .orElse("Utilisateur inconnu")
                );
                dto.setAnnuleurNom(annuleurNom);
            }

            return dto;
        }).collect(Collectors.toList());
    }

    /**
     * Retourne les crédits actifs (non annulés) — endpoint optimisé.
     * Le paramètre boutiqueId est ignoré (architecture une-instance-par-boutique).
     */
    @Override
    public List<Vente> obtenirCreditsActifs(Long boutiqueId) {
        return venteRepository.findCreditsActifs();
    }

    // ==================== MÉTHODES PRIVÉES ====================

    private Vente preparerVenteBase(VenteRequest request) {
        validerRequeteVente(request);

        Utilisateur vendeur = utilisateurRepository.findById(request.getVendeurId())
                .orElseThrow(() -> new RessourceIntrouvableException("Vendeur non trouvé: " + request.getVendeurId()));

        Vente vente = new Vente();
        vente.setVendeur(vendeur);
        vente.setModePaiement(request.getModePaiement());
        vente.setReferencePaiement(request.getReferencePaiement());
        boolean estCredit = request.getEstCredit() != null && request.getEstCredit();
        vente.setEstCredit(estCredit);
        if (estCredit) {
            double montantVerse = request.getMontantVerse() != null ? request.getMontantVerse() : 0.0;
            vente.setMontantVerse(montantVerse);
            vente.setDateEcheance(request.getDateEcheance());
            vente.setCreditRegle(false);
        } else {
            vente.setMontantVerse(0.0);
            vente.setMontantRestant(0.0);
            vente.setCreditRegle(false);
        }

        gererClientVente(vente, request);

        for (LigneVenteRequest ligneRequest : request.getLignes()) {
            LigneVente ligne = creerLigneVente(ligneRequest);
            vente.ajouterLigne(ligne);
        }

        vente.calculerTotal();

        if (request.getRemiseGlobale() != null && request.getRemiseGlobale() > 0 && request.getTypeRemiseGlobale() != null) {
            if (request.getTypeRemiseGlobale() == RemiseType.POURCENTAGE) {
                vente.appliquerRemiseGlobalePourcentage(request.getRemiseGlobale());
            } else {
                vente.appliquerRemiseGlobaleMontant(request.getRemiseGlobale());
            }
        }

        if (request.getClientRequestId() != null && !request.getClientRequestId().isBlank()) {
            vente.setClientRequestId(request.getClientRequestId());
        }

        // Pour les ventes crédit : recalculer montantRestant après calculerTotal()
        if (Boolean.TRUE.equals(vente.getEstCredit())) {
            double verse = vente.getMontantVerse() != null ? vente.getMontantVerse() : 0.0;
            vente.setMontantRestant(vente.getMontantTotal() - verse);
            vente.setCreditRegle(vente.getMontantRestant() <= 0);
        }

        Vente savedVente = venteRepository.save(vente);
        mettreAJourStockVente(savedVente);
        fideliteService.gagnerPoints(savedVente.getClient(), savedVente.getId(), savedVente.getMontantTotal());
        return savedVente;
    }

    private void validerRequeteVente(VenteRequest request) {
        if (request == null) throw new IllegalArgumentException("La requête est requise");
        if (request.getVendeurId() == null) throw new IllegalArgumentException("Le vendeur est requis");
        if (request.getLignes() == null || request.getLignes().isEmpty()) {
            throw new IllegalArgumentException("La vente doit contenir au moins un produit");
        }
        if (request.getModePaiement() == null) throw new IllegalArgumentException("Le mode de paiement est requis");

        if (request.getModePaiement() != ModePaiement.ESPECES &&
                (request.getReferencePaiement() == null || request.getReferencePaiement().trim().isEmpty())) {
            throw new IllegalArgumentException("La référence de paiement est requise");
        }

        // Lignes sans niveauId : vérification selon présence de niveaux
        Map<Long, Integer> quantitesParProduit = new HashMap<>();
        for (LigneVenteRequest ligne : request.getLignes()) {
            if (ligne.getProduitId() == null) throw new IllegalArgumentException("L'ID produit est requis");
            if (ligne.getQuantite() == null || ligne.getQuantite() <= 0) {
                throw new IllegalArgumentException("La quantité doit être positive");
            }
            if (ligne.getNiveauId() == null) {
                int facteur = ligne.getNiveauFacteur() != null && ligne.getNiveauFacteur() > 1 ? ligne.getNiveauFacteur() : 1;
                quantitesParProduit.merge(ligne.getProduitId(), ligne.getQuantite() * facteur, Integer::sum);
            }
        }

        for (Map.Entry<Long, Integer> entry : quantitesParProduit.entrySet()) {
            Produit produit = produitRepository.findById(entry.getKey())
                    .orElseThrow(() -> new RessourceIntrouvableException("Produit non trouvé: " + entry.getKey()));
            List<ProduitNiveau> niveaux = niveauRepository.findByProduitIdOrderByOrdreAsc(produit.getId());
            if (!niveaux.isEmpty()) {
                // Produit avec niveaux : utiliser le stock total calculé depuis les niveaux
                long stockTotal = calculerStockTotalNiveaux(niveaux, produit);
                if (stockTotal < entry.getValue()) {
                    throw new StockInsuffisantException("Stock insuffisant pour " + produit.getNom() +
                            ". Disponible: " + stockTotal + " unités base, Demandé: " + entry.getValue());
                }
            } else {
                if (produit.getQuantite() < entry.getValue()) {
                    throw new StockInsuffisantException("Stock insuffisant pour " + produit.getNom() +
                            ". Disponible: " + produit.getQuantite() + ", Demandé: " + entry.getValue());
                }
            }
        }

        // Lignes avec niveauId : vérification stock cascade par niveau
        for (LigneVenteRequest ligne : request.getLignes()) {
            if (ligne.getNiveauId() != null) {
                Produit produit = produitRepository.findById(ligne.getProduitId())
                        .orElseThrow(() -> new RessourceIntrouvableException("Produit non trouvé: " + ligne.getProduitId()));
                List<ProduitNiveau> niveaux = niveauRepository.findByProduitIdOrderByOrdreAsc(ligne.getProduitId());
                ProduitNiveau niveau = niveaux.stream()
                        .filter(n -> n.getId().equals(ligne.getNiveauId()))
                        .findFirst()
                        .orElseThrow(() -> new RessourceIntrouvableException("Niveau non trouvé: " + ligne.getNiveauId()));
                long disponible = calculerDisponibleNiveau(niveau, niveaux, produit);
                if (disponible < ligne.getQuantite()) {
                    throw new StockInsuffisantException("Stock insuffisant pour " + produit.getNom() +
                            " - niveau " + niveau.getNom() + ". Disponible: " + disponible + ", Demandé: " + ligne.getQuantite());
                }
            }
        }
    }

    private long calculerDisponibleNiveau(ProduitNiveau niveau, List<ProduitNiveau> niveaux, Produit produit) {
        long direct = niveau.getStock() != null ? niveau.getStock() : 0L;
        long facteur = niveau.getFacteur() != null && niveau.getFacteur() > 0 ? niveau.getFacteur() : 1L;
        if (niveau.getParentId() == null) {
            // Niveau racine : son parent implicite est le produit principal
            // lui-même (quantitePrincipale = stock pas encore décomposé).
            long stockPrincipal = produit.getQuantitePrincipale() != null ? produit.getQuantitePrincipale() : 0L;
            return direct + stockPrincipal * facteur;
        }
        // Récursif : remonte toute la chaîne des parents (pas seulement le parent direct)
        // pour que la disponibilité annoncée corresponde à ce que la cascade peut vraiment
        // fournir en décomposant depuis le produit principal si besoin.
        ProduitNiveau parent = niveaux.stream()
                .filter(n -> n.getId().equals(niveau.getParentId()))
                .findFirst().orElse(null);
        if (parent == null) return direct;
        long disponibleParent = calculerDisponibleNiveau(parent, niveaux, produit);
        return direct + disponibleParent * facteur;
    }

    private LigneVente creerLigneVente(LigneVenteRequest ligneRequest) {
        Produit produit = produitRepository.findById(ligneRequest.getProduitId())
                .orElseThrow(() -> new RessourceIntrouvableException("Produit non trouvé: " + ligneRequest.getProduitId()));

        LigneVente ligne = new LigneVente();
        ligne.setProduit(produit);
        ligne.setQuantite(ligneRequest.getQuantite());
        ligne.setNiveauId(ligneRequest.getNiveauId()); // ID du niveau vendu (cascade stock)
        // Utilise le prix achat du niveau (conditionnement) si fourni, sinon prix achat du produit
        Double prixAchatEffectif = (ligneRequest.getPrixAchat() != null && ligneRequest.getPrixAchat() > 0)
                ? ligneRequest.getPrixAchat()
                : produit.getPrixAchat();
        ligne.setPrixAchat(prixAchatEffectif);
        // Facteur pour déduction stock en unité de base (pièces). Ex: 200 si 1 Carton = 200 Pièces
        ligne.setNiveauFacteur(ligneRequest.getNiveauFacteur() != null && ligneRequest.getNiveauFacteur() > 1
                ? ligneRequest.getNiveauFacteur() : 1);

        Double prixVente = ligneRequest.getPrixUnitaire() != null ? ligneRequest.getPrixUnitaire() : produit.getPrixVente();
        ligne.setPrixUnitaire(prixVente);
        ligne.setPrixOriginalProduit(produit.getPrixVente());

        if (ligneRequest.getRemisePourcentage() != null && ligneRequest.getRemisePourcentage() > 0) {
            ligne.appliquerRemisePourcentage(ligneRequest.getRemisePourcentage());
        } else if (ligneRequest.getRemiseMontant() != null && ligneRequest.getRemiseMontant() > 0) {
            ligne.appliquerRemiseMontant(ligneRequest.getRemiseMontant());
        } else {
            ligne.calculerSousTotal();
        }

        return ligne;
    }

    private void gererClientVente(Vente vente, VenteRequest request) {
        vente.setClient(null);
        vente.setClientDivers(false);

        if (request.getClientId() != null) {
            Client client = clientRepository.findById(request.getClientId()).orElse(null);
            if (client != null) {
                vente.setClient(client);
                vente.setClientNom(client.getNom());
                vente.setClientPrenom(client.getPrenom());
                vente.setClientTelephone(client.getNumeroTelephone());
                return;
            }
        }

        if (request.getClientTelephone() != null && !request.getClientTelephone().trim().isEmpty()) {
            Client client = clientRepository.findByNumeroTelephone(request.getClientTelephone()).orElse(null);
            if (client != null) {
                vente.setClient(client);
                vente.setClientNom(client.getNom());
                vente.setClientPrenom(client.getPrenom());
                vente.setClientTelephone(client.getNumeroTelephone());
                return;
            }
            if (Boolean.TRUE.equals(request.getCreerClient())) {
                client = new Client();
                client.setNom(request.getClientNom() != null ? request.getClientNom() : "Client");
                client.setPrenom(request.getClientPrenom() != null ? request.getClientPrenom() : "");
                client.setNumeroTelephone(request.getClientTelephone());
                client = clientRepository.save(client);
                vente.setClient(client);
                vente.setClientNom(client.getNom());
                vente.setClientPrenom(client.getPrenom());
                vente.setClientTelephone(client.getNumeroTelephone());
                return;
            }
        }

        vente.setClientDivers(true);
        vente.setClientNom(request.getClientNom() != null ? request.getClientNom() : CLIENT_DIVERS_NOM);
        vente.setClientPrenom(request.getClientPrenom());
        vente.setClientTelephone(request.getClientTelephone());
    }

    private void mettreAJourStockVente(Vente vente) {
        List<MouvementStock> mouvementsNiveaux = new ArrayList<>();

        for (LigneVente ligne : vente.getLignes()) {
            Long produitId = ligne.getProduit().getId();

            if (ligne.getNiveauId() == null) {
                List<ProduitNiveau> niveaux = niveauRepository.findByProduitIdOrderByOrdreAsc(produitId);
                if (!niveaux.isEmpty()) {
                    // Produit avec niveaux : déduire depuis le niveau feuille (base) via cascade
                    Produit produit = produitRepository.findById(produitId)
                            .orElseThrow(() -> new RessourceIntrouvableException("Produit non trouvé: " + produitId));
                    ProduitNiveau niveauFeuille = trouverNiveauFeuille(niveaux);
                    int facteur = ligne.getNiveauFacteur() != null && ligne.getNiveauFacteur() > 1 ? ligne.getNiveauFacteur() : 1;
                    int qteFeuille = ligne.getQuantite() * facteur;
                    for (int i = 0; i < qteFeuille; i++) {
                        mouvementsNiveaux.addAll(deductUniteNiveauCascade(produit, niveaux, niveauFeuille));
                    }
                    niveauRepository.saveAll(niveaux);
                    List<ProduitNiveau> niveauxUpdated = niveauRepository.findByProduitIdOrderByOrdreAsc(produitId);
                    syncProduitQuantite(produit, niveauxUpdated);
                } else {
                    // Produit sans niveaux : déduction directe du stock produit
                    int facteur = ligne.getNiveauFacteur() != null ? ligne.getNiveauFacteur() : 1;
                    inventaireService.sortieStockVente(produitId, ligne.getQuantite() * facteur,
                            vente.getVendeur() != null ? vente.getVendeur().getId() : null, vente.getId());
                }
            } else {
                // Vente par niveau : déduction cascade
                Produit produit = produitRepository.findById(produitId)
                        .orElseThrow(() -> new RessourceIntrouvableException("Produit non trouvé: " + produitId));
                List<ProduitNiveau> niveaux = niveauRepository.findByProduitIdOrderByOrdreAsc(produitId);
                ProduitNiveau niveauVendu = niveaux.stream()
                        .filter(n -> n.getId().equals(ligne.getNiveauId()))
                        .findFirst()
                        .orElseThrow(() -> new RessourceIntrouvableException("Niveau non trouvé: " + ligne.getNiveauId()));

                for (int i = 0; i < ligne.getQuantite(); i++) {
                    mouvementsNiveaux.addAll(deductUniteNiveauCascade(produit, niveaux, niveauVendu));
                }
                niveauRepository.saveAll(niveaux);
                // Synchroniser Produit.quantite avec le total des stocks niveaux
                List<ProduitNiveau> niveauxUpdated = niveauRepository.findByProduitIdOrderByOrdreAsc(produitId);
                syncProduitQuantite(produit, niveauxUpdated);
            }

            produitRepository.findById(produitId).ifPresent(p -> {
                notificationService.notifierMiseAJourStock(p.getId(), p.getNom(), p.getQuantite());
                stockWebSocketService.diffuserMiseAJourStock(BOUTIQUE_ID, p.getId(), p.getNom(), p.getQuantite());
                if (p.getQuantite() == 0) {
                    stockWebSocketService.diffuserAlerteStock(BOUTIQUE_ID, "Rupture de stock : " + p.getNom());
                }
            });
        }

        if (!mouvementsNiveaux.isEmpty()) {
            mouvementStockRepository.saveAll(mouvementsNiveaux);
        }
    }

    /**
     * Déduit 1 unité du niveau target et cascade vers le parent si stock épuisé.
     * Retourne la liste des mouvements de stock générés (SORTIE pour vente directe,
     * SORTIE+AJUSTEMENT lors d'une décomposition cascade).
     */
    private List<MouvementStock> deductUniteNiveauCascade(Produit produit, List<ProduitNiveau> niveaux, ProduitNiveau target) {
        List<MouvementStock> mouvements = new ArrayList<>();

        // 1. S'assurer qu'au moins 1 unité est disponible à ce niveau, en décomposant en
        //    cascade depuis les niveaux parents si besoin (récursif, jusqu'au produit
        //    principal — plus de limite à "un seul cran").
        mouvements.addAll(assurerStockNiveauDisponible(produit, niveaux, target, 1));

        // 2. Consommer 1 unité pour la vente (le stock est maintenant garanti >= 1)
        int stock = target.getStock() != null ? target.getStock() : 0;
        target.setStock(stock - 1);
        mouvements.add(creerMouvementNiveau(
                produit, target,
                TypeMouvement.SORTIE,
                stock, stock - 1,
                "Vente - " + produit.getNom() + " [" + target.getNom() + "]"
        ));
        return mouvements;
    }

    private static final int PROFONDEUR_MAX_NIVEAUX_CASCADE = 5;

    /**
     * S'assure qu'il y a au moins {@code quantiteRequise} unités disponibles au niveau
     * {@code target}, en décomposant récursivement depuis les niveaux parents (produit
     * principal compris) si le niveau lui-même et son parent direct sont insuffisants.
     * Ne consomme rien — c'est le rôle de l'appelant après coup. Lève
     * StockInsuffisantException seulement si la hiérarchie entière est épuisée.
     */
    private List<MouvementStock> assurerStockNiveauDisponible(Produit produit, List<ProduitNiveau> niveaux,
            ProduitNiveau target, int quantiteRequise) {
        return assurerStockNiveauDisponible(produit, niveaux, target, quantiteRequise, new HashSet<>());
    }

    private List<MouvementStock> assurerStockNiveauDisponible(Produit produit, List<ProduitNiveau> niveaux,
            ProduitNiveau target, int quantiteRequise, Set<Long> visites) {
        List<MouvementStock> mouvements = new ArrayList<>();
        int stock = target.getStock() != null ? target.getStock() : 0;
        if (stock >= quantiteRequise) {
            return mouvements;
        }

        if (!visites.add(target.getId()) || visites.size() > PROFONDEUR_MAX_NIVEAUX_CASCADE) {
            throw new IllegalStateException("Hiérarchie de niveaux invalide (cycle ou profondeur > "
                    + PROFONDEUR_MAX_NIVEAUX_CASCADE + ") pour le produit " + produit.getNom());
        }

        if (target.getParentId() == null) {
            // Niveau racine : son parent implicite est le produit principal
            // lui-même (quantitePrincipale = stock pas encore décomposé,
            // ex: cartons fermés). C'est ce qui manquait avant : la cascade
            // s'arrêtait au niveau racine sans jamais puiser dans le produit.
            int facteurRacine = target.getFacteur() != null && target.getFacteur() > 0 ? target.getFacteur() : 1;
            int manqueRacine = quantiteRequise - stock;
            int principalNecessaire = (int) Math.ceil(manqueRacine / (double) facteurRacine);
            int stockPrincipalAvant = produit.getQuantitePrincipale() != null ? produit.getQuantitePrincipale() : 0;
            if (stockPrincipalAvant < principalNecessaire) {
                throw new StockInsuffisantException("Stock " + target.getNom() + " de " + produit.getNom() +
                        " épuisé, et stock principal insuffisant pour cascader (" + stockPrincipalAvant +
                        " " + produit.getNom() + " disponible(s)).");
            }
            produit.setQuantitePrincipale(stockPrincipalAvant - principalNecessaire);
            produitRepository.save(produit);

            int nouveauStockRacine = stock + principalNecessaire * facteurRacine;
            mouvements.add(creerMouvementNiveau(
                    produit, target,
                    TypeMouvement.AJUSTEMENT,
                    stock, nouveauStockRacine,
                    "Cascade depuis " + produit.getNom() + " (principal) (+" + (principalNecessaire * facteurRacine) + " " + target.getNom() + ")"
            ));
            target.setStock(nouveauStockRacine);
            return mouvements;
        }

        ProduitNiveau parent = niveaux.stream()
                .filter(n -> n.getId().equals(target.getParentId()))
                .findFirst()
                .orElseThrow(() -> new StockInsuffisantException("Niveau parent introuvable pour " + target.getNom()));

        int facteur = target.getFacteur() != null && target.getFacteur() > 0 ? target.getFacteur() : 1;
        int manque = quantiteRequise - stock;
        int parentsNecessaires = (int) Math.ceil(manque / (double) facteur);

        // Cascade récursive vers le haut (ouvre le grand-parent, arrière-grand-parent, etc.
        // si le parent direct est lui-même insuffisant — c'est ça qui manquait avant).
        mouvements.addAll(assurerStockNiveauDisponible(produit, niveaux, parent, parentsNecessaires, visites));

        int parentStockAvant = parent.getStock() != null ? parent.getStock() : 0;
        parent.setStock(parentStockAvant - parentsNecessaires);
        mouvements.add(creerMouvementNiveau(
                produit, parent,
                TypeMouvement.SORTIE,
                parentStockAvant, parent.getStock(),
                "Décomposition cascade - " + parent.getNom() + " → " + target.getNom()
        ));

        int nouveauStockTarget = stock + parentsNecessaires * facteur;
        mouvements.add(creerMouvementNiveau(
                produit, target,
                TypeMouvement.AJUSTEMENT,
                stock, nouveauStockTarget,
                "Cascade depuis " + parent.getNom() + " (+" + (parentsNecessaires * facteur) + " " + target.getNom() + ")"
        ));
        target.setStock(nouveauStockTarget);

        return mouvements;
    }

    private MouvementStock creerMouvementNiveau(Produit produit, ProduitNiveau niveau,
            TypeMouvement type, int qteAvant, int qteApres, String motif) {
        MouvementStock m = new MouvementStock();
        m.setProduit(produit);
        m.setQuantite(Math.abs(qteApres - qteAvant));
        m.setTypeMouvement(type);
        m.setQuantiteAvant(qteAvant);
        m.setQuantiteApres(qteApres);
        m.setNiveauId(niveau.getId());
        m.setNiveauNom(niveau.getNom());
        m.setMotif(motif);
        m.setReferenceType("VENTE_NIVEAU");
        return m;
    }

    // ==================== MODIFICATION LIGNES AVEC AJUSTEMENT CAISSE ====================

    @Transactional
    @CacheEvict(value = "produits", allEntries = true)
    public Map<String, Object> modifierLignesVente(Long venteId, ModificationLignesRequest request) {
        log.info("=== MODIFICATION LIGNES VENTE ID: {} ===", venteId);

        Vente vente = venteRepository.findById(venteId)
                .orElseThrow(() -> new RessourceIntrouvableException("Vente introuvable: " + venteId));

        if (Boolean.TRUE.equals(vente.getAnnulee())) {
            throw new IllegalStateException("Impossible de modifier une vente annulée");
        }

        if (Boolean.TRUE.equals(vente.getEstCredit()) && Boolean.TRUE.equals(vente.getCreditRegle())) {
            throw new IllegalStateException("Impossible de modifier un crédit entièrement réglé");
        }

        if (request.getLignes() == null || request.getLignes().isEmpty()) {
            throw new IllegalArgumentException("La liste des nouvelles lignes ne peut pas être vide");
        }

        double ancienTotal = vente.getMontantTotal() != null ? vente.getMontantTotal() : 0.0;

        // 1. Remettre l'ancien stock
        retablirStockAncienneVente(vente);
        vente.getLignes().clear();
        venteRepository.save(vente);

        // 2. Construire les nouvelles lignes
        for (LigneVenteRequest ligneReq : request.getLignes()) {
            LigneVente ligne = creerLigneVente(ligneReq);
            vente.ajouterLigne(ligne);
        }

        vente.calculerTotal();
        double nouveauTotal = vente.getMontantTotal() != null ? vente.getMontantTotal() : 0.0;
        double difference = nouveauTotal - ancienTotal;

        // 3. Ajuster la caisse selon la différence
        String motif = (request.getMotif() != null ? request.getMotif() : "Modification vente N°" + vente.getNumeroVente());

        if (Math.abs(difference) > 0.01) {
            if (difference > 0) {
                // Seulement pour les ventes COMPTANT : ici le client paye vraiment la
                // différence tout de suite → ENTRÉE caisse. Pour une vente à CRÉDIT, cet
                // argent n'est pas encaissé (il vient juste s'ajouter au montant restant
                // dû par le client via calculerTotal() plus haut) — l'ajouter aussi en
                // caisse le comptait deux fois (une fois comme dette client, une fois
                // comme argent physique reçu alors qu'aucun argent n'est entré).
                if (!Boolean.TRUE.equals(vente.getEstCredit())) {
                    caisseService.entreeCaisse(difference,
                            "Complément modification vente N°" + vente.getNumeroVente() + " - " + motif,
                            request.getUtilisateurId(), "ESPECES", null);
                    log.info("Entrée caisse: +{} F (client paye la différence)", difference);
                } else {
                    log.info("Modification crédit - Différence positive: {} F, PAS d'entrée caisse (juste ajustement du montant restant)",
                            difference);
                }
            } else {
                // ✅ MODIFICATION ICI : Seulement pour les ventes COMPTANT
                if (!Boolean.TRUE.equals(vente.getEstCredit())) {
                    // Vente comptant → remboursement en caisse
                    caisseService.sortieCaisse(Math.abs(difference),
                            "Remboursement modification vente N°" + vente.getNumeroVente() + " - " + motif,
                            request.getUtilisateurId());
                    log.info("Sortie caisse: -{} F (remboursement client - vente comptant)", Math.abs(difference));
                } else {
                    // ✅ Vente à crédit → PAS de remboursement caisse
                    log.info("Modification crédit - Différence négative: {} F, PAS de remboursement caisse (juste ajustement du montant restant)", 
                            Math.abs(difference));
                }
            }
        }

        // montantRestant/creditRegle pour les ventes à crédit sont déjà recalculés par
        // vente.calculerTotal() (ligne 1240) et par le hook @PreUpdate de l'entité au save
        // ci-dessous — pas besoin de les recalculer ici (l'ancienne version dupliquait ce
        // calcul en ignorant montantRetourne, cf. session du 2026-08-02).
        if (Boolean.TRUE.equals(vente.getEstCredit())) {
            log.info("Crédit - Nouveau montant restant: {}", vente.getMontantRestant());
        }

        Vente venteSauvee = venteRepository.save(vente);
        mettreAJourStockVente(venteSauvee);

        log.info("Vente modifiée: ancien total={}, nouveau total={}, différence={}", ancienTotal, nouveauTotal, difference);

        Map<String, Object> result = new HashMap<>();
        result.put("ancienTotal", ancienTotal);
        result.put("nouveauTotal", nouveauTotal);
        result.put("difference", difference);
        result.put("venteId", venteId);
        result.put("numeroVente", vente.getNumeroVente());
        return result;
    }

    private void retablirStockAncienneVente(Vente vente) {
        for (LigneVente ligne : vente.getLignes()) {
            if (ligne.getNiveauId() == null) {
                // Remise directe sur le stock produit
                int facteur = ligne.getNiveauFacteur() != null ? ligne.getNiveauFacteur() : 1;
                inventaireService.entreeStock(ligne.getProduit().getId(), ligne.getQuantite() * facteur,
                        vente.getVendeurId(), "Annulation vente N°" + vente.getNumeroVente());
            } else {
                // Remise sur le stock du niveau (pas de remballage automatique)
                niveauRepository.findById(ligne.getNiveauId()).ifPresent(niveau -> {
                    int stockActuel = niveau.getStock() != null ? niveau.getStock() : 0;
                    niveau.setStock(stockActuel + ligne.getQuantite());
                    niveauRepository.save(niveau);

                    // Synchroniser Produit.quantite
                    Produit produit = niveau.getProduit();
                    List<ProduitNiveau> niveaux = niveauRepository.findByProduitIdOrderByOrdreAsc(produit.getId());
                    syncProduitQuantite(produit, niveaux);
                });
            }
        }
    }

    // ── Synchronisation Produit.quantite ──────────────────────────────────────

    private void syncProduitQuantite(Produit produit, List<ProduitNiveau> niveaux) {
        long total = 0;
        for (ProduitNiveau n : niveaux) {
            long f = facteurVersBase(n, niveaux);
            total += (n.getStock() != null ? n.getStock() : 0) * f;
        }
        // Le stock encore "principal" (non décomposé) alimente le niveau
        // racine (parentId = null) : il faut l'ajouter au total affiché, sinon
        // le produit semble en rupture tant qu'aucun niveau n'a été ouvert.
        if (produit.getQuantitePrincipale() != null && !niveaux.isEmpty()) {
            ProduitNiveau racine = niveaux.stream().filter(n -> n.getParentId() == null).findFirst().orElse(null);
            if (racine != null) {
                long facteurRacine = racine.getFacteur() != null && racine.getFacteur() > 0 ? racine.getFacteur() : 1L;
                total += produit.getQuantitePrincipale() * facteurRacine * facteurVersBase(racine, niveaux);
            }
        }
        produit.setQuantite((int) total);
        produitRepository.save(produit);
    }

    private long facteurVersBase(ProduitNiveau niveau, List<ProduitNiveau> niveaux) {
        ProduitNiveau child = niveaux.stream()
                .filter(n -> niveau.getId().equals(n.getParentId()))
                .findFirst().orElse(null);
        if (child == null) return 1L; // feuille = unité de base
        return child.getFacteur() * facteurVersBase(child, niveaux);
    }

    // Niveau feuille = celui qui n'a aucun enfant (le plus petit conditionnement)
    private ProduitNiveau trouverNiveauFeuille(List<ProduitNiveau> niveaux) {
        Set<Long> parentsIds = niveaux.stream()
                .map(ProduitNiveau::getParentId)
                .filter(Objects::nonNull)
                .collect(java.util.stream.Collectors.toSet());
        // Feuille = niveau dont l'id n'est le parentId d'aucun autre niveau
        return niveaux.stream()
                .filter(n -> !parentsIds.contains(n.getId()))
                .findFirst()
                .orElse(niveaux.get(niveaux.size() - 1));
    }

    // Stock total en unités de base (pour ventes sans niveauId sur produit avec niveaux)
    private long calculerStockTotalNiveaux(List<ProduitNiveau> niveaux, Produit produit) {
        long total = 0;
        for (ProduitNiveau n : niveaux) {
            long f = facteurVersBase(n, niveaux);
            total += (n.getStock() != null ? n.getStock() : 0) * f;
        }
        if (produit.getQuantitePrincipale() != null) {
            ProduitNiveau racine = niveaux.stream().filter(n -> n.getParentId() == null).findFirst().orElse(null);
            if (racine != null) {
                long facteurRacine = racine.getFacteur() != null && racine.getFacteur() > 0 ? racine.getFacteur() : 1L;
                total += produit.getQuantitePrincipale() * facteurRacine * facteurVersBase(racine, niveaux);
            }
        }
        return total;
    }
}