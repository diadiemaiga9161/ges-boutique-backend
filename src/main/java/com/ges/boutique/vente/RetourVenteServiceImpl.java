package com.ges.boutique.vente;

import com.ges.boutique.avance.AvanceClientService;
import com.ges.boutique.caisse.CaisseService;
import com.ges.boutique.exception.RessourceIntrouvableException;
import com.ges.boutique.inventaire.InventaireService;
import com.ges.boutique.produit.Produit;
import com.ges.boutique.produit.ProduitRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class RetourVenteServiceImpl {

    private final VenteRepository venteRepository;
    private final LigneVenteRepository ligneVenteRepository;
    private final ProduitRepository produitRepository;
    private final RetourVenteRepository retourVenteRepository;
    private final CaisseService caisseService;
    private final AvanceClientService avanceClientService;
    private final InventaireService inventaireService;

    @Transactional
    public RetourVente effectuerRetour(RetourVenteRequest request) {
        log.info("=== EFFETUER RETOUR ===");
        log.info("Vente ID: {}", request.getVenteId());

        // Vente verrouillée jusqu'à la fin du retour : un 2e retour envoyé en même temps (double
        // clic, deux téléphones) attend, puis voit ce qui a déjà été rendu et est refusé s'il est en trop.
        Vente vente = venteRepository.findByIdPourMiseAJour(request.getVenteId())
                .orElseThrow(() -> new RessourceIntrouvableException("Vente introuvable: " + request.getVenteId()));

        if (Boolean.TRUE.equals(vente.getAnnulee())) {
            throw new IllegalArgumentException("Impossible de faire un retour sur une vente annulée");
        }

        if (request.getLignes() == null || request.getLignes().isEmpty()) {
            throw new IllegalArgumentException("Au moins une ligne de retour est requise");
        }
        verifierQuantitesRendables(vente, request);

        RetourVente retour = new RetourVente();
        retour.setVente(vente);
        retour.setMotif(request.getMotif());
        retour.setUtilisateurId(request.getUtilisateurId());
        retour.setDateRetour(LocalDateTime.now());

        if (vente.getClient() != null) {
            retour.setClientNom(vente.getClient().getNom());
        } else if (vente.getClientNom() != null) {
            retour.setClientNom(vente.getClientNom());
        } else {
            retour.setClientNom("Client divers");
        }

        double totalRetour = 0.0;
        // Bénéfice réellement perdu par ce retour, calculé à partir du coût d'achat AU
        // MOMENT DE LA VENTE (LigneVente.benefice d'origine), jamais du prix catalogue
        // actuel qui a pu changer depuis — cf. reference-comptabilite: "COGS doit
        // utiliser le coût réel de l'article au moment de la vente".
        double totalBeneficeRetourne = 0.0;
        List<LigneRetourVente> lignes = new ArrayList<>();
        Map<Long, Integer> dejaRendusDansDemande = new HashMap<>();

        for (RetourVenteRequest.LigneRetourVenteRequest ligneReq : request.getLignes()) {
            log.info("Traitement ligne - Produit ID: {}", ligneReq.getProduitId());

            Produit produit = produitRepository.findById(ligneReq.getProduitId())
                    .orElseThrow(() -> new RessourceIntrouvableException("Produit introuvable: " + ligneReq.getProduitId()));

            log.info("Produit trouvé: ID={}, Nom={}", produit.getId(), produit.getNom());

            if (ligneReq.getQuantiteRetournee() == null || ligneReq.getQuantiteRetournee() <= 0) {
                throw new IllegalArgumentException("Quantité invalide pour: " + produit.getNom());
            }

            LigneRetourVente ligne = new LigneRetourVente();
            ligne.setRetour(retour);
            ligne.setProduit(produit);
            ligne.setLigneVenteId(ligneReq.getLigneVenteId());
            ligne.setQuantiteRetournee(ligneReq.getQuantiteRetournee());
            // Produit vendu avec une offre par quantité : le montant rendu est calculé ici
            // (ce que le client avait payé moins le prix de ce qu'il garde), jamais celui de l'appli.
            LigneVente ligneOffre = ligneAvecOffre(vente, ligneReq);
            if (ligneOffre != null) {
                int k = ligneReq.getQuantiteRetournee();
                double montant = montantOffreRetour(vente, ligneOffre, k, dejaRendusDansDemande);
                dejaRendusDansDemande.merge(ligneOffre.getId(), k, Integer::sum);
                ligne.setLigneVenteId(ligneOffre.getId());
                ligne.setPrixUnitaire(montant / k);
                ligne.setSousTotal(montant);
                double prixAchat = ligneOffre.getPrixAchat() != null ? ligneOffre.getPrixAchat() : 0.0;
                totalBeneficeRetourne += montant - prixAchat * k;
            } else {
                ligne.setPrixUnitaire(ligneReq.getPrixUnitaire());
                ligne.setSousTotal(ligneReq.getQuantiteRetournee() * ligneReq.getPrixUnitaire());
            }
            totalRetour += ligne.getSousTotal();
            lignes.add(ligne);

            log.info("Ligne retour créée - Produit: {}, Quantité: {}, Sous-total: {}",
                    produit.getNom(), ligneReq.getQuantiteRetournee(), ligne.getSousTotal());

            String motifRetourStock = "Retour vente " + vente.getNumeroVente() + " - " + produit.getNom();
            inventaireService.retourStock(produit.getId(), ligneReq.getQuantiteRetournee(), request.getUtilisateurId(), motifRetourStock);

            if (ligneOffre == null) {
                totalBeneficeRetourne += calculerBeneficeRetourneLigne(ligneReq, produit);
            }
        }

        retour.setLignes(lignes);
        retour.setMontantTotal(totalRetour);

        // BUG FIX (audit comptable) : le CA et le bénéfice affichés dans les rapports
        // (VenteRepository, ClientRepository) comptaient toujours la marchandise
        // retournée comme si elle était vendue — le retour ne mettait à jour que le
        // stock et la caisse, jamais montantTotal/beneficeTotal ni un champ de suivi
        // équivalent pour les ventes comptant. montantMarchandiseRetournee/beneficeRetourne
        // (nouveaux champs, distincts de montantRetourne qui reste réservé au calcul du
        // solde crédit restant) sont soustraits dans les requêtes de CA/bénéfice pour que
        // le retour réduise bien CA + marge partout, quel que soit le mode de paiement.
        double montantMarchandiseRetourneeCumule = (vente.getMontantMarchandiseRetournee() != null ? vente.getMontantMarchandiseRetournee() : 0.0) + totalRetour;
        double beneficeRetourneCumuleGlobal = (vente.getBeneficeRetourne() != null ? vente.getBeneficeRetourne() : 0.0) + totalBeneficeRetourne;
        vente.setMontantMarchandiseRetournee(montantMarchandiseRetourneeCumule);
        vente.setBeneficeRetourne(beneficeRetourneCumuleGlobal);

        String motifRemboursement = "Retour vente " + vente.getNumeroVente() +
                " - " + retour.getClientNom() +
                (request.getMotif() != null ? " - " + request.getMotif() : "");

        double montantAvanceUtilise = vente.getMontantAvanceUtilise() != null ? vente.getMontantAvanceUtilise() : 0.0;
        String clientNom = vente.getClientNom() != null ? vente.getClientNom() :
                (vente.getClient() != null ? vente.getClient().getNom() : null);

        double montantAvanceRetourne = Math.min(totalRetour, montantAvanceUtilise);
        double montantCaisseRetourne = totalRetour - montantAvanceRetourne;

        if (montantAvanceRetourne > 0 && clientNom != null) {
            avanceClientService.remettreAvance(clientNom, montantAvanceRetourne);
            vente.setMontantAvanceUtilise(Math.max(0, montantAvanceUtilise - montantAvanceRetourne));
        }

        if (Boolean.TRUE.equals(vente.getEstCredit())) {
            // Vente à crédit : la part du retour non couverte par une avance n'a
            // jamais été encaissée (le client n'a pas encore payé) -> on réduit
            // simplement le solde restant à payer, la caisse n'est pas touchée.
            double montantRetourneCumule = (vente.getMontantRetourne() != null ? vente.getMontantRetourne() : 0.0) + montantCaisseRetourne;
            vente.setMontantRetourne(montantRetourneCumule);
            // montantRestant/creditRegle sont recalculés automatiquement (dans les deux sens)
            // par le hook @PreUpdate de Vente au moment du save ci-dessous — pas besoin de les
            // recalculer ici (voir Vente.onUpdate()).
        } else if (montantCaisseRetourne > 0) {
            // Vente comptant : la somme avait bien été encaissée à la vente, elle est remboursée depuis la caisse.
            caisseService.sortieCaisseRemboursementRetour(montantCaisseRetourne, motifRemboursement, request.getUtilisateurId());
        }

        // Un retour peut ne concerner qu'une partie des produits de la vente
        // (ex: 3 articles vendus, 1 seul retourné). On ne marque la vente comme
        // "Retourné" (totalement) que si TOUTE la quantité vendue a été rendue ;
        // sinon c'est un retour partiel, affiché différemment côté UI.
        int quantiteVendueTotale = vente.getLignes() == null ? 0 :
                vente.getLignes().stream().mapToInt(l -> l.getQuantite() != null ? l.getQuantite() : 0).sum();

        int quantiteDejaRetournee = retourVenteRepository.findByVenteIdOrderByDateRetourDesc(vente.getId()).stream()
                .flatMap(r -> r.getLignes().stream())
                .mapToInt(l -> l.getQuantiteRetournee() != null ? l.getQuantiteRetournee() : 0)
                .sum();
        int quantiteRetourneeCetteFois = lignes.stream()
                .mapToInt(l -> l.getQuantiteRetournee() != null ? l.getQuantiteRetournee() : 0)
                .sum();
        int quantiteRetourneeTotale = quantiteDejaRetournee + quantiteRetourneeCetteFois;

        vente.setEstRetourne(true);
        vente.setRetourPartiel(quantiteVendueTotale > 0 && quantiteRetourneeTotale < quantiteVendueTotale);
        venteRepository.save(vente);

        RetourVente saved = retourVenteRepository.save(retour);
        log.info("Retour sauvegardé avec ID: {}", saved.getId());

        return saved;
    }

    /**
     * Montant qui sera rendu, sans rien enregistrer : la caisse l'affiche avant de confirmer,
     * pour que la personne rende exactement ce que le serveur va compter.
     */
    @Transactional(readOnly = true)
    public Map<String, Object> apercu(RetourVenteRequest request) {
        Vente vente = venteRepository.findById(request.getVenteId())
                .orElseThrow(() -> new RessourceIntrouvableException("Vente introuvable: " + request.getVenteId()));
        if (request.getLignes() != null && !request.getLignes().isEmpty()) {
            verifierQuantitesRendables(vente, request);
        }
        Map<Long, Integer> dejaRendusDansDemande = new HashMap<>();
        List<Map<String, Object>> details = new ArrayList<>();
        double total = 0.0;
        List<RetourVenteRequest.LigneRetourVenteRequest> demandees =
                request.getLignes() == null ? List.of() : request.getLignes();
        for (RetourVenteRequest.LigneRetourVenteRequest ligneReq : demandees) {
            int k = ligneReq.getQuantiteRetournee() == null ? 0 : ligneReq.getQuantiteRetournee();
            if (k <= 0) continue;
            LigneVente ligneOffre = ligneAvecOffre(vente, ligneReq);
            double montant;
            if (ligneOffre != null) {
                montant = montantOffreRetour(vente, ligneOffre, k, dejaRendusDansDemande);
                dejaRendusDansDemande.merge(ligneOffre.getId(), k, Integer::sum);
            } else {
                montant = k * (ligneReq.getPrixUnitaire() != null ? ligneReq.getPrixUnitaire() : 0.0);
            }
            Map<String, Object> d = new LinkedHashMap<>();
            d.put("produitId", ligneReq.getProduitId());
            d.put("quantiteRetournee", k);
            d.put("montant", montant);
            d.put("offre", ligneOffre != null ? ligneOffre.getOffreLibelle() : null);
            details.add(d);
            total += montant;
        }
        Map<String, Object> res = new LinkedHashMap<>();
        res.put("montantTotal", total);
        res.put("lignes", details);
        return res;
    }

    /**
     * Refuse un retour qui rendrait, pour un produit, plus que ce qui a été vendu moins ce qui a
     * déjà été rendu (retours précédents + lignes de cette même demande).
     */
    private void verifierQuantitesRendables(Vente vente, RetourVenteRequest request) {
        Map<Long, Integer> vendu = new HashMap<>();
        Map<Long, String> noms = new HashMap<>();
        if (vente.getLignes() != null) {
            for (LigneVente l : vente.getLignes()) {
                if (l.getProduit() == null || l.getProduit().getId() == null) continue;
                vendu.merge(l.getProduit().getId(), l.getQuantite() != null ? l.getQuantite() : 0, Integer::sum);
                noms.putIfAbsent(l.getProduit().getId(), l.getProduit().getNom());
            }
        }
        Map<Long, Integer> dejaRendu = new HashMap<>();
        for (RetourVente r : retourVenteRepository.findByVenteIdOrderByDateRetourDesc(vente.getId())) {
            for (LigneRetourVente lr : r.getLignes()) {
                if (lr.getProduit() == null) continue;
                dejaRendu.merge(lr.getProduit().getId(), lr.getQuantiteRetournee() != null ? lr.getQuantiteRetournee() : 0, Integer::sum);
            }
        }
        Map<Long, Integer> demande = new HashMap<>();
        for (RetourVenteRequest.LigneRetourVenteRequest l : request.getLignes()) {
            if (l.getProduitId() == null || l.getQuantiteRetournee() == null) continue;
            demande.merge(l.getProduitId(), l.getQuantiteRetournee(), Integer::sum);
        }
        for (Map.Entry<Long, Integer> e : demande.entrySet()) {
            Long produitId = e.getKey();
            Integer v = vendu.get(produitId);
            if (v == null) {
                throw new IllegalArgumentException("Ce produit ne fait pas partie de la vente " + vente.getNumeroVente() + ".");
            }
            int rendu = dejaRendu.getOrDefault(produitId, 0);
            int reste = Math.max(0, v - rendu);
            if (e.getValue() > reste) {
                String nom = noms.getOrDefault(produitId, "ce produit");
                throw new IllegalArgumentException(reste == 0
                        ? "« " + nom + " » a déjà été entièrement rendu (" + rendu + " sur " + v + " vendus)."
                        : "« " + nom + " » : " + rendu + " déjà rendu(s) sur " + v + " vendu(s), vous pouvez encore en rendre " + reste + ".");
            }
        }
    }

    /** Ligne de la vente concernée si elle a été vendue avec une offre par quantité, sinon null. */
    private LigneVente ligneAvecOffre(Vente vente, RetourVenteRequest.LigneRetourVenteRequest ligneReq) {
        if (vente.getLignes() == null || ligneReq.getProduitId() == null) return null;
        LigneVente trouvee = null;
        if (ligneReq.getLigneVenteId() != null) {
            trouvee = vente.getLignes().stream()
                    .filter(l -> ligneReq.getLigneVenteId().equals(l.getId())
                            && l.getProduit() != null && ligneReq.getProduitId().equals(l.getProduit().getId()))
                    .findFirst().orElse(null);
        }
        if (trouvee == null) {
            trouvee = vente.getLignes().stream()
                    .filter(l -> l.getProduit() != null && ligneReq.getProduitId().equals(l.getProduit().getId())
                            && l.getOffrePaliers() != null && l.getRemiseOffre() != null && l.getRemiseOffre() > 0)
                    .findFirst().orElse(null);
        }
        if (trouvee == null || trouvee.getOffrePaliers() == null
                || trouvee.getRemiseOffre() == null || trouvee.getRemiseOffre() <= 0) return null;
        return trouvee;
    }

    private double montantOffreRetour(Vente vente, LigneVente ligne, int k, Map<Long, Integer> dejaRendusDansDemande) {
        Long produitId = ligne.getProduit().getId();
        int dejaRendus = retourVenteRepository.findByVenteIdOrderByDateRetourDesc(vente.getId()).stream()
                .flatMap(r -> r.getLignes().stream())
                .filter(lr -> ligne.getId().equals(lr.getLigneVenteId())
                        || (lr.getLigneVenteId() == null && lr.getProduit() != null
                            && produitId.equals(lr.getProduit().getId())))
                .mapToInt(lr -> lr.getQuantiteRetournee() != null ? lr.getQuantiteRetournee() : 0)
                .sum()
                + dejaRendusDansDemande.getOrDefault(ligne.getId(), 0);
        int restant = (ligne.getQuantite() == null ? 0 : ligne.getQuantite()) - dejaRendus;
        if (k > restant) {
            throw new IllegalArgumentException("Quantité retournée trop grande pour « " + ligne.getProduitNom()
                    + " » : il en reste " + Math.max(0, restant) + " à rendre sur cette vente.");
        }
        return com.ges.boutique.offre.OffreQuantiteService.montantRetour(ligne, k, dejaRendus);
    }

    public List<RetourVente> getRetoursByVente(Long venteId) {
        return retourVenteRepository.findByVenteIdOrderByDateRetourDesc(venteId);
    }

    public List<RetourVente> getAllRetours() {
        return retourVenteRepository.findAllByOrderByDateRetourDesc();
    }

    /**
     * Bénéfice à déduire du CA pour la quantité retournée d'une ligne, calculé à partir
     * du bénéfice RÉEL de la ligne de vente d'origine (coût d'achat au moment de la
     * vente) quand elle est retrouvable via ligneVenteId — jamais du prix catalogue
     * actuel du produit, qui a pu changer depuis la vente (cf. reference-comptabilite,
     * section 3.4/9.3). Si l'appelant n'a pas fourni ligneVenteId (anciens appels), on
     * retombe sur la marge catalogue actuelle du produit, en dernier recours seulement.
     */
    // package-private (pas private) pour permettre un test unitaire direct, comme
    // LigneVente.calculerSousTotal() ailleurs dans le projet.
    double calculerBeneficeRetourneLigne(RetourVenteRequest.LigneRetourVenteRequest ligneReq, Produit produit) {
        int quantite = ligneReq.getQuantiteRetournee() != null ? ligneReq.getQuantiteRetournee() : 0;
        if (quantite <= 0) return 0.0;

        if (ligneReq.getLigneVenteId() != null) {
            var ligneVenteOpt = ligneVenteRepository.findById(ligneReq.getLigneVenteId());
            if (ligneVenteOpt.isPresent()) {
                LigneVente ligneVente = ligneVenteOpt.get();
                Integer quantiteVendue = ligneVente.getQuantite();
                Double beneficeLigneVente = ligneVente.getBenefice();
                if (quantiteVendue != null && quantiteVendue > 0 && beneficeLigneVente != null) {
                    double beneficeParUnite = beneficeLigneVente / quantiteVendue;
                    return beneficeParUnite * quantite;
                }
            } else {
                log.warn("LigneVente {} introuvable pour le calcul du bénéfice retourné — repli sur la marge catalogue actuelle du produit {}",
                        ligneReq.getLigneVenteId(), produit.getId());
            }
        }

        double prixAchatActuel = produit.getPrixAchat() != null ? produit.getPrixAchat() : 0.0;
        double prixUnitaireRetour = ligneReq.getPrixUnitaire() != null ? ligneReq.getPrixUnitaire() : 0.0;
        return (prixUnitaireRetour - prixAchatActuel) * quantite;
    }
}