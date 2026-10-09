package com.ges.boutique.caisse;

import com.ges.boutique.config.NotificationService;
import com.ges.boutique.exception.MessageErreurUtil;
import com.ges.boutique.exception.RessourceIntrouvableException;
import com.ges.boutique.exception.SoldeInsuffisantException;
import com.ges.boutique.client.Client;
import com.ges.boutique.client.ClientRepository;
import com.ges.boutique.compte.Compte;
import com.ges.boutique.compte.CompteRepository;
import com.ges.boutique.compte.OperationCompte;
import com.ges.boutique.compte.OperationCompteRepository;
import com.ges.boutique.compte.TypeOperationCompte;
import com.ges.boutique.facture.Facture;
import com.ges.boutique.facture.FactureRepository;
import com.ges.boutique.facture.LigneFacture;
import com.ges.boutique.facture.LigneFactureRepository;
import com.ges.boutique.facture.FactureRequest;
import com.ges.boutique.facture.LigneFactureRequest;
import com.ges.boutique.produit.Produit;
import com.ges.boutique.produit.ProduitRepository;
import com.ges.boutique.utilisateur.Utilisateur;
import com.ges.boutique.utilisateur.UtilisateurRepository;
import com.ges.boutique.vente.ModePaiement;
import com.ges.boutique.vente.Vente;
import com.ges.boutique.vente.VenteRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class CaisseServiceImpl implements CaisseService {

    @PersistenceContext
    private EntityManager entityManager;

    private final CaisseRepository caisseRepository;
    private final OperationCaisseRepository operationRepository;
    private final UtilisateurRepository utilisateurRepository;
    private final VenteRepository venteRepository;
    private final FactureRepository factureRepository;
    private final LigneFactureRepository ligneFactureRepository;
    private final ClientRepository clientRepository;
    private final ProduitRepository produitRepository;
    private final CompteRepository compteRepository;
    private final OperationCompteRepository operationCompteRepository;
    private final TransfertCaisseBanqueRepository transfertRepository;
    private final NotificationService notificationService;

    // ==================== GESTION DES CAISSES ====================

    @Override
    @Transactional
    public Caisse creerCaisse(String numeroCaisse) {
        Caisse caisse = new Caisse();
        if (numeroCaisse != null && !numeroCaisse.trim().isEmpty()) {
            if (caisseRepository.existsByNumeroCaisse(numeroCaisse)) {
                throw new RuntimeException("Une caisse avec ce numéro existe déjà: " + numeroCaisse);
            }
            caisse.setNumeroCaisse(numeroCaisse);
        }
        caisse.setSoldeActuel(0.0);
        caisse.setSoldeInitial(0.0);
        caisse.setSoldeSysteme(0.0);
        caisse.setSoldeReel(0.0);
        caisse.setEcart(0.0);
        caisse.setTotalEntrees(0.0);
        caisse.setTotalSorties(0.0);
        caisse.setEstOuverte(false);
        caisse.setNombreOperations(0);
        caisse.setVerifiee(false);
        return caisseRepository.save(caisse);
    }

    @Override
    @Transactional
    public Caisse ouvrirCaisse() {
        Optional<Caisse> caisseOuverte = caisseRepository.findCaisseOuverte();
        if (caisseOuverte.isPresent()) {
            log.info("Caisse déjà ouverte: {}", caisseOuverte.get().getNumeroCaisse());
            return caisseOuverte.get();
        }

        try {
            Optional<Caisse> derniereCaisse = caisseRepository.findFirstByOrderByIdDesc();
            Caisse caisse;

            if (derniereCaisse.isPresent() && !derniereCaisse.get().isEstOuverte()) {
                caisse = derniereCaisse.get();
                caisse.setEstOuverte(true);
                caisse.setDateOuverture(LocalDateTime.now());
                caisse.setDerniereOperation(LocalDateTime.now());
                caisse.setVerifiee(false);
                caisse.setSoldeSysteme(caisse.getSoldeActuel());
                caisse.setSoldeReel(caisse.getSoldeActuel());
                log.info("Réouverture de la caisse existante: {}", caisse.getNumeroCaisse());
            } else {
                caisse = new Caisse();
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
                log.info("Création d'une nouvelle caisse");
            }

            Caisse savedCaisse = caisseRepository.save(caisse);

            OperationCaisse operation = new OperationCaisse();
            operation.setCaisse(savedCaisse);
            operation.setType(TypeOperationCaisse.OUVERTURE);
            operation.setMontant(savedCaisse.getSoldeActuel());
            operation.setSoldeAvant(savedCaisse.getSoldeActuel());
            operation.setSoldeApres(savedCaisse.getSoldeActuel());
            operation.setMotif("Ouverture de caisse - " + savedCaisse.getNumeroCaisse());
            operation.setDateOperation(LocalDateTime.now());
            operation.setEstReglee(true);
            operationRepository.save(operation);
            notificationService.notifierOuvertureCaisse(Map.of(
                    "numeroCaisse", savedCaisse.getNumeroCaisse(),
                    "solde", savedCaisse.getSoldeActuel()
            ));

            return savedCaisse;

        } catch (Exception e) {
            log.error("Erreur lors de l'ouverture de la caisse: {}", e.getMessage(), e);
            throw new RuntimeException("Impossible d'ouvrir la caisse: " + MessageErreurUtil.messageClient(e));
        }
    }

    @Override
    @Transactional
    public Caisse ouvrirCaisse(Long caisseId) {
        Caisse caisse = caisseRepository.findById(caisseId)
                .orElseThrow(() -> new RessourceIntrouvableException("Caisse non trouvée avec l'ID: " + caisseId));

        if (caisse.isEstOuverte()) {
            log.info("Caisse déjà ouverte: {}", caisse.getNumeroCaisse());
            return caisse;
        }

        caisse.setEstOuverte(true);
        caisse.setDateOuverture(LocalDateTime.now());
        caisse.setDerniereOperation(LocalDateTime.now());
        caisse.setVerifiee(false);

        Caisse savedCaisse = caisseRepository.save(caisse);

        OperationCaisse operation = new OperationCaisse();
        operation.setCaisse(savedCaisse);
        operation.setType(TypeOperationCaisse.OUVERTURE);
        operation.setMontant(savedCaisse.getSoldeActuel());
        operation.setSoldeAvant(savedCaisse.getSoldeActuel());
        operation.setSoldeApres(savedCaisse.getSoldeActuel());
        operation.setMotif("Ouverture de caisse - " + savedCaisse.getNumeroCaisse());
        operation.setDateOperation(LocalDateTime.now());
        operation.setEstReglee(true);
        operationRepository.save(operation);

        return savedCaisse;
    }

    @Override
    @Transactional
    public Caisse fermerCaisse(Long utilisateurId) {
        Caisse caisse = getCaisseOuverte();
        return fermerCaisse(caisse.getId(), utilisateurId);
    }

    @Override
    @Transactional
    public Caisse fermerCaisse(Long caisseId, Long utilisateurId) {
        Caisse caisse = caisseRepository.findById(caisseId)
                .orElseThrow(() -> new RessourceIntrouvableException("Caisse non trouvée avec l'ID: " + caisseId));

        if (!caisse.isEstOuverte()) {
            throw new IllegalStateException("La caisse est déjà fermée");
        }

        caisse.setEstOuverte(false);
        caisse.setDateFermeture(LocalDateTime.now());
        caisse.setDerniereOperation(LocalDateTime.now());
        caisse.mettreAJourSoldeSysteme();

        Long nombreOps = operationRepository.countOperationsDuJourByCaisseId(caisseId);
        caisse.setNombreOperations(nombreOps != null ? nombreOps.intValue() : 0);

        if (utilisateurId != null) {
            utilisateurRepository.findById(utilisateurId).ifPresent(u ->
                    caisse.setUtilisateurVerification(u.getNomComplet()));
        }

        Double soldeAvantFermeture = caisse.getSoldeActuel();
        caisse.setSoldeActuel(0.0);
        caisse.setSoldeInitial(0.0);
        caisse.setSoldeSysteme(0.0);
        caisse.setSoldeReel(0.0);
        caisse.setEcart(0.0);
        caisse.setTotalEntrees(0.0);
        caisse.setTotalSorties(0.0);

        log.info("Fermeture de caisse - Solde avant fermeture: {}, Solde réinitialisé à 0", soldeAvantFermeture);

        Caisse savedCaisse = caisseRepository.save(caisse);

        OperationCaisse operation = new OperationCaisse();
        operation.setCaisse(savedCaisse);
        operation.setType(TypeOperationCaisse.FERMETURE);
        operation.setMontant(soldeAvantFermeture);
        operation.setSoldeAvant(soldeAvantFermeture);
        operation.setSoldeApres(0.0);
        operation.setMotif("Fermeture de caisse - " + caisse.getNumeroCaisse() + " - Montant final: " + soldeAvantFermeture);
        operation.setDateOperation(LocalDateTime.now());
        operation.setEstReglee(true);
        if (utilisateurId != null) {
            utilisateurRepository.findById(utilisateurId).ifPresent(operation::setUtilisateur);
        }
        operationRepository.save(operation);

        return savedCaisse;
    }

    @Override
    public Caisse getCaisseOuverte() {
        return verrouiller(caisseRepository.findCaisseOuverte()
                .orElseThrow(() -> new IllegalStateException("Aucune caisse n'est ouverte. Veuillez ouvrir une caisse")));
    }

    /**
     * Relit la caisse en la verrouillant jusqu'à la fin de la transaction. Sans ce verrou, deux
     * opérations simultanées (paiement groupé, double clic) partaient du même solde et la seconde
     * écrasait la première : de l'argent encaissé disparaissait du solde. Les modifications en
     * attente sont d'abord enregistrées pour ne pas être perdues par la relecture.
     */
    private Caisse verrouiller(Caisse caisse) {
        if (caisse != null && TransactionSynchronizationManager.isActualTransactionActive()
                && !TransactionSynchronizationManager.isCurrentTransactionReadOnly()
                && entityManager.contains(caisse)) {
            entityManager.flush();
            entityManager.refresh(caisse, LockModeType.PESSIMISTIC_WRITE);
        }
        return caisse;
    }

    @Override
    public Caisse getCaisseOuverte(Long caisseId) {
        Caisse caisse = caisseRepository.findById(caisseId)
                .orElseThrow(() -> new RessourceIntrouvableException("Caisse non trouvée avec l'ID: " + caisseId));
        if (!caisse.isEstOuverte()) {
            throw new IllegalStateException("La caisse " + caisse.getNumeroCaisse() + " n'est pas ouverte");
        }
        return caisse;
    }

    @Override
    public List<Caisse> obtenirToutesCaisses() {
        return caisseRepository.findAll();
    }

    @Override
    public boolean isCaisseOuverte() {
        return caisseRepository.findCaisseOuverte().isPresent();
    }

    // ==================== SOLDES ET VÉRIFICATIONS ====================

    @Override
    public Double getSoldeActuel() {
        return getCaisseOuverte().getSoldeActuel();
    }

    @Override
    public Double getSoldeSysteme() {
        return getCaisseOuverte().getSoldeSysteme();
    }

    @Override
    @Transactional
    public Caisse verifierCaisse(Double soldeReelSaisi, Long utilisateurId, String observations) {
        Caisse caisse = getCaisseOuverte();
        caisse.verifierCaisse(soldeReelSaisi, utilisateurId != null ? utilisateurId.toString() : "Système");
        caisse.mettreAJourSoldeSysteme();

        Caisse savedCaisse = caisseRepository.save(caisse);

        OperationCaisse operation = new OperationCaisse();
        operation.setCaisse(savedCaisse);
        operation.setType(TypeOperationCaisse.VERIFICATION);
        operation.setMontant(Math.abs(caisse.getEcart()));
        operation.setSoldeAvant(caisse.getSoldeSysteme());
        operation.setSoldeApres(caisse.getSoldeSysteme());
        operation.setMotif("Vérification de caisse - Écart: " + caisse.getEcart() +
                (observations != null ? " (" + observations + ")" : ""));
        operation.setDateOperation(LocalDateTime.now());
        operation.setEstReglee(true);
        if (utilisateurId != null) {
            utilisateurRepository.findById(utilisateurId).ifPresent(operation::setUtilisateur);
        }
        operationRepository.save(operation);

        return savedCaisse;
    }

    @Override
    public Map<String, Object> getEcartCaisse() {
        Caisse caisse = getCaisseOuverte();
        Map<String, Object> ecart = new HashMap<>();
        ecart.put("soldeSysteme", caisse.getSoldeSysteme());
        ecart.put("soldeReel", caisse.getSoldeReel());
        ecart.put("ecart", caisse.getEcart());
        ecart.put("verifiee", caisse.isVerifiee());
        ecart.put("dateVerification", caisse.getDateVerification());
        return ecart;
    }

    // ==================== OPÉRATIONS DE CAISSE ====================

    @Override
    @Transactional
    public OperationCaisse entreeCaisse(Double montant, String motif, Long utilisateurId,
                                        String modePaiement, String reference) {
        if (montant == null || montant <= 0) {
            throw new IllegalArgumentException("Le montant doit être supérieur à 0");
        }

        verifierEtOuvrirCaisseSiNecessaire();
        Caisse caisse = getCaisseOuverte();
        Double soldeAvant = caisse.getSoldeActuel();
        // Entrée reçue par Wave, Moov ou Orange Money (ex. avance client) : notée, mais rien
        // n'entre dans le tiroir.
        if (!estHorsCaisse(modePaiement)) {
            caisse.setSoldeActuel(soldeAvant + montant);
            caisse.setTotalEntrees(caisse.getTotalEntrees() + montant);
        }
        caisse.setDerniereOperation(LocalDateTime.now());
        caisseRepository.save(caisse);

        OperationCaisse operation = new OperationCaisse();
        operation.setCaisse(caisse);
        operation.setType(TypeOperationCaisse.ENTREE);
        operation.setMontant(montant);
        operation.setSoldeAvant(soldeAvant);
        operation.setSoldeApres(caisse.getSoldeActuel());
        operation.setMotif(motif);
        operation.setEstReglee(true);
        operation.setDateOperation(LocalDateTime.now());

        if (utilisateurId != null) {
            utilisateurRepository.findById(utilisateurId).ifPresent(operation::setUtilisateur);
        }

        if (modePaiement != null) {
            try {
                operation.setModePaiement(ModePaiementCaisse.valueOf(modePaiement));
            } catch (IllegalArgumentException e) {
                operation.setModePaiement(ModePaiementCaisse.ESPECES);
            }
        }
        operation.setReferencePaiement(reference);

        OperationCaisse saved = operationRepository.save(operation);
        notificationService.notifierOperationCaisse("ENTREE", Map.of(
                "montant", montant,
                "motif", motif != null ? motif : "",
                "soldeApres", caisse.getSoldeActuel()
        ));
        return saved;
    }

    @Override
    @Transactional
    public OperationCaisse sortieCaisse(Double montant, String motif, Long utilisateurId) {
        if (montant == null || montant <= 0) {
            throw new IllegalArgumentException("Le montant doit être supérieur à 0");
        }

        verifierEtOuvrirCaisseSiNecessaire();
        Caisse caisse = getCaisseOuverte();

        if (caisse.getSoldeActuel() < montant) {
            throw new SoldeInsuffisantException(
                    "Solde insuffisant. Disponible: " + caisse.getSoldeActuel() +
                            ", Demandé: " + montant);
        }

        Double soldeAvant = caisse.getSoldeActuel();

        caisse.setSoldeActuel(soldeAvant - montant);
        caisse.setTotalSorties(caisse.getTotalSorties() + montant);
        caisse.setDerniereOperation(LocalDateTime.now());
        caisseRepository.save(caisse);

        OperationCaisse operation = new OperationCaisse();
        operation.setCaisse(caisse);
        operation.setType(TypeOperationCaisse.SORTIE);
        operation.setMontant(montant);
        operation.setSoldeAvant(soldeAvant);
        operation.setSoldeApres(caisse.getSoldeActuel());
        operation.setMotif(motif);
        operation.setEstReglee(true);
        operation.setDateOperation(LocalDateTime.now());

        if (utilisateurId != null) {
            utilisateurRepository.findById(utilisateurId).ifPresent(operation::setUtilisateur);
        }

        OperationCaisse savedSortie = operationRepository.save(operation);
        notificationService.notifierOperationCaisse("SORTIE", Map.of(
                "montant", montant,
                "motif", motif != null ? motif : "",
                "soldeApres", caisse.getSoldeActuel()
        ));
        return savedSortie;
    }

    // ==================== METHODE POUR REMBOURSEMENT RETOUR ====================

    @Override
    @Transactional
    public OperationCaisse sortieCaisseRemboursementRetour(Double montant, String motif, Long utilisateurId) {
        if (montant == null || montant <= 0) {
            throw new IllegalArgumentException("Le montant doit être supérieur à 0");
        }

        verifierEtOuvrirCaisseSiNecessaire();
        Caisse caisse = getCaisseOuverte();

        if (caisse.getSoldeActuel() < montant) {
            throw new SoldeInsuffisantException(
                    "Solde insuffisant. Disponible: " + caisse.getSoldeActuel() +
                            ", Demandé: " + montant);
        }

        Double soldeAvant = caisse.getSoldeActuel();

        // Diminuer le solde - NE PAS ajouter à totalSorties
        caisse.setSoldeActuel(soldeAvant - montant);
        // IMPORTANT: NE PAS incrementer totalSorties
        caisse.setDerniereOperation(LocalDateTime.now());
        caisseRepository.save(caisse);

        OperationCaisse operation = new OperationCaisse();
        operation.setCaisse(caisse);
        operation.setType(TypeOperationCaisse.REMBOURSEMENT_RETOUR);
        operation.setMontant(montant);
        operation.setSoldeAvant(soldeAvant);
        operation.setSoldeApres(caisse.getSoldeActuel());
        operation.setMotif(motif != null ? motif : "Remboursement retour vente");
        operation.setEstReglee(true);
        operation.setDateOperation(LocalDateTime.now());

        if (utilisateurId != null) {
            utilisateurRepository.findById(utilisateurId).ifPresent(operation::setUtilisateur);
        }

        log.info("Remboursement retour enregistré: {} F, solde avant: {}, solde après: {} (non compté dans totalSorties)",
                montant, soldeAvant, caisse.getSoldeActuel());
        return operationRepository.save(operation);
    }

    // ==================== METHODES POUR FOURNISSEURS ====================

    @Override
    @Transactional
    public OperationCaisse sortieCaisseFournisseur(Double montant, String motif, Long utilisateurId) {
        if (montant == null || montant <= 0) {
            throw new IllegalArgumentException("Le montant doit être supérieur à 0");
        }

        verifierEtOuvrirCaisseSiNecessaire();
        Caisse caisse = getCaisseOuverte();

        if (caisse.getSoldeActuel() < montant) {
            throw new SoldeInsuffisantException(
                    "Solde insuffisant. Disponible: " + caisse.getSoldeActuel() +
                            ", Demandé: " + montant);
        }

        Double soldeAvant = caisse.getSoldeActuel();

        caisse.setSoldeActuel(soldeAvant - montant);
        caisse.setTotalSorties(caisse.getTotalSorties() + montant);
        caisse.setDerniereOperation(LocalDateTime.now());
        caisseRepository.save(caisse);

        OperationCaisse operation = new OperationCaisse();
        operation.setCaisse(caisse);
        operation.setType(TypeOperationCaisse.PAIEMENT_FOURNISSEUR);
        operation.setMontant(montant);
        operation.setSoldeAvant(soldeAvant);
        operation.setSoldeApres(caisse.getSoldeActuel());
        operation.setMotif(motif);
        operation.setEstReglee(true);
        operation.setDateOperation(LocalDateTime.now());

        if (utilisateurId != null) {
            utilisateurRepository.findById(utilisateurId).ifPresent(operation::setUtilisateur);
        }

        log.info("Paiement fournisseur enregistré: {} F", montant);
        return operationRepository.save(operation);
    }

    @Override
    @Transactional
    public OperationCaisse sortieCaisseAvance(Double montant, String motif, Long utilisateurId) {
        if (montant == null || montant <= 0) {
            throw new IllegalArgumentException("Le montant doit être supérieur à 0");
        }

        verifierEtOuvrirCaisseSiNecessaire();
        Caisse caisse = getCaisseOuverte();

        if (caisse.getSoldeActuel() < montant) {
            throw new SoldeInsuffisantException(
                    "Solde insuffisant. Disponible: " + caisse.getSoldeActuel() +
                            ", Demandé: " + montant);
        }

        Double soldeAvant = caisse.getSoldeActuel();

        caisse.setSoldeActuel(soldeAvant - montant);
        caisse.setTotalSorties(caisse.getTotalSorties() + montant);
        caisse.setDerniereOperation(LocalDateTime.now());
        caisseRepository.save(caisse);

        OperationCaisse operation = new OperationCaisse();
        operation.setCaisse(caisse);
        operation.setType(TypeOperationCaisse.AVANCE_FOURNISSEUR);
        operation.setMontant(montant);
        operation.setSoldeAvant(soldeAvant);
        operation.setSoldeApres(caisse.getSoldeActuel());
        operation.setMotif(motif);
        operation.setEstReglee(true);
        operation.setDateOperation(LocalDateTime.now());

        if (utilisateurId != null) {
            utilisateurRepository.findById(utilisateurId).ifPresent(operation::setUtilisateur);
        }

        log.info("Avance fournisseur enregistrée: {} F", montant);
        return operationRepository.save(operation);
    }

    // ==================== PAIEMENTS EMPLOYES ====================

    @Override
    @Transactional
    public OperationCaisse sortieCaisseEmploye(Double montant, String motif, Long utilisateurId) {
        if (montant == null || montant <= 0)
            throw new IllegalArgumentException("Le montant doit être supérieur à 0");

        verifierEtOuvrirCaisseSiNecessaire();
        Caisse caisse = getCaisseOuverte();

        if (caisse.getSoldeActuel() < montant)
            throw new SoldeInsuffisantException(
                    "Solde insuffisant. Disponible: " + caisse.getSoldeActuel() + ", Demandé: " + montant);

        Double soldeAvant = caisse.getSoldeActuel();
        caisse.setSoldeActuel(soldeAvant - montant);
        caisse.setDerniereOperation(LocalDateTime.now());
        caisseRepository.save(caisse);

        OperationCaisse operation = new OperationCaisse();
        operation.setCaisse(caisse);
        operation.setType(TypeOperationCaisse.PAIEMENT_EMPLOYE);
        operation.setMontant(montant);
        operation.setSoldeAvant(soldeAvant);
        operation.setSoldeApres(caisse.getSoldeActuel());
        operation.setMotif(motif);
        operation.setEstReglee(true);
        operation.setDateOperation(LocalDateTime.now());

        if (utilisateurId != null)
            utilisateurRepository.findById(utilisateurId).ifPresent(operation::setUtilisateur);

        log.info("Paiement employé: {} F", montant);
        return operationRepository.save(operation);
    }

    @Override
    @Transactional
    public OperationCaisse retourCaisseEmploye(Double montant, String motif, Long utilisateurId) {
        if (montant == null || montant <= 0)
            throw new IllegalArgumentException("Le montant doit être supérieur à 0");

        verifierEtOuvrirCaisseSiNecessaire();
        Caisse caisse = getCaisseOuverte();

        Double soldeAvant = caisse.getSoldeActuel();
        caisse.setSoldeActuel(soldeAvant + montant);
        caisse.setDerniereOperation(LocalDateTime.now());
        caisseRepository.save(caisse);

        OperationCaisse operation = new OperationCaisse();
        operation.setCaisse(caisse);
        operation.setType(TypeOperationCaisse.ANNULATION_PAIEMENT_EMPLOYE);
        operation.setMontant(montant);
        operation.setSoldeAvant(soldeAvant);
        operation.setSoldeApres(caisse.getSoldeActuel());
        operation.setMotif(motif);
        operation.setEstReglee(true);
        operation.setDateOperation(LocalDateTime.now());

        if (utilisateurId != null)
            utilisateurRepository.findById(utilisateurId).ifPresent(operation::setUtilisateur);

        log.info("Retour caisse annulation employé: {} F", montant);
        return operationRepository.save(operation);
    }

    // ==================== DÉPENSES ====================

    @Override
    @Transactional
    public OperationCaisse sortieCaisseDepense(Double montant, String motif, Long utilisateurId) {
        if (montant == null || montant <= 0)
            throw new IllegalArgumentException("Le montant doit être supérieur à 0");

        verifierEtOuvrirCaisseSiNecessaire();
        Caisse caisse = getCaisseOuverte();

        if (caisse.getSoldeActuel() < montant)
            throw new SoldeInsuffisantException("Solde insuffisant. Disponible: " + caisse.getSoldeActuel() + ", Demandé: " + montant);

        Double soldeAvant = caisse.getSoldeActuel();
        caisse.setSoldeActuel(soldeAvant - montant);
        caisse.setTotalSorties(caisse.getTotalSorties() + montant);
        caisse.setDerniereOperation(LocalDateTime.now());
        caisseRepository.save(caisse);

        OperationCaisse operation = new OperationCaisse();
        operation.setCaisse(caisse);
        operation.setType(TypeOperationCaisse.DEPENSE);
        operation.setMontant(montant);
        operation.setSoldeAvant(soldeAvant);
        operation.setSoldeApres(caisse.getSoldeActuel());
        operation.setMotif(motif);
        operation.setEstReglee(true);
        operation.setDateOperation(LocalDateTime.now());

        if (utilisateurId != null)
            utilisateurRepository.findById(utilisateurId).ifPresent(operation::setUtilisateur);

        log.info("Dépense enregistrée: {} F - {}", montant, motif);
        return operationRepository.save(operation);
    }

    @Override
    @Transactional
    public OperationCaisse entreeCaisseDepense(Double montant, String motif, Long utilisateurId) {
        if (montant == null || montant <= 0)
            throw new IllegalArgumentException("Le montant doit être supérieur à 0");

        verifierEtOuvrirCaisseSiNecessaire();
        Caisse caisse = getCaisseOuverte();

        Double soldeAvant = caisse.getSoldeActuel();
        caisse.setSoldeActuel(soldeAvant + montant);
        caisse.setDerniereOperation(LocalDateTime.now());
        caisseRepository.save(caisse);

        OperationCaisse operation = new OperationCaisse();
        operation.setCaisse(caisse);
        operation.setType(TypeOperationCaisse.DEPENSE);
        operation.setMontant(montant);
        operation.setSoldeAvant(soldeAvant);
        operation.setSoldeApres(caisse.getSoldeActuel());
        operation.setMotif("Ajustement dépense: " + motif);
        operation.setEstReglee(true);
        operation.setDateOperation(LocalDateTime.now());

        if (utilisateurId != null)
            utilisateurRepository.findById(utilisateurId).ifPresent(operation::setUtilisateur);

        log.info("Ajustement dépense (retour caisse): {} F", montant);
        return operationRepository.save(operation);
    }

    // ==================== VENTES ====================

    /** Wave, Moov, Orange Money : l'argent arrive sur le compte mobile money, jamais dans le tiroir. */
    private static boolean estHorsCaisse(String modePaiement) {
        return modePaiement != null &&
                (modePaiement.equals("ORANGE_MONEY") || modePaiement.equals("MOOV_MONEY") || modePaiement.equals("WAVE_MONEY"));
    }

    /** Ce qu'une opération a réellement ajouté au tiroir (0 pour un paiement mobile money). */
    private static double ajouteAuTiroir(OperationCaisse op) {
        return Math.max(0.0, (op.getSoldeApres() != null ? op.getSoldeApres() : 0.0)
                - (op.getSoldeAvant() != null ? op.getSoldeAvant() : 0.0));
    }

    @Override
    @Transactional
    public OperationCaisse enregistrerVente(Vente vente, Long utilisateurId,
                                            String modePaiement, String reference) {
        if (vente == null) {
            throw new IllegalArgumentException("La vente ne peut pas être nulle");
        }

        verifierEtOuvrirCaisseSiNecessaire();
        Caisse caisse = getCaisseOuverte();
        Double soldeAvant = caisse.getSoldeActuel();

        // Orange Money, Moov Money et Wave ne touchent pas au solde de la caisse
        // (l'argent ne rentre pas physiquement dans le tiroir-caisse)
        boolean estMobileMoney = estHorsCaisse(modePaiement);

        if (!estMobileMoney) {
            caisse.setSoldeActuel(soldeAvant + vente.getMontantTotal());
            caisse.setTotalEntrees(caisse.getTotalEntrees() + vente.getMontantTotal());
        }
        caisse.setDerniereOperation(LocalDateTime.now());
        caisseRepository.save(caisse);

        OperationCaisse operation = new OperationCaisse();
        operation.setCaisse(caisse);
        operation.setType(TypeOperationCaisse.VENTE_COMPTANT);
        operation.setMontant(vente.getMontantTotal());
        operation.setSoldeAvant(soldeAvant);
        operation.setSoldeApres(estMobileMoney ? soldeAvant : caisse.getSoldeActuel());
        operation.setMotif("Vente N°" + vente.getNumeroVente());
        operation.setVente(vente);
        operation.setEstReglee(true);
        operation.setDateOperation(LocalDateTime.now());

        if (utilisateurId != null) {
            utilisateurRepository.findById(utilisateurId).ifPresent(operation::setUtilisateur);
        }

        if (modePaiement != null) {
            try {
                operation.setModePaiement(ModePaiementCaisse.valueOf(modePaiement));
            } catch (IllegalArgumentException e) {
                operation.setModePaiement(ModePaiementCaisse.ESPECES);
            }
        }
        operation.setReferencePaiement(reference);

        return operationRepository.save(operation);
    }

    @Override
    @Transactional
    public OperationCaisse enregistrerVenteCredit(Vente vente, Long utilisateurId,
                                                  String clientNom, String clientTelephone,
                                                  LocalDate dateEcheance) {
        if (vente == null) {
            throw new IllegalArgumentException("La vente ne peut pas être nulle");
        }

        verifierEtOuvrirCaisseSiNecessaire();
        Caisse caisse = getCaisseOuverte();
        Double soldeAvant = caisse.getSoldeActuel();
        Double montantVerseInitial = vente.getMontantVerse() != null ? vente.getMontantVerse() : 0.0;
        Double montantRestant = Math.max(0.0, vente.getMontantTotal() - montantVerseInitial);

        OperationCaisse operation = new OperationCaisse();
        operation.setCaisse(caisse);
        operation.setType(TypeOperationCaisse.VENTE_CREDIT);
        operation.setMontant(vente.getMontantTotal());
        operation.setSoldeAvant(soldeAvant);
        operation.setSoldeApres(soldeAvant);
        operation.setMotif("Vente à crédit N°" + vente.getNumeroVente());
        operation.setVente(vente);
        operation.setEstReglee(montantRestant <= 0);
        operation.setClientNom(clientNom != null ? clientNom : vente.getClientNom());
        operation.setClientTelephone(clientTelephone != null ? clientTelephone : vente.getClientTelephone());
        operation.setDateOperation(LocalDateTime.now());
        operation.setMontantVerse(montantVerseInitial);
        operation.setMontantRestant(montantRestant);
        operation.setVenteCreditId(vente.getId());

        if (dateEcheance != null) {
            operation.setDateEcheance(dateEcheance.atTime(LocalTime.MAX));
        } else {
            operation.setDateEcheance(LocalDateTime.now().plusDays(30));
        }

        if (utilisateurId != null) {
            utilisateurRepository.findById(utilisateurId).ifPresent(operation::setUtilisateur);
        }

        OperationCaisse savedOperation = operationRepository.save(operation);

        // BUG FIX : si un acompte initial EN CASH a été versé à la création du crédit,
        // l'enregistrer comme REGLEMENT_CREDIT dans la caisse (argent réellement reçu).
        // L'éventuelle avance client (montantAvanceUtilise) est EXCLUE : elle a déjà été
        // comptabilisée en caisse lors du dépôt initial (enregistrerAvance → entreeCaisse).
        double avanceUtilisee = vente.getMontantAvanceUtilise() != null ? vente.getMontantAvanceUtilise() : 0.0;
        double montantCashVerse = montantVerseInitial - avanceUtilisee;

        if (montantCashVerse > 0) {
            // Acompte payé par Wave, Moov ou Orange Money : enregistré pour le suivi du crédit,
            // mais rien n'entre dans le tiroir.
            boolean acompteHorsCaisse = vente.getModePaiement() != null && estHorsCaisse(vente.getModePaiement().name());
            if (!acompteHorsCaisse) {
                caisse.setSoldeActuel(soldeAvant + montantCashVerse);
                caisse.setTotalEntrees(caisse.getTotalEntrees() + montantCashVerse);
            }
            caisse.setDerniereOperation(LocalDateTime.now());
            caisseRepository.save(caisse);

            String nomClient = clientNom != null ? clientNom
                    : (vente.getClientNom() != null ? vente.getClientNom() : "");

            OperationCaisse acompte = new OperationCaisse();
            acompte.setCaisse(caisse);
            acompte.setType(TypeOperationCaisse.REGLEMENT_CREDIT);
            acompte.setMontant(montantCashVerse);
            acompte.setSoldeAvant(soldeAvant);
            acompte.setSoldeApres(caisse.getSoldeActuel());
            acompte.setMotif("Acompte initial - " + nomClient + " - Vente N°" + vente.getNumeroVente());
            acompte.setVente(vente);
            acompte.setEstReglee(true);
            acompte.setDateOperation(LocalDateTime.now());
            acompte.setMontantVerse(montantCashVerse);
            acompte.setMontantRestant(montantRestant);
            acompte.setVenteCreditId(vente.getId());
            // BUG FIX : sans ce champ, cette opération de REGLEMENT_CREDIT restait avec un
            // modePaiement null et le rapport de réconciliation caisse (getReconciliationVendeurs,
            // filtre op.getModePaiement() == ESPECES) l'ignorait silencieusement — l'acompte
            // rentrait bien dans le solde physique de la caisse mais n'était jamais réclamé au
            // vendeur dans le rapport. On reprend le mode de paiement réel de la vente.
            acompte.setModePaiement(vente.getModePaiement() != null
                    ? ModePaiementCaisse.valueOf(vente.getModePaiement().name())
                    : ModePaiementCaisse.ESPECES);
            acompte.setClientNom(nomClient);
            acompte.setClientTelephone(clientTelephone != null ? clientTelephone : vente.getClientTelephone());
            if (utilisateurId != null) {
                utilisateurRepository.findById(utilisateurId).ifPresent(acompte::setUtilisateur);
            }
            operationRepository.save(acompte);
            log.info("Acompte initial cash enregistré en caisse : {} F (avance exclue: {} F) - client: {} - vente: {}",
                    montantCashVerse, avanceUtilisee, nomClient, vente.getNumeroVente());
        }

        return savedOperation;
    }

    @Override
    @Transactional
    public OperationCaisse reglementCredit(Long venteCreditId, Double montantRegle,
                                           Long utilisateurId, String modePaiement,
                                           String reference, String motif, String referenceGroupe) {
        Vente vente = venteRepository.findById(venteCreditId)
                .orElseThrow(() -> new RessourceIntrouvableException("Vente non trouvée avec l'ID: " + venteCreditId));

        if (!Boolean.TRUE.equals(vente.getEstCredit())) {
            throw new IllegalArgumentException("La vente avec l'ID " + venteCreditId + " n'est pas un crédit");
        }

        if (Boolean.TRUE.equals(vente.getCreditRegle())) {
            throw new IllegalStateException("Ce crédit est déjà totalement réglé");
        }

        if (Boolean.TRUE.equals(vente.getAnnulee())) {
            throw new IllegalStateException("Ce crédit a été annulé et ne peut plus être réglé");
        }

        if (montantRegle == null || montantRegle <= 0) {
            throw new IllegalArgumentException("Le montant réglé doit être supérieur à 0");
        }

        Double montantRestantActuel = vente.getMontantRestant();
        if (montantRegle > montantRestantActuel) {
            throw new IllegalArgumentException("Le montant réglé ne peut pas dépasser le montant restant (" +
                    montantRestantActuel + ")");
        }

        verifierEtOuvrirCaisseSiNecessaire();
        Caisse caisse = getCaisseOuverte();
        Double soldeAvant = caisse.getSoldeActuel();

        // Règlement payé par Wave, Moov ou Orange Money : la dette baisse, mais rien n'entre dans le tiroir.
        if (!estHorsCaisse(modePaiement)) {
            caisse.setSoldeActuel(soldeAvant + montantRegle);
            caisse.setTotalEntrees(caisse.getTotalEntrees() + montantRegle);
        }
        caisse.setDerniereOperation(LocalDateTime.now());
        caisseRepository.save(caisse);

        Double nouveauMontantVerse = vente.getMontantVerse() + montantRegle;
        Double nouveauMontantRestant = vente.getMontantRestant() - montantRegle;

        vente.setMontantVerse(nouveauMontantVerse);
        vente.setMontantRestant(nouveauMontantRestant);

        if (nouveauMontantRestant <= 0) {
            vente.setCreditRegle(true);
            vente.setDateReglement(LocalDate.now());
        }
        venteRepository.save(vente);

        Optional<OperationCaisse> creditOperationOpt = operationRepository.findFirstByVenteIdAndType(
                vente.getId(), TypeOperationCaisse.VENTE_CREDIT);

        if (creditOperationOpt.isPresent()) {
            OperationCaisse creditOperation = creditOperationOpt.get();
            creditOperation.setMontantVerse(nouveauMontantVerse);
            creditOperation.setMontantRestant(nouveauMontantRestant);
            creditOperation.setEstReglee(nouveauMontantRestant <= 0);
            operationRepository.save(creditOperation);
        }

        OperationCaisse reglementOperation = new OperationCaisse();
        reglementOperation.setCaisse(caisse);
        reglementOperation.setType(TypeOperationCaisse.REGLEMENT_CREDIT);
        reglementOperation.setMontant(montantRegle);
        reglementOperation.setSoldeAvant(soldeAvant);
        reglementOperation.setSoldeApres(caisse.getSoldeActuel());
        String motifFinal = (motif != null && !motif.isBlank())
                ? motif
                : "Règlement crédit - Vente N°" + vente.getNumeroVente();
        reglementOperation.setMotif(motifFinal);
        reglementOperation.setVente(vente);
        reglementOperation.setEstReglee(true);
        reglementOperation.setDateOperation(LocalDateTime.now());
        reglementOperation.setMontantVerse(montantRegle);
        reglementOperation.setMontantRestant(nouveauMontantRestant);
        reglementOperation.setVenteCreditId(venteCreditId);
        reglementOperation.setClientNom(vente.getClientNom());
        reglementOperation.setClientTelephone(vente.getClientTelephone());

        if (utilisateurId != null) {
            utilisateurRepository.findById(utilisateurId).ifPresent(reglementOperation::setUtilisateur);
        }

        if (modePaiement != null) {
            try {
                reglementOperation.setModePaiement(ModePaiementCaisse.valueOf(modePaiement));
            } catch (IllegalArgumentException e) {
                reglementOperation.setModePaiement(ModePaiementCaisse.ESPECES);
            }
        }
        reglementOperation.setReferencePaiement(reference);
        reglementOperation.setReferenceGroupe(referenceGroupe);

        return operationRepository.save(reglementOperation);
    }

    // ==================== ANNULATIONS ====================

    @Override
    @Transactional
    public OperationCaisse annulerVente(Vente vente, Long utilisateurId, String motif) {
        return annulerVenteAvecRepercussion(vente, utilisateurId, motif);
    }

    @Override
    @Transactional
    public OperationCaisse annulerVenteCredit(Vente vente, Long utilisateurId, String motif) {
        return annulerVenteCreditAvecRepercussion(vente, utilisateurId, motif);
    }

    @Override
    @Transactional
    public OperationCaisse annulerVenteAvecRepercussion(Vente vente, Long utilisateurId, String motif) {
        log.info("=== ANNULATION VENTE AVEC REPERCUSSION CAISSE ===");

        if (Boolean.TRUE.equals(vente.getEstCredit())) {
            return annulerVenteCreditAvecRepercussion(vente, utilisateurId, motif);
        }

        if (Boolean.TRUE.equals(vente.getAnnulee())) {
            log.warn("La vente {} est déjà annulée", vente.getNumeroVente());
            Optional<OperationCaisse> existingOp = operationRepository.findOperationVenteByVenteIdAndType(
                    vente.getId(), TypeOperationCaisse.ANNULATION_VENTE);
            if (existingOp.isPresent()) {
                return existingOp.get();
            }
            throw new IllegalStateException("Cette vente est déjà annulée");
        }

        verifierEtOuvrirCaisseSiNecessaire();
        Caisse caisse = getCaisseOuverte();

        Optional<OperationCaisse> venteOperationOpt = operationRepository.findOperationVenteByVenteIdAndType(
                vente.getId(), TypeOperationCaisse.VENTE_COMPTANT);

        if (venteOperationOpt.isEmpty()) {
            throw new IllegalStateException("Opération de vente non trouvée en caisse");
        }

        OperationCaisse venteOperation = venteOperationOpt.get();

        // Déjà annulée en caisse (ex. appel en double) : ne pas retirer l'argent une 2e fois.
        if (Boolean.TRUE.equals(venteOperation.getVenteAnnulee())) {
            Optional<OperationCaisse> dejaFaite = operationRepository.findOperationVenteByVenteIdAndType(
                    vente.getId(), TypeOperationCaisse.ANNULATION_VENTE);
            if (dejaFaite.isPresent()) {
                log.warn("Annulation en double ignorée pour la vente {}", vente.getNumeroVente());
                return dejaFaite.get();
            }
        }

        Double soldeAvant = caisse.getSoldeActuel();
        Double montantVente = vente.getMontantTotal();

        // L'annulation retire du tiroir exactement ce que la vente y avait mis : une vente payée
        // par Wave, Moov, Orange Money… n'a rien ajouté au tiroir (l'argent est sur le compte
        // mobile money), son annulation ne doit donc rien en retirer. Avant, le montant total
        // était retiré dans tous les cas → caisse plus basse que l'argent réel.
        double entreDansLeTiroir = Math.max(0.0,
                (venteOperation.getSoldeApres() != null ? venteOperation.getSoldeApres() : 0.0)
                        - (venteOperation.getSoldeAvant() != null ? venteOperation.getSoldeAvant() : 0.0));

        if (caisse.getSoldeActuel() < entreDansLeTiroir) {
            throw new SoldeInsuffisantException("Solde insuffisant pour annuler la vente");
        }

        if (entreDansLeTiroir > 0) {
            caisse.setSoldeActuel(soldeAvant - entreDansLeTiroir);
            caisse.setTotalSorties(caisse.getTotalSorties() + entreDansLeTiroir);
        }
        caisse.setDerniereOperation(LocalDateTime.now());
        caisseRepository.save(caisse);

        OperationCaisse operation = new OperationCaisse();
        operation.setCaisse(caisse);
        operation.setType(TypeOperationCaisse.ANNULATION_VENTE);
        operation.setMontant(montantVente);
        operation.setSoldeAvant(soldeAvant);
        operation.setSoldeApres(caisse.getSoldeActuel());
        String motifAnnulation = motif != null ? "ANNULATION VENTE " + vente.getNumeroVente() + " - " + motif :
                "Annulation vente N°" + vente.getNumeroVente();
        if (entreDansLeTiroir < montantVente) {
            motifAnnulation += entreDansLeTiroir > 0
                    ? " (" + Math.round(entreDansLeTiroir) + " F retirés du tiroir, le reste payé hors caisse)"
                    : " (payée hors caisse : rien retiré du tiroir)";
        }
        operation.setMotif(motifAnnulation);
        operation.setVente(vente);
        operation.setEstReglee(true);
        operation.setDateOperation(LocalDateTime.now());
        operation.setVenteAnnulee(true);

        if (utilisateurId != null) {
            utilisateurRepository.findById(utilisateurId).ifPresent(operation::setUtilisateur);
        }

        OperationCaisse savedOperation = operationRepository.save(operation);

        venteOperation.setVenteAnnulee(true);
        operationRepository.save(venteOperation);

        return savedOperation;
    }

    @Override
    @Transactional
    public OperationCaisse annulerVenteCreditAvecRepercussion(Vente vente, Long utilisateurId, String motif) {
        log.info("=== ANNULATION CREDIT AVEC REPERCUSSION CAISSE ===");

        if (!Boolean.TRUE.equals(vente.getEstCredit())) {
            return annulerVenteAvecRepercussion(vente, utilisateurId, motif);
        }

        // Crédit déjà annulé en caisse : ne pas rembourser une 2e fois (avant, chaque nouvel appui
        // sur « Annuler » remboursait de nouveau le client depuis le tiroir).
        Optional<OperationCaisse> annulationExistante = operationRepository.findFirstByVenteIdAndType(
                vente.getId(), TypeOperationCaisse.ANNULATION_CREDIT);
        if (annulationExistante.isPresent()) {
            log.warn("Annulation de crédit en double ignorée pour la vente {}", vente.getNumeroVente());
            return annulationExistante.get();
        }

        if (Boolean.TRUE.equals(vente.getAnnulee())) {
            log.warn("Le crédit {} est déjà annulé", vente.getNumeroVente());
            Optional<OperationCaisse> existingOp = operationRepository.findFirstByVenteIdAndType(
                    vente.getId(), TypeOperationCaisse.ANNULATION_CREDIT);
            if (existingOp.isPresent()) {
                return existingOp.get();
            }
            throw new IllegalStateException("Ce crédit est déjà annulé");
        }

        verifierEtOuvrirCaisseSiNecessaire();
        Caisse caisse = getCaisseOuverte();

        // Chercher l'opération VENTE_CREDIT — peut être absente pour les commandes créées avant le fix
        Optional<OperationCaisse> creditOperationOpt = operationRepository.findFirstByVenteIdAndType(
                vente.getId(), TypeOperationCaisse.VENTE_CREDIT);

        // Si pas d'opération VENTE_CREDIT, chercher une VENTE_COMPTANT (commandes validées avant le fix)
        if (creditOperationOpt.isEmpty()) {
            creditOperationOpt = operationRepository.findFirstByVenteIdAndType(
                    vente.getId(), TypeOperationCaisse.VENTE_COMPTANT);
            log.warn("Aucune opération VENTE_CREDIT pour la vente {} — utilisation de VENTE_COMPTANT comme fallback", vente.getNumeroVente());
        }

        Double soldeAvant = caisse.getSoldeActuel();

        OperationCaisse operation = new OperationCaisse();
        operation.setCaisse(caisse);
        operation.setType(TypeOperationCaisse.ANNULATION_CREDIT);
        operation.setMontant(vente.getMontantTotal());
        operation.setSoldeAvant(soldeAvant);
        operation.setSoldeApres(soldeAvant);
        operation.setMotif(motif != null ? "ANNULATION CREDIT " + vente.getNumeroVente() + " - " + motif :
                "Annulation crédit N°" + vente.getNumeroVente());
        operation.setVente(vente);
        operation.setEstReglee(true);
        operation.setDateOperation(LocalDateTime.now());
        operation.setClientNom(vente.getClientNom());
        operation.setClientTelephone(vente.getClientTelephone());
        operation.setVenteCreditId(vente.getId());
        operation.setMontantVerse(vente.getMontantVerse());
        operation.setMontantRestant(vente.getMontantRestant());
        operation.setVenteAnnulee(true);

        if (utilisateurId != null) {
            utilisateurRepository.findById(utilisateurId).ifPresent(operation::setUtilisateur);
        }

        OperationCaisse savedOperation = operationRepository.save(operation);

        // Marquer l'opération originale (VENTE_CREDIT ou VENTE_COMPTANT) comme annulée
        if (creditOperationOpt.isPresent()) {
            creditOperationOpt.get().setVenteAnnulee(true);
            operationRepository.save(creditOperationOpt.get());
        }

        List<OperationCaisse> reglements = operationRepository.findReglementsByVenteCredit(vente.getId());
        double verseDansLeTiroir = 0.0;
        boolean reglementsTrouves = false;
        for (OperationCaisse reglement : reglements) {
            if (reglement.getType() == TypeOperationCaisse.REGLEMENT_CREDIT && !reglement.isAnnule()) {
                reglementsTrouves = true;
                verseDansLeTiroir += ajouteAuTiroir(reglement);
            }
            reglement.setVenteAnnulee(true);
            operationRepository.save(reglement);
        }

        // Si le client a déjà versé de l'argent → le rembourser depuis le tiroir, mais seulement la
        // part entrée dans le tiroir : ni ce qui a été payé en Wave/Moov/Orange Money, ni l'avance
        // du client (rendue sur son compte d'avance par VenteServiceImpl). Anciens crédits dont les
        // règlements ne sont pas retrouvés : calcul d'avant (montant versé hors avance).
        double montantVerse = reglementsTrouves
                ? verseDansLeTiroir
                : Math.max(0.0, (vente.getMontantVerse() != null ? vente.getMontantVerse() : 0.0)
                        - (vente.getMontantAvanceUtilise() != null ? vente.getMontantAvanceUtilise() : 0.0));
        if (montantVerse > 0) {
            log.info("Remboursement de {} FCFA au client {} suite à annulation crédit {}", montantVerse, vente.getClientNom(), vente.getNumeroVente());
            double soldeAvantRemb = caisse.getSoldeActuel();
            caisse.setSoldeActuel(soldeAvantRemb - montantVerse);
            caisse.setTotalSorties(caisse.getTotalSorties() + montantVerse);
            caisse.setDerniereOperation(LocalDateTime.now());
            caisseRepository.save(caisse);

            OperationCaisse remboursement = new OperationCaisse();
            remboursement.setCaisse(caisse);
            remboursement.setType(TypeOperationCaisse.REMBOURSEMENT_COMMANDE);
            remboursement.setMontant(montantVerse);
            remboursement.setSoldeAvant(soldeAvantRemb);
            remboursement.setSoldeApres(caisse.getSoldeActuel());
            remboursement.setMotif("Remboursement client " + (vente.getClientNom() != null ? vente.getClientNom() : "") +
                    " — annulation commande/crédit N°" + vente.getNumeroVente());
            remboursement.setVente(vente);
            remboursement.setClientNom(vente.getClientNom());
            remboursement.setClientTelephone(vente.getClientTelephone());
            remboursement.setDateOperation(LocalDateTime.now());
            remboursement.setEstReglee(true);
            remboursement.setVenteAnnulee(true);
            if (utilisateurId != null) {
                utilisateurRepository.findById(utilisateurId).ifPresent(remboursement::setUtilisateur);
            }
            operationRepository.save(remboursement);
        }

        return savedOperation;
    }

    // ==================== GESTION DES CREDITS ====================

    @Override
    public List<OperationCaisse> getCreditsNonRegles() {
        List<OperationCaisse> credits = operationRepository.findCreditsNonRegles(TypeOperationCaisse.VENTE_CREDIT);
        List<OperationCaisse> creditsFiltres = new ArrayList<>();
        for (OperationCaisse credit : credits) {
            if (credit.getVente() != null && Boolean.TRUE.equals(credit.getVente().getAnnulee())) {
                continue;
            }
            if (credit.getVente() != null) {
                credit.setMontantVerse(credit.getVente().getMontantVerse());
                credit.setMontantRestant(credit.getVente().getMontantRestant());
                credit.setEstReglee(Boolean.TRUE.equals(credit.getVente().getCreditRegle()));
            }
            creditsFiltres.add(credit);
        }
        return creditsFiltres;
    }

    @Override
    public List<OperationCaisse> getCreditsEnRetard() {
        List<OperationCaisse> credits = operationRepository.findCreditsEnRetard(LocalDateTime.now());
        List<OperationCaisse> creditsFiltres = new ArrayList<>();
        for (OperationCaisse credit : credits) {
            if (credit.getVente() != null && Boolean.TRUE.equals(credit.getVente().getAnnulee())) {
                continue;
            }
            if (credit.getVente() != null) {
                credit.setMontantVerse(credit.getVente().getMontantVerse());
                credit.setMontantRestant(credit.getVente().getMontantRestant());
                credit.setEstReglee(Boolean.TRUE.equals(credit.getVente().getCreditRegle()));
            }
            creditsFiltres.add(credit);
        }
        return creditsFiltres;
    }

    @Override
    public Map<String, Object> getSituationCredits() {
        Map<String, Object> situation = new HashMap<>();
        List<OperationCaisse> creditsNonRegles = getCreditsNonRegles();
        List<OperationCaisse> creditsEnRetard = getCreditsEnRetard();

        Double montantTotalCredits = creditsNonRegles.stream().mapToDouble(OperationCaisse::getMontant).sum();
        Double montantRestantTotal = creditsNonRegles.stream().mapToDouble(c -> c.getMontantRestant() != null ? c.getMontantRestant() : c.getMontant()).sum();
        Double montantTotalRetard = creditsEnRetard.stream().mapToDouble(c -> c.getMontantRestant() != null ? c.getMontantRestant() : c.getMontant()).sum();

        situation.put("nombreCreditsNonRegles", creditsNonRegles.size());
        situation.put("montantTotalCredits", arrondir(montantTotalCredits));
        situation.put("montantRestantTotal", arrondir(montantRestantTotal));
        situation.put("nombreCreditsEnRetard", creditsEnRetard.size());
        situation.put("montantTotalRetard", arrondir(montantTotalRetard));

        return situation;
    }

    @Override
    public List<OperationCaisse> getHistoriqueReglementsCredit(Long venteCreditId) {
        return operationRepository.findReglementsByVenteCredit(venteCreditId);
    }

    // ==================== OPERATIONS ====================

    @Override
    public List<OperationCaisse> getOperationsDuJour() {
        return operationRepository.findOperationsDuJour().stream()
                .filter(op -> !Boolean.TRUE.equals(op.getVenteAnnulee()))
                .toList();
    }

    @Override
    public List<OperationCaisse> getOperationsDeLaSemaine() {
        LocalDateTime debutSemaine = LocalDate.now().with(TemporalAdjusters.previousOrSame(java.time.DayOfWeek.MONDAY)).atStartOfDay();
        LocalDateTime finSemaine = LocalDate.now().with(TemporalAdjusters.nextOrSame(java.time.DayOfWeek.SUNDAY)).atTime(LocalTime.MAX);
        return operationRepository.findOperationsParPeriode(debutSemaine, finSemaine).stream()
                .filter(op -> !Boolean.TRUE.equals(op.getVenteAnnulee()))
                .toList();
    }

    @Override
    public List<OperationCaisse> getOperationsDuMois() {
        LocalDate aujourdhui = LocalDate.now();
        LocalDateTime debutMois = aujourdhui.withDayOfMonth(1).atStartOfDay();
        LocalDateTime finMois = aujourdhui.withDayOfMonth(aujourdhui.lengthOfMonth()).atTime(LocalTime.MAX);
        return operationRepository.findOperationsParPeriode(debutMois, finMois).stream()
                .filter(op -> !Boolean.TRUE.equals(op.getVenteAnnulee()))
                .toList();
    }

    @Override
    public List<OperationCaisse> getOperationsDeLAnnee() {
        LocalDate aujourdhui = LocalDate.now();
        LocalDateTime debutAnnee = aujourdhui.withDayOfYear(1).atStartOfDay();
        LocalDateTime finAnnee = aujourdhui.withDayOfYear(aujourdhui.lengthOfYear()).atTime(LocalTime.MAX);
        return operationRepository.findOperationsParPeriode(debutAnnee, finAnnee).stream()
                .filter(op -> !Boolean.TRUE.equals(op.getVenteAnnulee()))
                .toList();
    }

    @Override
    public List<OperationCaisse> getOperationsParPeriode(LocalDate dateDebut, LocalDate dateFin) {
        LocalDateTime debut = dateDebut.atStartOfDay();
        LocalDateTime fin = dateFin.atTime(LocalTime.MAX);
        return operationRepository.findOperationsParPeriode(debut, fin).stream()
                .filter(op -> !Boolean.TRUE.equals(op.getVenteAnnulee()))
                .toList();
    }

    // ==================== STATISTIQUES ====================

    @Override
    public Map<String, Object> getStatistiquesDuJour() {
        return getStatistiquesParPeriode(LocalDate.now(), LocalDate.now());
    }

    @Override
    public Map<String, Object> getStatistiquesDeLaSemaine() {
        LocalDate debutSemaine = LocalDate.now().with(TemporalAdjusters.previousOrSame(java.time.DayOfWeek.MONDAY));
        LocalDate finSemaine = LocalDate.now().with(TemporalAdjusters.nextOrSame(java.time.DayOfWeek.SUNDAY));
        return getStatistiquesParPeriode(debutSemaine, finSemaine);
    }

    @Override
    public Map<String, Object> getStatistiquesDuMois() {
        LocalDate aujourdhui = LocalDate.now();
        LocalDate debutMois = aujourdhui.withDayOfMonth(1);
        LocalDate finMois = aujourdhui.withDayOfMonth(aujourdhui.lengthOfMonth());
        return getStatistiquesParPeriode(debutMois, finMois);
    }

    @Override
    public Map<String, Object> getStatistiquesDeLAnnee() {
        LocalDate aujourdhui = LocalDate.now();
        LocalDate debutAnnee = aujourdhui.withDayOfYear(1);
        LocalDate finAnnee = aujourdhui.withDayOfYear(aujourdhui.lengthOfYear());
        return getStatistiquesParPeriode(debutAnnee, finAnnee);
    }

    @Override
    public Map<String, Object> getStatistiquesParPeriode(LocalDate dateDebut, LocalDate dateFin) {
        Map<String, Object> stats = new HashMap<>();
        LocalDateTime debut = dateDebut.atStartOfDay();
        LocalDateTime fin = dateFin.atTime(LocalTime.MAX);

        List<OperationCaisse> operationsBrutes = operationRepository.findOperationsParPeriode(debut, fin);
        List<OperationCaisse> operations = operationsBrutes.stream()
                .filter(op -> !Boolean.TRUE.equals(op.getVenteAnnulee()))
                .toList();

        Double totalVentesComptant = 0.0;
        Double totalVentesCredit = 0.0;
        Double totalReglementsCredit = 0.0;
        Double totalEntrees = 0.0;
        Double totalSorties = 0.0;
        Double totalPaiementsFournisseurs = 0.0;
        Double totalAvancesFournisseurs = 0.0;
        Double totalRemboursementsRetour = 0.0;
        Double totalPaiementsEmployes = 0.0;
        Double totalAnnulations = 0.0;

        for (OperationCaisse op : operations) {
            switch (op.getType()) {
                case VENTE_COMPTANT:
                    totalVentesComptant += op.getMontant();
                    break;
                case VENTE_CREDIT:
                    totalVentesCredit += op.getMontant();
                    break;
                case REGLEMENT_CREDIT:
                    totalReglementsCredit += op.getMontant();
                    break;
                case ENTREE:
                    totalEntrees += op.getMontant();
                    break;
                case SORTIE:
                    totalSorties += op.getMontant();
                    break;
                case PAIEMENT_FOURNISSEUR:
                    totalPaiementsFournisseurs += op.getMontant();
                    break;
                case AVANCE_FOURNISSEUR:
                    totalAvancesFournisseurs += op.getMontant();
                    break;
                case REMBOURSEMENT_RETOUR:
                    totalRemboursementsRetour += op.getMontant();
                    break;
                case PAIEMENT_EMPLOYE:
                    totalPaiementsEmployes += op.getMontant();
                    break;
                case ANNULATION_VENTE:
                case ANNULATION_CREDIT:
                    totalAnnulations += op.getMontant();
                    break;
                default:
                    break;
            }
        }

        // CORRECTION: totalSortiesCaisse n'inclut PAS les remboursements retour
        Double totalSortiesCaisse = totalSorties + totalPaiementsFournisseurs + totalAvancesFournisseurs + totalPaiementsEmployes + totalAnnulations;

        // BUG FIX (audit comptable) : soldeNetPeriode = totalEntreesCaisse - totalSortiesCaisse
        // ne reflétait pas le vrai mouvement de caisse sur la période, car totalSortiesCaisse
        // exclut délibérément les remboursements retour (cf. commentaire ci-dessus) ET,
        // avant ce correctif, les virements vers la banque, dépenses, remboursements de
        // commande annulée et autres types non listés dans le switch — tout type
        // d'opération non explicitement géré y tombait silencieusement (default: break),
        // sans jamais impacter ni entrées ni sorties. Calcul robuste : chaque OperationCaisse
        // porte déjà soldeAvant/soldeApres qui reflète FIDÈLEMENT son effet réel sur la
        // caisse (delta positif ou négatif, quel que soit le type) ; sommer ces deltas sur
        // TOUTE la période (liste non filtrée sur venteAnnulee, pour que vente + annulation
        // survenues toutes les deux dans la période se neutralisent correctement) donne le
        // vrai solde net, sans dépendre d'une classification manuelle exhaustive par type.
        //
        // BUG FIX #2 (2026-08-13) : ce solde net alimente la carte "Bénéfice net / Perte
        // nette" du mois (soldeNetPeriode côté front), donc c'est censé mesurer le
        // RÉSULTAT de l'activité, pas le solde physique du tiroir-caisse. Or un
        // VIREMENT_BANQUE fait bien baisser le solde du tiroir-caisse (l'argent quitte la
        // caisse), mais ce n'est pas une dépense/perte : l'argent reste à la boutique,
        // simplement déplacé vers le compte en banque. Vérifié sur les données réelles de
        // juillet (boutique1, VPS) : 97 581 950 F de virements ce mois-là faisaient passer
        // l'affichage de +89 772 350 F (vrai résultat) à -7 809 600 F ("Perte nette"),
        // alors que le mois était largement bénéficiaire. On exclut donc VIREMENT_BANQUE de
        // ce calcul (les autres types, y compris ceux non listés dans le switch ci-dessus,
        // restent inclus via le même mécanisme robuste).
        double soldeNetPeriodeReel = operationsBrutes.stream()
                .filter(op -> op.getType() != TypeOperationCaisse.VIREMENT_BANQUE)
                .mapToDouble(op -> (op.getSoldeApres() != null ? op.getSoldeApres() : 0.0)
                        - (op.getSoldeAvant() != null ? op.getSoldeAvant() : 0.0))
                .sum();

        // Argent réellement entré / sorti du tiroir sur la période, avec la même règle que
        // soldeNetPeriode (effet réel de chaque opération, dépôts banque à part) : l'écran Caisse
        // affiche ainsi Entré − Sorti = Différence. Les ventes Wave/Moov/Orange Money n'y figurent
        // pas (rien n'entre dans le tiroir) ; dépenses, remboursements… sont bien dans « sorti ».
        double argentEntreTiroir = 0.0;
        double argentSortiTiroir = 0.0;
        double argentDeposeBanque = 0.0;
        for (OperationCaisse op : operationsBrutes) {
            double effet = (op.getSoldeApres() != null ? op.getSoldeApres() : 0.0)
                    - (op.getSoldeAvant() != null ? op.getSoldeAvant() : 0.0);
            if (op.getType() == TypeOperationCaisse.VIREMENT_BANQUE) argentDeposeBanque -= effet;
            else if (effet > 0) argentEntreTiroir += effet;
            else argentSortiTiroir -= effet;
        }

        Map<LocalDate, Double> chiffreParJour = new HashMap<>();
        Map<LocalDate, Integer> nombreOperationsParJour = new HashMap<>();

        for (OperationCaisse op : operations) {
            LocalDate date = op.getDateOperation().toLocalDate();
            chiffreParJour.merge(date, op.getMontant(), Double::sum);
            nombreOperationsParJour.merge(date, 1, Integer::sum);
        }

        Double totalEntreesCaisse = totalVentesComptant + totalReglementsCredit + totalEntrees;

        stats.put("periode", Map.of("debut", dateDebut, "fin", dateFin, "nbJours", ChronoUnit.DAYS.between(dateDebut, dateFin) + 1));
        stats.put("totalVentesComptant", arrondir(totalVentesComptant));
        stats.put("totalNouveauxCredits", arrondir(totalVentesCredit));
        stats.put("totalReglementsCredit", arrondir(totalReglementsCredit));
        stats.put("totalAutresEntrees", arrondir(totalEntrees));
        stats.put("totalPaiementsFournisseurs", arrondir(totalPaiementsFournisseurs));
        stats.put("totalAvancesFournisseurs", arrondir(totalAvancesFournisseurs));
        stats.put("totalRemboursementsRetour", arrondir(totalRemboursementsRetour));
        stats.put("totalPaiementsEmployes", arrondir(totalPaiementsEmployes));
        stats.put("totalAnnulations", arrondir(totalAnnulations));
        stats.put("totalSorties", arrondir(totalSortiesCaisse));
        stats.put("totalEntrees", arrondir(totalEntreesCaisse));
        stats.put("soldeNetPeriode", arrondir(soldeNetPeriodeReel));
        stats.put("argentEntreTiroir", arrondir(argentEntreTiroir));
        stats.put("argentSortiTiroir", arrondir(argentSortiTiroir));
        stats.put("argentDeposeBanque", arrondir(argentDeposeBanque));
        stats.put("nombreOperations", operations.size());
        stats.put("moyenneJournaliere", arrondir(totalEntreesCaisse / (ChronoUnit.DAYS.between(dateDebut, dateFin) + 1)));
        stats.put("chiffreParJour", chiffreParJour);
        stats.put("operationsParJour", nombreOperationsParJour);

        Map<String, Double> parModePaiement = new HashMap<>();
        for (OperationCaisse op : operations) {
            if (op.getModePaiement() != null) {
                parModePaiement.merge(op.getModePaiement().toString(), op.getMontant(), Double::sum);
            }
        }
        stats.put("detailsParModePaiement", parModePaiement);

        return stats;
    }

    @Override
    public Map<String, Object> getRevenusEtPertesParPeriode(LocalDate dateDebut, LocalDate dateFin) {
        Map<String, Object> resultats = new HashMap<>();
        LocalDateTime debut = dateDebut.atStartOfDay();
        LocalDateTime fin = dateFin.atTime(LocalTime.MAX);

        List<OperationCaisse> operations = operationRepository.findOperationsParPeriode(debut, fin).stream()
                .filter(op -> !Boolean.TRUE.equals(op.getVenteAnnulee()))
                .toList();

        Double totalRevenus = 0.0;
        Double totalPertes = 0.0;
        Double totalVentesComptant = 0.0;
        Double totalReglementsCredit = 0.0;
        Double totalAutresEntrees = 0.0;
        Double totalSorties = 0.0;
        Double totalPaiementsFournisseurs = 0.0;
        Double totalAvancesFournisseurs = 0.0;
        Double totalAnnulations = 0.0;
        Double totalRemboursementsRetour = 0.0;

        for (OperationCaisse op : operations) {
            switch (op.getType()) {
                case VENTE_COMPTANT:
                    totalVentesComptant += op.getMontant();
                    totalRevenus += op.getMontant();
                    break;
                case REGLEMENT_CREDIT:
                    totalReglementsCredit += op.getMontant();
                    totalRevenus += op.getMontant();
                    break;
                case ENTREE:
                    totalAutresEntrees += op.getMontant();
                    totalRevenus += op.getMontant();
                    break;
                case SORTIE:
                    totalSorties += op.getMontant();
                    totalPertes += op.getMontant();
                    break;
                case PAIEMENT_FOURNISSEUR:
                    totalPaiementsFournisseurs += op.getMontant();
                    totalPertes += op.getMontant();
                    break;
                case AVANCE_FOURNISSEUR:
                    totalAvancesFournisseurs += op.getMontant();
                    totalPertes += op.getMontant();
                    break;
                case ANNULATION_VENTE:
                case ANNULATION_CREDIT:
                    totalAnnulations += op.getMontant();
                    totalPertes += op.getMontant();
                    break;
                case REMBOURSEMENT_RETOUR:
                    totalRemboursementsRetour += op.getMontant();
                    // CORRECTION: NE PAS ajouter aux pertes
                    break;
                default:
                    break;
            }
        }

        resultats.put("periode", Map.of("dateDebut", dateDebut, "dateFin", dateFin));
        resultats.put("totalRevenus", arrondir(totalRevenus));
        resultats.put("totalPertes", arrondir(totalPertes));
        resultats.put("soldeNet", arrondir(totalRevenus - totalPertes));
        resultats.put("totalRemboursementsRetour", arrondir(totalRemboursementsRetour));
        resultats.put("soldeReelCaisse", arrondir(totalRevenus - totalPertes - totalRemboursementsRetour));
        resultats.put("detailsRevenus", Map.of(
                "ventesComptant", arrondir(totalVentesComptant),
                "reglementsCredit", arrondir(totalReglementsCredit),
                "autresEntrees", arrondir(totalAutresEntrees)));
        resultats.put("detailsPertes", Map.of(
                "sorties", arrondir(totalSorties),
                "paiementsFournisseurs", arrondir(totalPaiementsFournisseurs),
                "avancesFournisseurs", arrondir(totalAvancesFournisseurs),
                "annulations", arrondir(totalAnnulations)));

        return resultats;
    }

    // ==================== RAPPORTS PDF ====================

    @Override
    public byte[] genererRapportJournalier(LocalDate date) { return new byte[0]; }
    @Override
    public byte[] genererRapportHebdomadaire(LocalDate debutSemaine, LocalDate finSemaine) { return new byte[0]; }
    @Override
    public byte[] genererRapportMensuel(int annee, int mois) { return new byte[0]; }
    @Override
    public byte[] genererRapportAnnuel(int annee) { return new byte[0]; }
    @Override
    public byte[] genererRapportPersonnalise(LocalDate dateDebut, LocalDate dateFin) { return new byte[0]; }

    // ==================== VENTES COMPTANT/CREDIT ====================

    @Override
    public Map<String, Object> getVentesComptantDuJour() {
        LocalDateTime debut = LocalDate.now().atStartOfDay();
        LocalDateTime fin = LocalDate.now().atTime(LocalTime.MAX);
        List<OperationCaisse> operations = operationRepository.findOperationsParPeriode(debut, fin).stream()
                .filter(op -> op.getType() == TypeOperationCaisse.VENTE_COMPTANT)
                .filter(op -> !Boolean.TRUE.equals(op.getVenteAnnulee()))
                .toList();
        Map<String, Object> result = new HashMap<>();
        result.put("date", LocalDate.now());
        result.put("nombreVentes", operations.size());
        result.put("totalVentesComptant", arrondir(operations.stream().mapToDouble(OperationCaisse::getMontant).sum()));
        return result;
    }

    @Override
    public Map<String, Object> getVentesCreditDuJour() {
        LocalDateTime debut = LocalDate.now().atStartOfDay();
        LocalDateTime fin = LocalDate.now().atTime(LocalTime.MAX);
        List<OperationCaisse> operations = operationRepository.findOperationsParPeriode(debut, fin).stream()
                .filter(op -> op.getType() == TypeOperationCaisse.VENTE_CREDIT)
                .filter(op -> !Boolean.TRUE.equals(op.getVenteAnnulee()))
                .toList();
        Map<String, Object> result = new HashMap<>();
        result.put("date", LocalDate.now());
        result.put("nombreVentes", operations.size());
        result.put("totalVentesCredit", arrondir(operations.stream().mapToDouble(OperationCaisse::getMontant).sum()));
        return result;
    }

    @Override
    public Map<String, Object> getVentesComptantParPeriode(LocalDate dateDebut, LocalDate dateFin) {
        LocalDateTime debut = dateDebut.atStartOfDay();
        LocalDateTime fin = dateFin.atTime(LocalTime.MAX);
        List<OperationCaisse> operations = operationRepository.findOperationsParPeriode(debut, fin).stream()
                .filter(op -> op.getType() == TypeOperationCaisse.VENTE_COMPTANT)
                .filter(op -> !Boolean.TRUE.equals(op.getVenteAnnulee()))
                .toList();
        Map<String, Object> result = new HashMap<>();
        result.put("periode", Map.of("debut", dateDebut, "fin", dateFin));
        result.put("nombreVentes", operations.size());
        result.put("totalVentesComptant", arrondir(operations.stream().mapToDouble(OperationCaisse::getMontant).sum()));
        return result;
    }

    @Override
    public Map<String, Object> getVentesCreditParPeriode(LocalDate dateDebut, LocalDate dateFin) {
        LocalDateTime debut = dateDebut.atStartOfDay();
        LocalDateTime fin = dateFin.atTime(LocalTime.MAX);
        List<OperationCaisse> operations = operationRepository.findOperationsParPeriode(debut, fin).stream()
                .filter(op -> op.getType() == TypeOperationCaisse.VENTE_CREDIT)
                .filter(op -> !Boolean.TRUE.equals(op.getVenteAnnulee()))
                .toList();
        Map<String, Object> result = new HashMap<>();
        result.put("periode", Map.of("debut", dateDebut, "fin", dateFin));
        result.put("nombreVentes", operations.size());
        result.put("totalVentesCredit", arrondir(operations.stream().mapToDouble(OperationCaisse::getMontant).sum()));
        return result;
    }

    @Override
    public Map<String, Object> getStatistiquesVentesComptantCredit() {
        LocalDateTime debutJour = LocalDate.now().atStartOfDay();
        LocalDateTime finJour = LocalDate.now().atTime(LocalTime.MAX);
        LocalDateTime debutSemaine = LocalDate.now().with(TemporalAdjusters.previousOrSame(java.time.DayOfWeek.MONDAY)).atStartOfDay();
        LocalDateTime finSemaine = LocalDate.now().with(TemporalAdjusters.nextOrSame(java.time.DayOfWeek.SUNDAY)).atTime(LocalTime.MAX);
        LocalDateTime debutMois = LocalDate.now().withDayOfMonth(1).atStartOfDay();
        LocalDateTime finMois = LocalDate.now().withDayOfMonth(LocalDate.now().lengthOfMonth()).atTime(LocalTime.MAX);

        Map<String, Object> stats = new HashMap<>();
        stats.put("jour", Map.of(
                "ventesComptant", arrondir(operationRepository.getTotalByTypeAndPeriod(TypeOperationCaisse.VENTE_COMPTANT, debutJour, finJour)),
                "ventesCredit", arrondir(operationRepository.getTotalByTypeAndPeriod(TypeOperationCaisse.VENTE_CREDIT, debutJour, finJour)),
                "reglementsCredit", arrondir(operationRepository.getTotalByTypeAndPeriod(TypeOperationCaisse.REGLEMENT_CREDIT, debutJour, finJour))));
        stats.put("semaine", Map.of(
                "ventesComptant", arrondir(operationRepository.getTotalByTypeAndPeriod(TypeOperationCaisse.VENTE_COMPTANT, debutSemaine, finSemaine)),
                "ventesCredit", arrondir(operationRepository.getTotalByTypeAndPeriod(TypeOperationCaisse.VENTE_CREDIT, debutSemaine, finSemaine)),
                "reglementsCredit", arrondir(operationRepository.getTotalByTypeAndPeriod(TypeOperationCaisse.REGLEMENT_CREDIT, debutSemaine, finSemaine))));
        stats.put("mois", Map.of(
                "ventesComptant", arrondir(operationRepository.getTotalByTypeAndPeriod(TypeOperationCaisse.VENTE_COMPTANT, debutMois, finMois)),
                "ventesCredit", arrondir(operationRepository.getTotalByTypeAndPeriod(TypeOperationCaisse.VENTE_CREDIT, debutMois, finMois)),
                "reglementsCredit", arrondir(operationRepository.getTotalByTypeAndPeriod(TypeOperationCaisse.REGLEMENT_CREDIT, debutMois, finMois))));
        return stats;
    }

    // ==================== FACTURES ====================

    @Override
    @Transactional
    public Facture creerFacture(FactureRequest request, Long utilisateurId) {
        if (request.getLignes() == null || request.getLignes().isEmpty()) {
            throw new IllegalArgumentException("Une facture doit contenir au moins une ligne");
        }

        Facture facture = new Facture();
        facture.setClientNom(request.getClientNom());
        facture.setClientPrenom(request.getClientPrenom());
        facture.setClientTelephone(request.getClientTelephone());
        facture.setClientAdresse(request.getClientAdresse());
        facture.setNotes(request.getNotes());

        if (utilisateurId != null) {
            Utilisateur utilisateur = utilisateurRepository.findById(utilisateurId)
                    .orElseThrow(() -> new RessourceIntrouvableException("Utilisateur non trouvé"));
            facture.setUtilisateur(utilisateur);
        }

        if (request.getClientId() != null) {
            Client client = clientRepository.findById(request.getClientId())
                    .orElseThrow(() -> new RessourceIntrouvableException("Client non trouvé"));
            facture.setClient(client);
            facture.setClientNom(client.getNom());
            facture.setClientPrenom(client.getPrenom());
            facture.setClientTelephone(client.getNumeroTelephone());
            facture.setClientAdresse(client.getAdresse());
        }

        Facture savedFacture = factureRepository.save(facture);

        for (LigneFactureRequest ligneRequest : request.getLignes()) {
            Produit produit = produitRepository.findById(ligneRequest.getProduitId())
                    .orElseThrow(() -> new RessourceIntrouvableException("Produit non trouvé"));

            LigneFacture ligne = new LigneFacture();
            ligne.setFacture(savedFacture);
            ligne.setProduit(produit);
            ligne.setQuantite(ligneRequest.getQuantite());
            ligne.setPrixUnitaire(ligneRequest.getPrixUnitaire() != null ? ligneRequest.getPrixUnitaire() : produit.getPrixVente());
            ligne.setPrixAchat(produit.getPrixAchat());

            if (ligneRequest.getRemisePourcentage() != null && ligneRequest.getRemisePourcentage() > 0) {
                ligne.appliquerRemisePourcentage(ligneRequest.getRemisePourcentage());
            } else if (ligneRequest.getRemiseMontant() != null && ligneRequest.getRemiseMontant() > 0) {
                ligne.appliquerRemiseMontant(ligneRequest.getRemiseMontant());
            }

            ligne.calculerSousTotal();
            ligneFactureRepository.save(ligne);
            savedFacture.getLignes().add(ligne);
        }

        savedFacture.calculerTotal();
        return factureRepository.save(savedFacture);
    }

    @Override
    @Transactional
    public Facture modifierFacture(Long factureId, FactureRequest request) {
        Facture facture = factureRepository.findById(factureId)
                .orElseThrow(() -> new RessourceIntrouvableException("Facture non trouvée"));

        facture.setClientNom(request.getClientNom());
        facture.setClientPrenom(request.getClientPrenom());
        facture.setClientTelephone(request.getClientTelephone());
        facture.setClientAdresse(request.getClientAdresse());
        facture.setNotes(request.getNotes());

        ligneFactureRepository.deleteByFactureId(factureId);
        facture.getLignes().clear();

        for (LigneFactureRequest ligneRequest : request.getLignes()) {
            Produit produit = produitRepository.findById(ligneRequest.getProduitId())
                    .orElseThrow(() -> new RessourceIntrouvableException("Produit non trouvé"));

            LigneFacture ligne = new LigneFacture();
            ligne.setFacture(facture);
            ligne.setProduit(produit);
            ligne.setQuantite(ligneRequest.getQuantite());
            ligne.setPrixUnitaire(ligneRequest.getPrixUnitaire() != null ? ligneRequest.getPrixUnitaire() : produit.getPrixVente());
            ligne.setPrixAchat(produit.getPrixAchat());

            if (ligneRequest.getRemisePourcentage() != null && ligneRequest.getRemisePourcentage() > 0) {
                ligne.appliquerRemisePourcentage(ligneRequest.getRemisePourcentage());
            } else if (ligneRequest.getRemiseMontant() != null && ligneRequest.getRemiseMontant() > 0) {
                ligne.appliquerRemiseMontant(ligneRequest.getRemiseMontant());
            }

            ligne.calculerSousTotal();
            ligneFactureRepository.save(ligne);
            facture.getLignes().add(ligne);
        }

        facture.calculerTotal();
        return factureRepository.save(facture);
    }

    @Override
    public Facture obtenirFactureParId(Long factureId) {
        return factureRepository.findById(factureId)
                .orElseThrow(() -> new RessourceIntrouvableException("Facture non trouvée"));
    }

    @Override
    public List<Facture> obtenirToutesFactures() {
        return factureRepository.findAll();
    }

    @Override
    public List<Facture> obtenirFacturesParStatut(String statut) {
        return factureRepository.findByStatut(statut);
    }

    @Override
    public List<Facture> obtenirFacturesParClient(String clientNom) {
        return factureRepository.findByClientNomContainingIgnoreCase(clientNom);
    }

    @Override
    public List<Facture> obtenirFacturesParPeriode(LocalDateTime dateDebut, LocalDateTime dateFin) {
        return factureRepository.findByDateCreationBetween(dateDebut, dateFin);
    }

    @Override
    @Transactional
    public void supprimerFacture(Long factureId) {
        Facture facture = factureRepository.findById(factureId)
                .orElseThrow(() -> new RessourceIntrouvableException("Facture non trouvée"));
        ligneFactureRepository.deleteByFactureId(factureId);
        factureRepository.deleteById(factureId);
    }

    @Override
    @Transactional
    public Facture validerFacture(Long factureId) {
        Facture facture = factureRepository.findById(factureId)
                .orElseThrow(() -> new RessourceIntrouvableException("Facture non trouvée"));
        facture.setStatut("VALIDE");
        return factureRepository.save(facture);
    }

    @Override
    @Transactional
    public Facture annulerFacture(Long factureId) {
        Facture facture = factureRepository.findById(factureId)
                .orElseThrow(() -> new RessourceIntrouvableException("Facture non trouvée"));
        if ("ANNULEE".equals(facture.getStatut())) {
            throw new IllegalStateException("La facture est déjà annulée");
        }
        facture.setStatut("ANNULEE");
        return factureRepository.save(facture);
    }

    @Override
    public Map<String, Object> getStatistiquesFactures() {
        List<Facture> toutesFactures = factureRepository.findAll();
        long nombreTotal = toutesFactures.size();
        long nombreBrouillons = toutesFactures.stream().filter(f -> "BROUILLON".equals(f.getStatut())).count();
        long nombreValides = toutesFactures.stream().filter(f -> "VALIDE".equals(f.getStatut())).count();
        long nombrePayees = toutesFactures.stream().filter(f -> "PAYEE".equals(f.getStatut())).count();
        long nombreAnnulees = toutesFactures.stream().filter(f -> "ANNULEE".equals(f.getStatut())).count();
        Double montantTotal = toutesFactures.stream()
                .filter(f -> "PAYEE".equals(f.getStatut()) || "VALIDE".equals(f.getStatut()))
                .mapToDouble(Facture::getMontantTotal).sum();
        Map<String, Object> stats = new HashMap<>();
        stats.put("nombreTotal", nombreTotal);
        stats.put("nombreBrouillons", nombreBrouillons);
        stats.put("nombreValides", nombreValides);
        stats.put("nombrePayees", nombrePayees);
        stats.put("nombreAnnulees", nombreAnnulees);
        stats.put("montantTotal", arrondir(montantTotal));
        return stats;
    }

    // ==================== TRANSFERT CAISSE → BANQUE ====================

    @Override
    @Transactional
    public Map<String, Object> transfererVersBanque(TransfertCaisseBanqueRequest request) {
        log.info("=== TRANSFERT CAISSE → BANQUE ===");
        log.info("Compte ID: {}, Montant: {}, Motif: {}", request.getCompteId(), request.getMontant(), request.getMotif());

        if (request.getMontant() == null || request.getMontant() <= 0) {
            throw new IllegalArgumentException("Le montant doit être supérieur à 0");
        }

        if (request.getCompteId() == null) {
            throw new IllegalArgumentException("Le compte bancaire destination est requis");
        }

        verifierEtOuvrirCaisseSiNecessaire();
        Caisse caisse = getCaisseOuverte();

        if (caisse.getSoldeActuel() < request.getMontant()) {
            throw new SoldeInsuffisantException(
                    "Solde caisse insuffisant. Disponible: " + caisse.getSoldeActuel() +
                            ", Demandé: " + request.getMontant());
        }

        Compte compte = compteRepository.findById(request.getCompteId())
                .orElseThrow(() -> new RessourceIntrouvableException("Compte bancaire non trouvé avec l'ID: " + request.getCompteId()));

        if (!compte.isActif()) {
            throw new IllegalStateException("Le compte bancaire " + compte.getNomBanque() + " est inactif");
        }

        Double soldeCaisseAvant = caisse.getSoldeActuel();

        caisse.setSoldeActuel(soldeCaisseAvant - request.getMontant());
        caisse.setTotalSorties(caisse.getTotalSorties() + request.getMontant());
        caisse.setDerniereOperation(LocalDateTime.now());
        caisseRepository.save(caisse);

        OperationCaisse operationCaisse = new OperationCaisse();
        operationCaisse.setCaisse(caisse);
        operationCaisse.setType(TypeOperationCaisse.VIREMENT_BANQUE);
        operationCaisse.setMontant(request.getMontant());
        operationCaisse.setSoldeAvant(soldeCaisseAvant);
        operationCaisse.setSoldeApres(caisse.getSoldeActuel());
        operationCaisse.setMotif("Virement vers banque - " + compte.getNomBanque() + " - " + (request.getMotif() != null ? request.getMotif() : ""));
        operationCaisse.setEstReglee(true);
        operationCaisse.setDateOperation(LocalDateTime.now());
        operationCaisse.setReferencePaiement(request.getReference());

        if (request.getUtilisateurId() != null) {
            utilisateurRepository.findById(request.getUtilisateurId()).ifPresent(operationCaisse::setUtilisateur);
        }

        OperationCaisse savedOperationCaisse = operationRepository.save(operationCaisse);
        log.info("Sortie de caisse enregistrée: {} F", request.getMontant());

        double soldeCompteAvant = compte.getSoldeActuel();

        compte.setTotalVersements(compte.getTotalVersements() + request.getMontant());
        compte.recalculerSolde();
        compteRepository.save(compte);

        OperationCompte operationCompte = new OperationCompte();
        operationCompte.setCompte(compte);
        operationCompte.setType("VIREMENT_CAISSE");
        operationCompte.setMontant(request.getMontant());
        operationCompte.setSoldeAvant(soldeCompteAvant);
        operationCompte.setSoldeApres(compte.getSoldeActuel());
        operationCompte.setMotif("Versement depuis caisse - " + (request.getMotif() != null ? request.getMotif() : ""));
        operationCompte.setReference(request.getReference());
        operationCompte.setUtilisateurId(request.getUtilisateurId());

        OperationCompte savedOperationCompte = operationCompteRepository.save(operationCompte);
        log.info("Crédit compte bancaire enregistré: {} F sur {}", request.getMontant(), compte.getNomBanque());

        TransfertCaisseBanque trace = new TransfertCaisseBanque();
        trace.setOperationCaisse(savedOperationCaisse);
        trace.setOperationCompte(savedOperationCompte);
        trace.setCompte(compte);
        trace.setMontant(request.getMontant());
        trace.setMotif(request.getMotif());
        trace.setReference(request.getReference());
        trace.setUtilisateurId(request.getUtilisateurId());

        transfertRepository.save(trace);
        log.info("Trace du transfert enregistrée");

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("success", true);
        response.put("message", "Transfert caisse → banque effectué avec succès");
        response.put("montant", request.getMontant());
        response.put("caisse", Map.of(
                "soldeAvant", soldeCaisseAvant,
                "soldeApres", caisse.getSoldeActuel()
        ));
        response.put("compte", Map.of(
                "id", compte.getId(),
                "nomBanque", compte.getNomBanque(),
                "soldeAvant", soldeCompteAvant,
                "soldeApres", compte.getSoldeActuel()
        ));
        response.put("operationCaisseId", savedOperationCaisse.getId());
        response.put("operationCompteId", savedOperationCompte.getId());
        response.put("transfertId", trace.getId());
        response.put("dateTransfert", trace.getDateTransfert());

        return response;
    }

    // ==================== PAIEMENTS GROUPES ====================

    @Override
    public List<Map<String, Object>> getPaiementsGroupes() {
        // Récupérer toutes les opérations de règlement avec motif "Paiement groupé"
        List<OperationCaisse> ops = operationRepository.findAll().stream()
            .filter(op -> TypeOperationCaisse.REGLEMENT_CREDIT.equals(op.getType())
                       && op.getMotif() != null
                       && op.getMotif().contains("Paiement group"))
            .collect(java.util.stream.Collectors.toList());

        // Grouper par referenceGroupe si présent, sinon par (clientNom + date jour + montant motif)
        Map<String, List<OperationCaisse>> grouped = new java.util.LinkedHashMap<>();
        for (OperationCaisse op : ops) {
            String key;
            if (op.getReferenceGroupe() != null && !op.getReferenceGroupe().isEmpty()) {
                key = op.getReferenceGroupe();
            } else {
                String motif = op.getMotif();
                String montantStr;
                if (motif != null && motif.contains(":")) {
                    montantStr = motif.substring(motif.lastIndexOf(":") + 1).trim();
                } else {
                    montantStr = "0";
                }
                String dateJour = op.getDateOperation() != null
                    ? op.getDateOperation().toString().substring(0, 10)
                    : "?";
                key = (op.getClientNom() != null ? op.getClientNom() : "?") + "_" + dateJour + "_" + montantStr;
            }
            grouped.computeIfAbsent(key, k -> new java.util.ArrayList<>()).add(op);
        }

        List<Map<String, Object>> result = new java.util.ArrayList<>();
        for (Map.Entry<String, List<OperationCaisse>> entry : grouped.entrySet()) {
            List<OperationCaisse> groupe = entry.getValue();
            OperationCaisse first = groupe.get(0);

            // Extraire montant total du motif
            String motif = first.getMotif();
            double montantTotal = 0;
            try {
                if (motif != null && motif.contains(":")) {
                    montantTotal = Double.parseDouble(motif.substring(motif.lastIndexOf(":") + 1).trim());
                }
            } catch (NumberFormatException e) { /* ignore */ }

            // Construire la liste des ventes impliquées
            List<Map<String, Object>> ventesImpliquees = new java.util.ArrayList<>();
            for (OperationCaisse op : groupe) {
                Map<String, Object> v = new java.util.LinkedHashMap<>();
                v.put("venteCreditId", op.getVenteCreditId());
                v.put("montantApplique", op.getMontant());
                String statut = "INCONNU";
                try {
                    if (op.getVenteCreditId() != null) {
                        Vente vente = venteRepository.findById(op.getVenteCreditId()).orElse(null);
                        if (vente != null) {
                            double verse = vente.getMontantVerse() != null ? vente.getMontantVerse() : 0;
                            double total = vente.getMontantTotal() != null ? vente.getMontantTotal() : 0;
                            statut = verse >= total ? "REGLE" : "EN_COURS";
                            v.put("resteARegler", Math.max(0, total - verse));
                            v.put("numeroVente", vente.getNumeroVente());
                        }
                    }
                } catch (Exception e) { /* ignore */ }
                v.put("statutCredit", statut);
                ventesImpliquees.add(v);
            }

            Map<String, Object> session = new java.util.LinkedHashMap<>();
            session.put("referenceGroupe", entry.getKey());
            session.put("clientNom", first.getClientNom());
            session.put("date", first.getDateOperation());
            session.put("montantTotalApporte", montantTotal);
            session.put("ventesImpliquees", ventesImpliquees);
            result.add(session);
        }

        // Trier par date décroissante
        result.sort((a, b) -> {
            Object da = a.get("date");
            Object db = b.get("date");
            if (da == null) return 1;
            if (db == null) return -1;
            return db.toString().compareTo(da.toString());
        });

        return result;
    }

    // ==================== RÉINITIALISATION / SUPPRESSION HISTORIQUE ====================

    @Override
    @Transactional
    public void reinitialiserJour() {
        log.info("=== RÉINITIALISATION CAISSE DU JOUR ===");
        LocalDateTime debut = LocalDate.now().atStartOfDay();
        LocalDateTime fin = LocalDate.now().atTime(LocalTime.MAX);

        // Supprimer toutes les opérations de la journée courante
        operationRepository.deleteByDateOperationBetween(debut, fin);
        log.info("Opérations du jour supprimées");

        // Remettre les compteurs de la caisse ouverte à zéro
        caisseRepository.findCaisseOuverte().ifPresent(caisse -> {
            caisse.setSoldeActuel(0.0);
            caisse.setSoldeSysteme(0.0);
            caisse.setSoldeInitial(0.0);
            caisse.setTotalEntrees(0.0);
            caisse.setTotalSorties(0.0);
            caisse.setNombreOperations(0);
            caisseRepository.save(caisse);
            log.info("Compteurs de la caisse ouverte réinitialisés à 0");
        });
    }

    @Override
    @Transactional
    public void supprimerHistorique() {
        log.info("=== SUPPRESSION HISTORIQUE COMPLET DES OPÉRATIONS DE CAISSE ===");
        operationRepository.deleteAllOperations();
        log.info("Historique complet supprimé");
    }

    // ==================== METHODES PRIVEES ====================

    private void verifierEtOuvrirCaisseSiNecessaire() {
        try {
            getCaisseOuverte();
        } catch (IllegalStateException e) {
            ouvrirCaisse();
        }
    }

    private Double arrondir(Double valeur) {
        if (valeur == null) return 0.0;
        return BigDecimal.valueOf(valeur).setScale(2, RoundingMode.HALF_UP).doubleValue();
    }

    // ==================== RÉCONCILIATION CAISSE PAR VENDEUR ====================

    /**
     * Rapport en lecture seule : pour une date donnée, calcule à la volée ce que chaque
     * vendeur ayant eu de l'activité ce jour-là doit remettre en espèces à l'admin.
     * Aucune nouvelle table : agrège les Vente (ventes comptant/crédit du jour) et les
     * OperationCaisse de type REGLEMENT_CREDIT (règlements de crédit encaissés en espèces).
     */
    @Override
    public List<ReconciliationVendeurDTO> getReconciliationVendeurs(LocalDate date) {
        LocalDate dateCible = date != null ? date : LocalDate.now();
        LocalDateTime debut = dateCible.atStartOfDay();
        LocalDateTime fin = dateCible.atTime(LocalTime.MAX);

        Map<Long, ReconciliationVendeurDTO> parVendeur = new LinkedHashMap<>();

        // 1) Ventes (comptant + crédit) du jour, non annulées, avec vendeur identifié
        List<Vente> ventesDuJour = venteRepository.findByDateRange(debut, fin).stream()
                .filter(v -> !Boolean.TRUE.equals(v.getAnnulee()))
                .filter(v -> v.getVendeur() != null)
                .toList();

        for (Vente vente : ventesDuJour) {
            Long vendeurId = vente.getVendeur().getId();
            String vendeurNom = vente.getVendeur().getNomComplet();
            ReconciliationVendeurDTO dto = parVendeur.computeIfAbsent(vendeurId,
                    id -> new ReconciliationVendeurDTO(id, vendeurNom, 0L, 0.0, 0.0, 0.0, 0.0));

            double montantTotal = vente.getMontantTotal() != null ? vente.getMontantTotal() : 0.0;

            if (Boolean.TRUE.equals(vente.getEstCredit())) {
                dto.setTotalVentesCredit(arrondir(dto.getTotalVentesCredit() + montantTotal));
            } else {
                // Vente comptant (mode de règlement != crédit)
                dto.setNombreVentes(dto.getNombreVentes() + 1);
                if (vente.getModePaiement() == ModePaiement.ESPECES) {
                    dto.setTotalVentesEspeces(arrondir(dto.getTotalVentesEspeces() + montantTotal));
                }
            }
        }

        // 2) Règlements de crédit du jour, encaissés en ESPECES, non annulés (ni le règlement,
        //    ni la vente liée), avec un utilisateur (vendeur) identifié
        List<OperationCaisse> reglementsEspecesDuJour = operationRepository.findOperationsParPeriode(debut, fin).stream()
                .filter(op -> op.getType() == TypeOperationCaisse.REGLEMENT_CREDIT)
                .filter(op -> !op.isAnnule())
                .filter(op -> !Boolean.TRUE.equals(op.getVenteAnnulee()))
                .filter(op -> op.getModePaiement() == ModePaiementCaisse.ESPECES)
                .filter(op -> op.getUtilisateur() != null)
                .toList();

        for (OperationCaisse reglement : reglementsEspecesDuJour) {
            Long vendeurId = reglement.getUtilisateur().getId();
            String vendeurNom = reglement.getUtilisateur().getNomComplet();
            ReconciliationVendeurDTO dto = parVendeur.computeIfAbsent(vendeurId,
                    id -> new ReconciliationVendeurDTO(id, vendeurNom, 0L, 0.0, 0.0, 0.0, 0.0));

            double montant = reglement.getMontant() != null ? reglement.getMontant() : 0.0;
            dto.setTotalReglementsCreditEspeces(arrondir(dto.getTotalReglementsCreditEspeces() + montant));
        }

        List<ReconciliationVendeurDTO> resultat = new ArrayList<>(parVendeur.values());
        for (ReconciliationVendeurDTO dto : resultat) {
            dto.setTotalAiRemettre(arrondir(dto.getTotalVentesEspeces() + dto.getTotalReglementsCreditEspeces()));
        }
        resultat.sort(Comparator.comparing(ReconciliationVendeurDTO::getVendeurNom, String.CASE_INSENSITIVE_ORDER));

        return resultat;
    }

    // ==================== ANNULATION RÈGLEMENT CRÉDIT ====================

    @Override
    @Transactional
    public OperationCaisse annulerReglementCredit(Long operationId, Long utilisateurId) {
        OperationCaisse op = operationRepository.findById(operationId)
                .orElseThrow(() -> new RessourceIntrouvableException("Opération introuvable: " + operationId));

        if (op.isAnnule()) {
            throw new IllegalStateException("Ce règlement est déjà annulé");
        }

        Caisse caisse = verrouiller(op.getCaisse());
        // Relu avec verrou (lecture à jour, pas l'ancienne « photo » de la transaction) : une 2e
        // annulation simultanée du même règlement voit qu'il est déjà annulé.
        entityManager.refresh(op, LockModeType.PESSIMISTIC_WRITE);
        if (op.isAnnule()) {
            throw new IllegalStateException("Ce règlement est déjà annulé");
        }
        double montant = op.getMontant();
        // On ne retire du tiroir que ce que ce règlement y avait mis (rien s'il a été payé en mobile money).
        double dansLeTiroir = ajouteAuTiroir(op);

        // Reverse l'entrée en caisse
        double soldeAvantAnnulation = caisse.getSoldeActuel();
        caisse.setSoldeActuel(soldeAvantAnnulation - dansLeTiroir);
        caisse.setTotalEntrees(Math.max(0.0, caisse.getTotalEntrees() - dansLeTiroir));
        caisse.setDerniereOperation(LocalDateTime.now());
        caisseRepository.save(caisse);

        // Trace dans l'historique : avant, le solde baissait sans aucune ligne, ce qui laissait un
        // « trou » inexplicable et faussait la « Différence » de la journée.
        OperationCaisse trace = new OperationCaisse();
        trace.setCaisse(caisse);
        trace.setType(TypeOperationCaisse.AJUSTEMENT);
        trace.setMontant(montant);
        trace.setSoldeAvant(soldeAvantAnnulation);
        trace.setSoldeApres(caisse.getSoldeActuel());
        String motifTrace = "Annulation règlement crédit #" + op.getId()
                + (op.getMotif() != null ? " (" + op.getMotif() + ")" : "")
                + (dansLeTiroir < montant ? " - payé hors caisse, rien retiré du tiroir" : "");
        trace.setMotif(motifTrace.length() > 250 ? motifTrace.substring(0, 250) : motifTrace);
        trace.setModePaiement(op.getModePaiement());
        trace.setEstReglee(true);
        trace.setDateOperation(LocalDateTime.now());
        if (utilisateurId != null) {
            utilisateurRepository.findById(utilisateurId).ifPresent(trace::setUtilisateur);
        }
        operationRepository.save(trace);

        // Rétablir la dette crédit sur l'opération VENTE_CREDIT associée
        if (op.getVenteCreditId() != null) {
            OperationCaisse venteCredit = operationRepository
                    .findByVenteCreditIdAndType(op.getVenteCreditId(), TypeOperationCaisse.VENTE_CREDIT)
                    .orElse(null);
            if (venteCredit != null) {
                double nouveauVerse = Math.max(0.0,
                        (venteCredit.getMontantVerse() != null ? venteCredit.getMontantVerse() : 0.0) - montant);
                venteCredit.setMontantVerse(nouveauVerse);
                double montantCredit = venteCredit.getMontant() != null ? venteCredit.getMontant() : 0.0;
                venteCredit.setMontantRestant(montantCredit - nouveauVerse);
                venteCredit.setEstReglee(venteCredit.getMontantRestant() <= 0.01);
                operationRepository.save(venteCredit);
            }

            // Mettre à jour la Vente elle-même (source de vérité pour getCreditsNonRegles)
            venteRepository.findById(op.getVenteCreditId()).ifPresent(vente -> {
                double nouveauVerse = Math.max(0.0,
                        (vente.getMontantVerse() != null ? vente.getMontantVerse() : 0.0) - montant);
                vente.setMontantVerse(nouveauVerse);
                double montantTotal = vente.getMontantTotal() != null ? vente.getMontantTotal() : 0.0;
                vente.setMontantRestant(montantTotal - nouveauVerse);
                if (vente.getMontantRestant() > 0.01) {
                    vente.setCreditRegle(false);
                    vente.setDateReglement(null);
                }
                venteRepository.save(vente);
            });
        }

        op.setAnnule(true);
        op.setDateAnnulationReglement(LocalDateTime.now());
        log.info("Règlement crédit #{} annulé: {} F (dont {} F retirés du tiroir)", operationId, montant, dansLeTiroir);
        return operationRepository.save(op);
    }

    @Override
    @Transactional(readOnly = true)
    public List<OperationCaisse> getReglementsParPeriode(String dateDebutStr, String dateFinStr) {
        if (dateDebutStr != null && dateFinStr != null) {
            LocalDateTime dateDebut = LocalDate.parse(dateDebutStr).atStartOfDay();
            LocalDateTime dateFin = LocalDate.parse(dateFinStr).atTime(23, 59, 59);
            return operationRepository.findReglementsByPeriode(dateDebut, dateFin);
        }
        return operationRepository.findAllReglements();
    }
}