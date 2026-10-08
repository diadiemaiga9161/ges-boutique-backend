package com.ges.boutique.commande;

import com.ges.boutique.boutique.Boutique;
import com.ges.boutique.boutique.BoutiqueRepository;
import com.ges.boutique.client.Client;
import com.ges.boutique.client.ClientRepository;
import com.ges.boutique.exception.RessourceIntrouvableException;
import com.ges.boutique.notification.NotificationPersistanceService;
import com.ges.boutique.produit.Produit;
import com.ges.boutique.produit.ProduitRepository;
import com.ges.boutique.utilisateur.Utilisateur;
import com.ges.boutique.utilisateur.UtilisateurRepository;
import com.ges.boutique.vente.*;
import com.ges.boutique.vitrine.VitrineCommandeRequest;
import com.ges.boutique.websocket.StockWebSocketService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class CommandeServiceImpl implements CommandeService {

    /** Quantité maximale d'un produit dans une commande de la vitrine (unités, ou kg/L/m). */
    private static final int QUANTITE_MAX_VITRINE = 1000;

    private final CommandeRepository commandeRepository;
    private final UtilisateurRepository utilisateurRepository;
    private final ClientRepository clientRepository;
    private final ProduitRepository produitRepository;
    private final VenteService venteService;
    private final NotificationPersistanceService notifService;
    private final StockWebSocketService stockWebSocketService;
    private final BoutiqueRepository boutiqueRepository;

    private static final Long BOUTIQUE_ID = 1L;

    @Override
    @Transactional
    public Commande creer(CommandeRequest request) {
        Commande commande = new Commande();
        commande.setStatut(StatutCommande.BROUILLON);
        commande.setModePaiement(request.getModePaiement());
        commande.setReferencePaiement(request.getReferencePaiement());
        commande.setEstCredit(request.getEstCredit());
        commande.setMontantVerse(request.getMontantVerse() != null ? request.getMontantVerse() : 0.0);
        commande.setDateEcheance(request.getDateEcheance());
        commande.setNotes(request.getNotes());

        // Vendeur
        if (request.getVendeurId() != null) {
            Utilisateur vendeur = utilisateurRepository.findById(request.getVendeurId())
                    .orElseThrow(() -> new RessourceIntrouvableException("Vendeur non trouvé"));
            commande.setVendeur(vendeur);
        }

        // Client
        setClient(commande, request);

        // Lignes
        List<LigneCommande> lignes = buildLignes(request.getLignes(), commande);
        commande.setLignes(lignes);
        commande.recalculer();

        return commandeRepository.save(commande);
    }

    @Override
    @Transactional
    public Commande modifier(Long id, CommandeRequest request) {
        Commande commande = findById(id);

        commande.setModePaiement(request.getModePaiement());
        commande.setReferencePaiement(request.getReferencePaiement());
        commande.setEstCredit(request.getEstCredit());
        commande.setMontantVerse(request.getMontantVerse() != null ? request.getMontantVerse() : 0.0);
        commande.setDateEcheance(request.getDateEcheance());
        commande.setNotes(request.getNotes());

        // Client
        setClient(commande, request);

        // Lignes — remplacer complètement
        commande.getLignes().clear();
        List<LigneCommande> nouvellesLignes = buildLignes(request.getLignes(), commande);
        commande.getLignes().addAll(nouvellesLignes);
        commande.recalculer();

        return commandeRepository.save(commande);
    }

    @Override
    @Transactional
    public Commande valider(Long id, Long currentUserId, ValidationCommandeRequest infosLivraison) {
        Commande commande = findById(id);
        if (commande.getStatut() == StatutCommande.VALIDEE) {
            throw new IllegalStateException("Cette commande est déjà validée");
        }

        if (infosLivraison != null) {
            commande.setFraisLivraison(infosLivraison.getFraisLivraison());
            commande.setChauffeurNom(infosLivraison.getChauffeurNom());
            commande.setChauffeurTelephone(infosLivraison.getChauffeurTelephone());
        }

        // Une commande venue de la vitrine n'a pas de vendeur (personne ne l'a prise en
        // magasin) — Vente.vendeur est NOT NULL, donc sans ceci venteService.creerVente()
        // échouait toujours avec "Le vendeur est requis" et une commande vitrine ne
        // pouvait jamais être validée. Celui qui clique "Valider" devient le vendeur de
        // référence pour la vente générée.
        if (commande.getVendeur() == null && currentUserId != null) {
            utilisateurRepository.findById(currentUserId).ifPresent(commande::setVendeur);
        }

        // Construire une VenteRequest depuis la commande
        VenteRequest venteRequest = new VenteRequest();
        venteRequest.setVendeurId(commande.getVendeurId());
        venteRequest.setClientId(commande.getClientId());
        venteRequest.setClientNom(commande.getClientNom());
        venteRequest.setClientPrenom(commande.getClientPrenom());
        venteRequest.setClientTelephone(commande.getClientTelephone());
        venteRequest.setModePaiement(commande.getModePaiement());
        // Si mode != ESPECES et référence absente, on la génère pour ne pas bloquer la validation
        String ref = commande.getReferencePaiement();
        if ((ref == null || ref.trim().isEmpty()) && commande.getModePaiement() != ModePaiement.ESPECES) {
            ref = "CMD-" + commande.getNumeroCommande();
        }
        venteRequest.setReferencePaiement(ref);
        venteRequest.setEstCredit(commande.getEstCredit());
        venteRequest.setMontantVerse(commande.getMontantVerse());
        venteRequest.setDateEcheance(commande.getDateEcheance());
        venteRequest.setClientDivers(commande.getClientId() == null);

        List<LigneVenteRequest> lignesVente = new ArrayList<>();
        for (LigneCommande lc : commande.getLignes()) {
            LigneVenteRequest lv = new LigneVenteRequest();
            lv.setProduitId(lc.getProduitId());
            lv.setQuantite(lc.getQuantite());
            lv.setPrixUnitaire(lc.getPrixUnitaire());
            lv.setPrixAchat(lc.getPrixAchat());
            lignesVente.add(lv);
        }
        venteRequest.setLignes(lignesVente);

        // Créer la vente → décrémente le stock + caisse + enregistre mouvement SORTIE avec referenceType=VENTE
        Vente vente = venteService.creerVente(venteRequest);

        // Marquer la commande comme validée
        commande.setStatut(StatutCommande.VALIDEE);
        commande.setDateValidation(LocalDateTime.now());
        commande.setVenteId(vente.getId());

        return commandeRepository.save(commande);
    }

    @Override
    @Transactional
    public void supprimer(Long id) {
        Commande commande = findById(id);
        if (commande.getStatut() == StatutCommande.VALIDEE) {
            throw new IllegalStateException("Impossible de supprimer une commande validée");
        }
        commandeRepository.deleteById(id);
    }

    @Override
    public Commande findById(Long id) {
        return commandeRepository.findById(id)
                .orElseThrow(() -> new RessourceIntrouvableException("Commande non trouvée: " + id));
    }

    @Override
    public List<Commande> findAll() {
        return commandeRepository.findAllByOrderByDateCommandeDesc();
    }

    @Override
    public List<Commande> findByStatut(StatutCommande statut) {
        return commandeRepository.findByStatutOrderByDateCommandeDesc(statut);
    }

    @Override
    @Transactional
    public Commande payerCredit(Long id, Double montant) {
        Commande commande = findById(id);
        if (!Boolean.TRUE.equals(commande.getEstCredit())) {
            throw new IllegalStateException("Cette commande n'est pas un crédit");
        }
        double nouveauVerse = (commande.getMontantVerse() != null ? commande.getMontantVerse() : 0.0) + montant;
        commande.setMontantVerse(Math.min(nouveauVerse, commande.getMontantTotal()));
        commande.recalculer();
        return commandeRepository.save(commande);
    }

    @Override
    @Transactional
    public Commande annuler(Long id, Long utilisateurId) {
        Commande commande = findById(id);
        if (commande.getStatut() == StatutCommande.ANNULEE) {
            throw new IllegalStateException("Cette commande est déjà annulée");
        }
        // Si validée → annuler la vente liée pour remettre le stock
        if (commande.getStatut() == StatutCommande.VALIDEE && commande.getVenteId() != null) {
            venteService.annulerVente(commande.getVenteId(), utilisateurId, "Annulation commande " + commande.getNumeroCommande());
        }
        commande.setStatut(StatutCommande.ANNULEE);
        return commandeRepository.save(commande);
    }

    @Override
    @Transactional
    public List<Commande> payerCreditsGroupes(List<Long> ids, Double montantTotal) {
        List<Commande> commandes = commandeRepository.findAllById(ids);
        double restant = montantTotal;
        for (Commande c : commandes) {
            if (restant <= 0) break;
            double dû = c.getMontantRestant() != null ? c.getMontantRestant() : 0.0;
            if (dû <= 0) continue;
            double paiement = Math.min(dû, restant);
            double nouveauVerse = (c.getMontantVerse() != null ? c.getMontantVerse() : 0.0) + paiement;
            c.setMontantVerse(nouveauVerse);
            c.recalculer();
            restant -= paiement;
        }
        return commandeRepository.saveAll(commandes);
    }

    @Override
    @Transactional
    @CacheEvict(value = "produits", allEntries = true)
    public Commande creerDepuisVitrine(VitrineCommandeRequest request) {
        // Fonctionnalité désactivable par le super admin (Boutique > Paramètres) —
        // vérifié ici (pas seulement masqué côté vitrine), sinon l'API publique
        // reste ouverte même quand l'admin a coché "Vitrine désactivée".
        Boutique boutique = boutiqueRepository.findAll().stream().findFirst().orElse(null);
        if (boutique != null && Boolean.FALSE.equals(boutique.getFeatureVitrineActif())) {
            throw new IllegalStateException("La vitrine en ligne est désactivée pour le moment");
        }

        if (request.getLignes() == null || request.getLignes().isEmpty()) {
            throw new IllegalArgumentException("La commande doit contenir au moins un produit");
        }
        if (request.getClientNom() == null || request.getClientNom().isBlank()
                || request.getClientTelephone() == null || request.getClientTelephone().isBlank()) {
            throw new IllegalArgumentException("Nom et téléphone sont obligatoires");
        }

        Commande commande = new Commande();
        commande.setStatut(StatutCommande.BROUILLON);
        commande.setOrigine(OrigineCommande.VITRINE);
        commande.setCodeSuivi(java.util.UUID.randomUUID().toString().replace("-", ""));
        commande.setVendeur(null);
        commande.setNotes(request.getNotes());
        commande.setAdresseLivraison(request.getAdresseLivraison());
        commande.setEstCredit(false);
        commande.setMontantVerse(0.0);
        // Pas de paiement en ligne : le mode réel n'est connu qu'à la remise. ESPECES
        // par défaut (venteService.creerVente() exige un mode non nul pour valider) —
        // la boutique peut le corriger via "Modifier" avant de valider si le client
        // paie finalement par Mobile Money, carte, etc.
        commande.setModePaiement(ModePaiement.ESPECES);

        // Client existant (même numéro) ou nouvelle fiche — jamais de doublon, jamais
        // de liste séparée : la personne rejoint la même liste Clients que tout le monde.
        Client client = clientRepository.findByNumeroTelephone(request.getClientTelephone().trim())
                .orElseGet(() -> {
                    Client nouveau = new Client();
                    nouveau.setNom(request.getClientNom().trim());
                    nouveau.setPrenom(request.getClientPrenom() != null ? request.getClientPrenom().trim() : "");
                    nouveau.setNumeroTelephone(request.getClientTelephone().trim());
                    nouveau.setDateCreation(LocalDateTime.now());
                    return clientRepository.save(nouveau);
                });
        commande.setClient(client);
        commande.setClientNom(client.getNom());
        commande.setClientPrenom(client.getPrenom());
        commande.setClientTelephone(client.getNumeroTelephone());

        List<LigneCommande> lignes = new ArrayList<>();
        for (VitrineCommandeRequest.Ligne lr : request.getLignes()) {
            if (lr.getQuantite() == null || lr.getQuantite() <= 0) continue;
            Produit produit = produitRepository.findById(lr.getProduitId())
                    .orElseThrow(() -> new RessourceIntrouvableException("Produit non trouvé: " + lr.getProduitId()));
            if (produit.getQuantite() == null || produit.getQuantite() <= 0) {
                throw new IllegalStateException("\"" + produit.getNom() + "\" n'est plus disponible");
            }
            LigneCommande ligne = new LigneCommande();
            ligne.setCommande(commande);
            ligne.setProduit(produit);
            // Vente à la mesure : le client de la vitrine commande en kg/L/m entiers.
            // Endpoint public : quantité plafonnée et calcul sans débordement (une quantité
            // énorme donnait une ligne négative), jamais plus que le stock disponible.
            if (lr.getQuantite() > QUANTITE_MAX_VITRINE) {
                throw new IllegalArgumentException("Quantité trop grande pour \"" + produit.getNom()
                        + "\" (maximum " + QUANTITE_MAX_VITRINE + ").");
            }
            long quantiteStock = produit.getModeMesure() != null
                    ? (long) lr.getQuantite() * produit.getModeMesure().getFacteur() : lr.getQuantite();
            if (quantiteStock > produit.getQuantite()) {
                throw new IllegalStateException("Il n'y a pas assez de \"" + produit.getNom()
                        + "\" pour cette quantité. Réduisez la quantité.");
            }
            ligne.setQuantite(Math.toIntExact(quantiteStock));
            ligne.setPrixUnitaire(produit.getPrixVente());
            ligne.setPrixAchat(produit.getPrixAchat());
            ligne.calculer();
            lignes.add(ligne);
        }
        if (lignes.isEmpty()) {
            throw new IllegalArgumentException("Aucun produit valide dans la commande");
        }
        commande.setLignes(lignes);
        commande.recalculer();

        Commande saved = commandeRepository.save(commande);

        // Notification persistée (visible dans la cloche) + WebSocket temps réel (si l'appli
        // est déjà ouverte, ça arrive instantanément ; sinon la personne la verra en rouvrant
        // l'appli via le contrôle "commandes vitrine en attente" fait à l'ouverture — cf.
        // trouverVitrineEnAttente(), appelé par les 3 plateformes au démarrage/connexion).
        notifService.creer("COMMANDE_VITRINE", "Nouvelle commande en ligne",
                "Commande " + saved.getNumeroCommande() + " de " + client.getNom() + " " + client.getPrenom()
                        + " (" + lignes.size() + " produit(s))", "/pages/commandes");
        stockWebSocketService.diffuserCommandeVitrine(BOUTIQUE_ID, Map.of(
                "type", "COMMANDE_VITRINE",
                "commandeId", saved.getId(),
                "numeroCommande", saved.getNumeroCommande(),
                "clientNom", client.getNom() + " " + client.getPrenom(),
                "timestamp", System.currentTimeMillis()
        ));

        return saved;
    }

    @Override
    public List<Commande> trouverVitrineEnAttente() {
        return commandeRepository.findByOrigineAndStatutOrderByDateCommandeDesc(
                OrigineCommande.VITRINE, StatutCommande.BROUILLON);
    }

    @Override
    @Transactional
    public Commande changerEtapeLivraison(Long id, EtapeLivraison etape) {
        Commande c = commandeRepository.findById(id)
                .orElseThrow(() -> new RessourceIntrouvableException("Commande introuvable"));
        if (c.getStatut() != StatutCommande.VALIDEE) {
            throw new IllegalStateException("Confirmez d'abord la commande avant de la marquer prête ou livrée.");
        }
        c.setEtapeLivraison(etape);
        c.setDateEtapeLivraison(etape == null ? null : LocalDateTime.now());
        return commandeRepository.save(c);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Long> clientsAyantCommandeEnLigne() {
        return commandeRepository.findClientIdsByOrigine(OrigineCommande.VITRINE);
    }

    // ─── Helpers ─────────────────────────────────────────────────────────────

    private void setClient(Commande commande, CommandeRequest request) {
        if (request.getClientId() != null) {
            clientRepository.findById(request.getClientId()).ifPresent(client -> {
                commande.setClient(client);
                commande.setClientNom(client.getNom());
                commande.setClientPrenom(client.getPrenom());
                commande.setClientTelephone(client.getNumeroTelephone());
            });
        } else {
            commande.setClient(null);
            commande.setClientNom(request.getClientNom());
            commande.setClientPrenom(request.getClientPrenom());
            commande.setClientTelephone(request.getClientTelephone());
        }
    }

    private List<LigneCommande> buildLignes(List<LigneCommandeRequest> lignesReq, Commande commande) {
        List<LigneCommande> lignes = new ArrayList<>();
        if (lignesReq == null) return lignes;
        for (LigneCommandeRequest req : lignesReq) {
            Produit produit = produitRepository.findById(req.getProduitId())
                    .orElseThrow(() -> new RessourceIntrouvableException("Produit non trouvé: " + req.getProduitId()));
            LigneCommande ligne = new LigneCommande();
            ligne.setCommande(commande);
            ligne.setProduit(produit);
            ligne.setQuantite(req.getQuantite());
            ligne.setPrixUnitaire(req.getPrixUnitaire() != null ? req.getPrixUnitaire() : produit.getPrixVente());
            ligne.setPrixAchat(produit.getPrixAchat());
            ligne.calculer();
            lignes.add(ligne);
        }
        return lignes;
    }
}
