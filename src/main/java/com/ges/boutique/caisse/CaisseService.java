package com.ges.boutique.caisse;

import com.ges.boutique.facture.Facture;
import com.ges.boutique.facture.FactureRequest;
import com.ges.boutique.vente.Vente;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public interface CaisseService {

    // Gestion des caisses
    Caisse creerCaisse(String numeroCaisse);
    Caisse ouvrirCaisse();
    Caisse fermerCaisse(Long utilisateurId);
    Caisse ouvrirCaisse(Long caisseId);
    Caisse fermerCaisse(Long caisseId, Long utilisateurId);
    Caisse getCaisseOuverte();
    Caisse getCaisseOuverte(Long caisseId);
    List<Caisse> obtenirToutesCaisses();
    boolean isCaisseOuverte();

    // Soldes et vérifications
    Double getSoldeActuel();
    Double getSoldeSysteme();
    Caisse verifierCaisse(Double soldeReelSaisi, Long utilisateurId, String observations);
    Map<String, Object> getEcartCaisse();

    // Opérations de caisse
    OperationCaisse entreeCaisse(Double montant, String motif, Long utilisateurId, String modePaiement, String reference);
    OperationCaisse sortieCaisse(Double montant, String motif, Long utilisateurId);
    OperationCaisse enregistrerVente(Vente vente, Long utilisateurId, String modePaiement, String reference);
    OperationCaisse enregistrerVenteCredit(Vente vente, Long utilisateurId, String clientNom, String clientTelephone, LocalDate dateEcheance);
    OperationCaisse reglementCredit(Long venteCreditId, Double montantRegle, Long utilisateurId, String modePaiement, String reference, String motif, String referenceGroupe);
    OperationCaisse annulerVente(Vente vente, Long utilisateurId, String motif);
    OperationCaisse annulerVenteCredit(Vente vente, Long utilisateurId, String motif);

    // Méthodes pour les annulations avec répercussion caisse
    OperationCaisse annulerVenteAvecRepercussion(Vente vente, Long utilisateurId, String motif);
    OperationCaisse annulerVenteCreditAvecRepercussion(Vente vente, Long utilisateurId, String motif);

    // Gestion des crédits
    List<OperationCaisse> getCreditsNonRegles();
    List<OperationCaisse> getCreditsEnRetard();
    Map<String, Object> getSituationCredits();
    List<OperationCaisse> getHistoriqueReglementsCredit(Long venteCreditId);

    // Opérations
    List<OperationCaisse> getOperationsDuJour();
    List<OperationCaisse> getOperationsDeLaSemaine();
    List<OperationCaisse> getOperationsDuMois();
    List<OperationCaisse> getOperationsDeLAnnee();
    List<OperationCaisse> getOperationsParPeriode(LocalDate dateDebut, LocalDate dateFin);

    // Statistiques
    Map<String, Object> getStatistiquesDuJour();
    Map<String, Object> getStatistiquesDeLaSemaine();
    Map<String, Object> getStatistiquesDuMois();
    Map<String, Object> getStatistiquesDeLAnnee();
    Map<String, Object> getStatistiquesParPeriode(LocalDate dateDebut, LocalDate dateFin);
    Map<String, Object> getRevenusEtPertesParPeriode(LocalDate dateDebut, LocalDate dateFin);

    // Rapports PDF
    byte[] genererRapportJournalier(LocalDate date);
    byte[] genererRapportHebdomadaire(LocalDate debutSemaine, LocalDate finSemaine);
    byte[] genererRapportMensuel(int annee, int mois);
    byte[] genererRapportAnnuel(int annee);
    byte[] genererRapportPersonnalise(LocalDate dateDebut, LocalDate dateFin);

    // Ventes comptant/crédit
    Map<String, Object> getVentesComptantDuJour();
    Map<String, Object> getVentesCreditDuJour();
    Map<String, Object> getVentesComptantParPeriode(LocalDate dateDebut, LocalDate dateFin);
    Map<String, Object> getVentesCreditParPeriode(LocalDate dateDebut, LocalDate dateFin);
    Map<String, Object> getStatistiquesVentesComptantCredit();

    // Factures
    Facture creerFacture(FactureRequest request, Long utilisateurId);
    Facture modifierFacture(Long factureId, FactureRequest request);
    Facture obtenirFactureParId(Long factureId);
    List<Facture> obtenirToutesFactures();
    List<Facture> obtenirFacturesParStatut(String statut);
    List<Facture> obtenirFacturesParClient(String clientNom);
    List<Facture> obtenirFacturesParPeriode(LocalDateTime dateDebut, LocalDateTime dateFin);
    void supprimerFacture(Long factureId);
    Facture validerFacture(Long factureId);
    Facture annulerFacture(Long factureId);
    Map<String, Object> getStatistiquesFactures();

    // Transfert bancaire
    Map<String, Object> transfererVersBanque(TransfertCaisseBanqueRequest request);

    // Paiements fournisseurs et avances
    OperationCaisse sortieCaisseFournisseur(Double montant, String motif, Long utilisateurId);
    OperationCaisse sortieCaisseAvance(Double montant, String motif, Long utilisateurId);

    // NOUVELLE METHODE POUR REMBOURSEMENT RETOUR
    OperationCaisse sortieCaisseRemboursementRetour(Double montant, String motif, Long utilisateurId);

    // Paiements employés
    OperationCaisse sortieCaisseEmploye(Double montant, String motif, Long utilisateurId);
    OperationCaisse retourCaisseEmploye(Double montant, String motif, Long utilisateurId);

    // Dépenses
    OperationCaisse sortieCaisseDepense(Double montant, String motif, Long utilisateurId);
    OperationCaisse entreeCaisseDepense(Double montant, String motif, Long utilisateurId);

    // Paiements groupés
    List<Map<String, Object>> getPaiementsGroupes();

    // Annulation de règlement de crédit
    OperationCaisse annulerReglementCredit(Long operationId, Long utilisateurId);
    List<OperationCaisse> getReglementsParPeriode(String dateDebut, String dateFin);

    // Page Paramètres : réinitialisation et suppression historique
    void reinitialiserJour();
    void supprimerHistorique();

    // Réconciliation caisse par vendeur (lecture seule, calcul à la volée)
    List<ReconciliationVendeurDTO> getReconciliationVendeurs(LocalDate date);

    /** Réconciliation + contrôle : total à remettre + autres mouvements = variation du tiroir. */
    java.util.Map<String, Object> getRapportReconciliation(LocalDate date);
}