package com.ges.boutique.commande;

import com.ges.boutique.vitrine.VitrineCommandeRequest;

import java.util.List;

public interface CommandeService {
    Commande creer(CommandeRequest request);
    Commande modifier(Long id, CommandeRequest request);
    /** infosLivraison est optionnel (peut être null) — une commande retirée en magasin n'a besoin d'aucun de ses champs. */
    Commande valider(Long id, Long currentUserId, ValidationCommandeRequest infosLivraison);
    void supprimer(Long id);
    Commande findById(Long id);
    List<Commande> findAll();
    List<Commande> findByStatut(StatutCommande statut);
    Commande payerCredit(Long id, Double montant);
    List<Commande> payerCreditsGroupes(List<Long> ids, Double montantTotal);
    Commande annuler(Long id, Long utilisateurId);

    /** Commande confirmée → Prête → Livrée (null = revenir à « confirmée »). */
    Commande changerEtapeLivraison(Long id, EtapeLivraison etape);

    /** Commande publique déposée depuis la vitrine (sans connexion) — cf. VitrineController. */
    Commande creerDepuisVitrine(VitrineCommandeRequest request);

    /** Commandes vitrine pas encore traitées — pour le badge/popup "commandes en attente". */
    List<Commande> trouverVitrineEnAttente();

    /** Clients ayant déjà commandé depuis la vitrine (badge « en ligne » de la liste Clients). */
    List<Long> clientsAyantCommandeEnLigne();
}
