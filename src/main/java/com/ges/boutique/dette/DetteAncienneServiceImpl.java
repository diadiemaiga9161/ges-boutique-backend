package com.ges.boutique.dette;

import com.ges.boutique.caisse.Caisse;
import com.ges.boutique.caisse.CaisseRepository;
import com.ges.boutique.caisse.ModePaiementCaisse;
import com.ges.boutique.caisse.OperationCaisse;
import com.ges.boutique.caisse.OperationCaisseRepository;
import com.ges.boutique.caisse.TypeOperationCaisse;
import com.ges.boutique.client.Client;
import com.ges.boutique.client.ClientRepository;
import com.ges.boutique.exception.RessourceIntrouvableException;
import com.ges.boutique.journalaudit.JournalAuditService;
import com.ges.boutique.journalaudit.TypeActionAudit;
import com.ges.boutique.utilisateur.Utilisateur;
import com.ges.boutique.utilisateur.UtilisateurRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class DetteAncienneServiceImpl implements DetteAncienneService {

    private final DetteAncienneRepository detteRepository;
    private final ReglementDetteAncienneRepository reglementRepository;
    private final ClientRepository clientRepository;
    private final UtilisateurRepository utilisateurRepository;
    private final CaisseRepository caisseRepository;
    private final OperationCaisseRepository operationRepository;
    private final JournalAuditService journalAuditService;

    @Override
    @Transactional
    public DetteAncienneDto creerDette(DetteAncienneRequest request) {
        log.info("Création d'une ancienne dette pour le client ID: {}, Montant: {}",
                request.getClientId(), request.getMontant());

        if (request.getClientId() == null) {
            throw new IllegalArgumentException("Le client est requis");
        }

        if (request.getMontant() == null || request.getMontant() <= 0) {
            throw new IllegalArgumentException("Le montant de la dette doit être supérieur à 0");
        }

        Client client = clientRepository.findById(request.getClientId())
                .orElseThrow(() -> new RessourceIntrouvableException(
                        "Client non trouvé avec l'ID: " + request.getClientId()));

        DetteAncienne dette = new DetteAncienne();
        dette.setClient(client);
        dette.setMontantInitial(request.getMontant());
        dette.setMontantRestant(request.getMontant());
        dette.setDateCredit(request.getDateCredit() != null ? request.getDateCredit() : LocalDate.now());
        dette.setDescription(request.getDescription());
        dette.setEstReglee(false);

        DetteAncienne savedDette = detteRepository.save(dette);
        log.info("Dette ancienne créée avec succès - ID: {}, Client: {}, Montant: {}",
                savedDette.getId(), client.getNom(), savedDette.getMontantInitial());

        return convertToDto(savedDette);
    }

    @Override
    @Transactional
    public DetteAncienneDto modifierDette(Long id, DetteAncienneRequest request) {
        log.info("Modification de la dette ID: {}", id);

        DetteAncienne dette = obtenirDetteEntityParId(id);

        long nombreReglements = reglementRepository.findByDetteId(id).size();
        if (nombreReglements > 0) {
            throw new IllegalStateException(
                    "Impossible de modifier une dette qui a déjà des règlements enregistrés");
        }

        if (request.getMontant() == null || request.getMontant() <= 0) {
            throw new IllegalArgumentException("Le montant de la dette doit être supérieur à 0");
        }

        Double ancienMontant = dette.getMontantInitial();
        Double difference = request.getMontant() - ancienMontant;

        dette.setMontantInitial(request.getMontant());
        dette.setMontantRestant(dette.getMontantRestant() + difference);
        dette.setDateCredit(request.getDateCredit() != null ? request.getDateCredit() : dette.getDateCredit());
        dette.setDescription(request.getDescription());

        if (dette.getMontantRestant() <= 0) {
            dette.setEstReglee(true);
        } else {
            dette.setEstReglee(false);
        }

        DetteAncienne savedDette = detteRepository.save(dette);
        log.info("Dette modifiée avec succès - ID: {}, Ancien montant: {}, Nouveau montant: {}",
                id, ancienMontant, request.getMontant());

        return convertToDto(savedDette);
    }

    @Override
    @Transactional(readOnly = true)
    public DetteAncienneDto obtenirDetteParId(Long id) {
        DetteAncienne dette = detteRepository.findById(id)
                .orElseThrow(() -> new RessourceIntrouvableException(
                        "Dette non trouvée avec l'ID: " + id));
        return convertToDto(dette);
    }

    private DetteAncienne obtenirDetteEntityParId(Long id) {
        return detteRepository.findById(id)
                .orElseThrow(() -> new RessourceIntrouvableException(
                        "Dette non trouvée avec l'ID: " + id));
    }

    @Override
    @Transactional(readOnly = true)
    public List<DetteAncienneDto> obtenirDettesParClient(Long clientId) {
        return detteRepository.findByClientId(clientId).stream()
                .map(this::convertToDto)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public List<DetteAncienneDto> obtenirToutesDettesNonReglees() {
        return detteRepository.findDettesNonReglees().stream()
                .map(this::convertToDto)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public List<DetteAncienneDto> obtenirToutesDettesReglees() {
        return detteRepository.findDettesReglees().stream()
                .map(this::convertToDto)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public List<DetteAncienneDto> obtenirToutesDettes() {
        return detteRepository.findAllOrderByDateCreditDesc().stream()
                .map(this::convertToDto)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public List<DetteAncienneDto> rechercherDettes(String search) {
        if (search == null || search.trim().isEmpty()) {
            return obtenirToutesDettesNonReglees();
        }
        return detteRepository.searchByClient(search.trim()).stream()
                .map(this::convertToDto)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public void supprimerDette(Long id) {
        DetteAncienne dette = obtenirDetteEntityParId(id);

        long nombreReglements = reglementRepository.findByDetteId(id).size();
        if (nombreReglements > 0) {
            throw new IllegalStateException(
                    "Impossible de supprimer une dette qui a déjà des règlements enregistrés");
        }

        detteRepository.deleteById(id);
        log.info("Dette supprimée avec succès - ID: {}", id);

        Utilisateur auteur = getUtilisateurCourantAudit();
        String clientInfo = dette.getClient() != null
                ? dette.getClient().getNom() + " " + dette.getClient().getPrenom()
                : "client inconnu";
        journalAuditService.enregistrer(
                auteur != null ? auteur.getId() : null,
                auteur != null ? auteur.getNomComplet() : null,
                TypeActionAudit.SUPPRESSION_CREDIT,
                "Dette #" + dette.getId() + " (" + clientInfo + ", montant " + dette.getMontantInitial()
                        + " F) supprimée");
    }

    /**
     * Utilisateur actuellement authentifié (contexte de sécurité Spring), pour les besoins
     * du journal d'audit — même mécanisme que celui déjà utilisé ailleurs dans le projet
     * (ex: DepenseController.getUserId(), ProduitNiveauController.getUserId()).
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
    public ReglementDetteAncienne enregistrerReglement(ReglementDetteRequest request) {
        log.info("Enregistrement d'un règlement de dette - Dette ID: {}, Montant: {}",
                request.getDetteId(), request.getMontantPaye());

        if (request.getDetteId() == null) {
            throw new IllegalArgumentException("La dette est requise");
        }

        if (request.getMontantPaye() == null || request.getMontantPaye() <= 0) {
            throw new IllegalArgumentException("Le montant payé doit être supérieur à 0");
        }

        DetteAncienne dette = obtenirDetteEntityParId(request.getDetteId());

        if (dette.getEstReglee()) {
            throw new IllegalStateException("Cette dette est déjà totalement réglée");
        }

        if (request.getMontantPaye() > dette.getMontantRestant()) {
            throw new IllegalArgumentException(
                    "Le montant payé (" + request.getMontantPaye() +
                            ") ne peut pas dépasser le montant restant (" + dette.getMontantRestant() + ")");
        }

        verifierEtOuvrirCaisseSiNecessaire();

        Caisse caisse = getCaisseOuverte();
        Double soldeAvant = caisse.getSoldeActuel();

        // Payé par Wave, Moov ou Orange Money : la dette baisse, mais rien n'entre dans le tiroir.
        String mode = request.getModePaiement();
        boolean horsCaisse = "ORANGE_MONEY".equals(mode) || "MOOV_MONEY".equals(mode) || "WAVE_MONEY".equals(mode);
        if (!horsCaisse) {
            caisse.setSoldeActuel(soldeAvant + request.getMontantPaye());
            caisse.setTotalEntrees(caisse.getTotalEntrees() + request.getMontantPaye());
        }
        caisse.setDerniereOperation(LocalDateTime.now());
        caisseRepository.save(caisse);

        OperationCaisse operation = new OperationCaisse();
        operation.setCaisse(caisse);
        operation.setType(TypeOperationCaisse.ENTREE);
        operation.setMontant(request.getMontantPaye());
        operation.setSoldeAvant(soldeAvant);
        operation.setSoldeApres(caisse.getSoldeActuel());
        operation.setMotif("Règlement dette ancienne - Client: " + dette.getClient().getNom() +
                " " + dette.getClient().getPrenom() + " - Dette ID: " + dette.getId());
        operation.setEstReglee(true);
        operation.setDateOperation(LocalDateTime.now());

        if (request.getUtilisateurId() != null) {
            utilisateurRepository.findById(request.getUtilisateurId())
                    .ifPresent(operation::setUtilisateur);
        }

        if (request.getModePaiement() != null) {
            try {
                operation.setModePaiement(ModePaiementCaisse.valueOf(request.getModePaiement()));
            } catch (IllegalArgumentException e) {
                operation.setModePaiement(ModePaiementCaisse.ESPECES);
            }
        }
        operation.setReferencePaiement(request.getReferencePaiement());
        operationRepository.save(operation);

        Double nouveauMontantRestant = dette.getMontantRestant() - request.getMontantPaye();
        dette.setMontantRestant(nouveauMontantRestant);
        dette.setDateDernierReglement(LocalDateTime.now());

        if (nouveauMontantRestant <= 0) {
            dette.setEstReglee(true);
        }

        DetteAncienne detteMisAJour = detteRepository.save(dette);

        ReglementDetteAncienne reglement = new ReglementDetteAncienne();
        reglement.setDette(detteMisAJour);
        reglement.setMontantPaye(request.getMontantPaye());
        reglement.setMontantRestantApres(nouveauMontantRestant);
        reglement.setModePaiement(request.getModePaiement() != null ? request.getModePaiement() : "ESPECES");
        reglement.setReferencePaiement(request.getReferencePaiement());
        reglement.setObservations(request.getObservations());

        if (request.getUtilisateurId() != null) {
            utilisateurRepository.findById(request.getUtilisateurId())
                    .ifPresent(reglement::setUtilisateur);
        }

        ReglementDetteAncienne savedReglement = reglementRepository.save(reglement);

        log.info("Règlement enregistré avec succès - Dette ID: {}, Montant payé: {}, Restant: {}",
                dette.getId(), request.getMontantPaye(), nouveauMontantRestant);

        return savedReglement;
    }

    @Override
    @Transactional(readOnly = true)
    public List<ReglementDetteAncienne> getHistoriqueReglements(Long detteId) {
        return reglementRepository.findByDetteId(detteId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ReglementDetteAncienne> getReglementsParPeriode(LocalDate dateDebut, LocalDate dateFin) {
        LocalDateTime debut = dateDebut.atStartOfDay();
        LocalDateTime fin = dateFin.atTime(LocalTime.MAX);
        return reglementRepository.findByPeriode(debut, fin);
    }

    @Override
    @Transactional(readOnly = true)
    public Map<String, Object> getStatistiquesGlobales() {
        Map<String, Object> stats = new HashMap<>();

        Double totalDettesInitiales = detteRepository.getTotalDettesInitiales();
        Double totalDettesRestantes = detteRepository.getTotalDettesRestantes();
        Long nombreDettesNonReglees = detteRepository.countDettesNonReglees();
        Long nombreDettesReglees = detteRepository.findDettesReglees().stream().count();
        Double totalReglements = reglementRepository.getTotalReglements();
        Double totalReglementsDuJour = reglementRepository.getTotalReglementsDuJour();

        stats.put("totalDettesInitiales", arrondir(totalDettesInitiales != null ? totalDettesInitiales : 0));
        stats.put("totalDettesRestantes", arrondir(totalDettesRestantes != null ? totalDettesRestantes : 0));
        stats.put("totalReglementsEffectues", arrondir(totalReglements != null ? totalReglements : 0));
        stats.put("nombreDettesNonReglees", nombreDettesNonReglees != null ? nombreDettesNonReglees : 0);
        stats.put("nombreDettesReglees", nombreDettesReglees);
        stats.put("totalReglementsDuJour", arrondir(totalReglementsDuJour != null ? totalReglementsDuJour : 0));

        return stats;
    }

    @Override
    @Transactional(readOnly = true)
    public Map<String, Object> getStatistiquesParClient(Long clientId) {
        Client client = clientRepository.findById(clientId)
                .orElseThrow(() -> new RessourceIntrouvableException(
                        "Client non trouvé avec l'ID: " + clientId));

        List<DetteAncienneDto> dettes = obtenirDettesParClient(clientId);

        Double totalDettes = dettes.stream()
                .mapToDouble(DetteAncienneDto::getMontantInitial)
                .sum();

        Double totalRestant = dettes.stream()
                .filter(d -> !d.getEstReglee())
                .mapToDouble(DetteAncienneDto::getMontantRestant)
                .sum();

        Double totalPaye = dettes.stream()
                .mapToDouble(DetteAncienneDto::getMontantPaye)
                .sum();

        Map<String, Object> stats = new HashMap<>();
        stats.put("clientId", client.getId());
        stats.put("clientNom", client.getNom());
        stats.put("clientPrenom", client.getPrenom());
        stats.put("clientTelephone", client.getNumeroTelephone());
        stats.put("nombreDettes", dettes.size());
        stats.put("nombreDettesNonReglees", dettes.stream().filter(d -> !d.getEstReglee()).count());
        stats.put("nombreDettesReglees", dettes.stream().filter(DetteAncienneDto::getEstReglee).count());
        stats.put("totalDettes", arrondir(totalDettes));
        stats.put("totalRestant", arrondir(totalRestant));
        stats.put("totalPaye", arrondir(totalPaye));
        stats.put("dettes", dettes);

        return stats;
    }

    @Override
    @Transactional(readOnly = true)
    public Map<String, Object> getStatistiquesReglementsParPeriode(LocalDate dateDebut, LocalDate dateFin) {
        List<ReglementDetteAncienne> reglements = getReglementsParPeriode(dateDebut, dateFin);

        Double totalReglements = reglements.stream()
                .mapToDouble(ReglementDetteAncienne::getMontantPaye)
                .sum();

        Map<LocalDate, Double> reglementsParJour = reglements.stream()
                .collect(Collectors.groupingBy(
                        r -> r.getDateReglement().toLocalDate(),
                        Collectors.summingDouble(ReglementDetteAncienne::getMontantPaye)
                ));

        Map<String, Double> reglementsParMode = reglements.stream()
                .collect(Collectors.groupingBy(
                        ReglementDetteAncienne::getModePaiement,
                        Collectors.summingDouble(ReglementDetteAncienne::getMontantPaye)
                ));

        Map<String, Object> stats = new HashMap<>();
        stats.put("periode", Map.of("debut", dateDebut, "fin", dateFin));
        stats.put("nombreReglements", reglements.size());
        stats.put("totalReglements", arrondir(totalReglements));
        stats.put("reglementsParJour", reglementsParJour);
        stats.put("reglementsParMode", reglementsParMode);
        stats.put("moyenneJournaliere", arrondir(
                totalReglements / (ChronoUnit.DAYS.between(dateDebut, dateFin) + 1)));

        return stats;
    }

    private DetteAncienneDto convertToDto(DetteAncienne dette) {
        DetteAncienneDto dto = new DetteAncienneDto();
        dto.setId(dette.getId());

        // Accès sécurisé au client (chargement dans la transaction)
        Client client = dette.getClient();
        if (client != null) {
            dto.setClientId(client.getId());
            dto.setClientNom(client.getNom());
            dto.setClientPrenom(client.getPrenom());
            dto.setClientTelephone(client.getNumeroTelephone());
        }

        dto.setMontantInitial(dette.getMontantInitial());
        dto.setMontantRestant(dette.getMontantRestant());
        dto.setMontantPaye(dette.getMontantPaye());
        dto.setDateCredit(dette.getDateCredit());
        dto.setDescription(dette.getDescription());
        dto.setEstReglee(dette.getEstReglee());
        dto.setDateCreation(dette.getDateCreation());
        dto.setDateDernierReglement(dette.getDateDernierReglement());
        return dto;
    }

    private void verifierEtOuvrirCaisseSiNecessaire() {
        try {
            getCaisseOuverte();
        } catch (IllegalStateException e) {
            ouvrirCaisse();
        }
    }

    private Caisse getCaisseOuverte() {
        return caisseRepository.findCaisseOuverte()
                .orElseThrow(() -> new IllegalStateException(
                        "Aucune caisse n'est ouverte. Veuillez ouvrir une caisse"));
    }

    private void ouvrirCaisse() {
        Caisse caisse = new Caisse();
        caisse.setSoldeActuel(0.0);
        caisse.setSoldeInitial(0.0);
        caisse.setSoldeSysteme(0.0);
        caisse.setSoldeReel(0.0);
        caisse.setEcart(0.0);
        caisse.setTotalEntrees(0.0);
        caisse.setTotalSorties(0.0);
        caisse.setEstOuverte(true);
        caisse.setDateOuverture(LocalDateTime.now());
        caisse.setDerniereOperation(LocalDateTime.now());
        caisse.setNombreOperations(0);
        caisse.setVerifiee(false);

        Caisse savedCaisse = caisseRepository.save(caisse);

        OperationCaisse operation = new OperationCaisse();
        operation.setCaisse(savedCaisse);
        operation.setType(TypeOperationCaisse.OUVERTURE);
        operation.setMontant(savedCaisse.getSoldeActuel());
        operation.setSoldeAvant(savedCaisse.getSoldeActuel());
        operation.setSoldeApres(savedCaisse.getSoldeActuel());
        operation.setMotif("Ouverture automatique de caisse pour règlement dette ancienne");
        operation.setDateOperation(LocalDateTime.now());
        operation.setEstReglee(true);
        operationRepository.save(operation);
    }

    private Double arrondir(Double valeur) {
        if (valeur == null) return 0.0;
        return BigDecimal.valueOf(valeur)
                .setScale(2, RoundingMode.HALF_UP)
                .doubleValue();
    }
}