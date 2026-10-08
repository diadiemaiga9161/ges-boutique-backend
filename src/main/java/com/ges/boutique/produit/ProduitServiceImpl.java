package com.ges.boutique.produit;

import com.ges.boutique.config.NotificationService;
import com.ges.boutique.journalaudit.JournalAuditService;
import com.ges.boutique.journalaudit.TypeActionAudit;
import com.ges.boutique.notification.NotificationPersistanceService;
import com.ges.boutique.exception.RessourceIntrouvableException;
import com.ges.boutique.feature.CleFonctionnalite;
import com.ges.boutique.fournisseur.Fournisseur;
import com.ges.boutique.fournisseur.FournisseurDto;
import com.ges.boutique.fournisseur.FournisseurRepository;
import com.ges.boutique.fournisseur.FournisseurRequest;
import com.ges.boutique.utilisateur.Utilisateur;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ProduitServiceImpl implements ProduitService {

    private final ProduitRepository produitRepository;
    private final CategorieRepository categorieRepository;
    private final FournisseurRepository fournisseurRepository;
    private final ExcelImportService excelImportService;
    private final NotificationService notificationService;
    private final NotificationPersistanceService notifPersistance;
    private final JournalAuditService journalAuditService;
    private final ProduitImageRepository produitImageRepository;
    private final com.ges.boutique.feature.FeatureToggleService featureToggleService;

    @Override
    @Transactional
    @CacheEvict(value = "produits", allEntries = true)
    public Produit creerProduit(ProduitRequest request) {
        validerProduitRequest(request);

        Categorie categorie = categorieRepository.findById(request.getCategorieId())
                .orElseThrow(() -> new RessourceIntrouvableException(
                        "Catégorie non trouvée avec l'ID: " + request.getCategorieId()
                ));

        Produit produit = new Produit();
        produit.setNom(request.getNom().trim());
        produit.setDescription(request.getDescription() != null ? request.getDescription().trim() : "");
        produit.setCategorie(categorie);
        produit.setPrixAchat(request.getPrixAchat());
        produit.setPrixVente(request.getPrixVente());
        produit.setQuantite(request.getQuantite());
        produit.setSeuilAlerte(request.getSeuilAlerte() != null ? request.getSeuilAlerte() : 10);
        produit.setCodeBarre(request.getCodeBarre() != null ? request.getCodeBarre().trim() : "");
        produit.setDateCreation(request.getDateCreation() != null ? request.getDateCreation() : LocalDate.now());
        produit.setDatePeremption(request.getDatePeremption());
        produit.setLotNumber(request.getLotNumber());
        produit.setConditionsStockage(request.getConditionsStockage());
        produit.setPoidsVolume(request.getPoidsVolume());
        produit.setUniteMesure(request.getUniteMesure());
        if (request.getUniteBase() != null) {
            produit.setUniteBase(request.getUniteBase());
        }
        produit.setBio(request.isBio());
        produit.setOrigine(request.getOrigine());
        produit.setTypeVente(request.getTypeVente() != null ? request.getTypeVente() : "DETAIL");
        // Vente à la mesure : seulement si le super admin l'a activée pour la boutique.
        if (request.getModeMesure() != null && featureToggleService.estActive(CleFonctionnalite.VENTE_A_LA_MESURE)) {
            produit.setModeMesure(request.getModeMesure());
            produit.setUniteBase(request.getModeMesure().getPetiteUnite());
        }

        if (request.getFournisseurId() != null) {
            Fournisseur fournisseur = fournisseurRepository.findById(request.getFournisseurId())
                    .orElseThrow(() -> new RessourceIntrouvableException(
                            "Fournisseur non trouvé avec l'ID: " + request.getFournisseurId()
                    ));
            produit.setFournisseur(fournisseur);
        }

        Produit saved = produitRepository.save(produit);
        notificationService.notifierMiseAJourStock(saved.getId(), saved.getNom(), saved.getQuantite());
        notificationService.notifierMiseAJourDashboard();
        verifierAlertesStock(saved);
        return saved;
    }

    @Override
    @Transactional
    @CacheEvict(value = "produits", allEntries = true)
    public Produit modifierProduit(Long id, ProduitRequest request) {
        Produit produit = produitRepository.findById(id)
                .orElseThrow(() -> new RessourceIntrouvableException("Produit non trouvé avec l'ID: " + id));

        if (request.getNom() != null && !request.getNom().trim().isEmpty()) {
            produit.setNom(request.getNom().trim());
        }

        if (request.getDescription() != null) {
            produit.setDescription(request.getDescription().trim());
        }

        if (request.getCategorieId() != null) {
            Categorie categorie = categorieRepository.findById(request.getCategorieId())
                    .orElseThrow(() -> new RessourceIntrouvableException(
                            "Catégorie non trouvée avec l'ID: " + request.getCategorieId()
                    ));
            produit.setCategorie(categorie);
        }

        if (request.getFournisseurId() != null) {
            Fournisseur fournisseur = fournisseurRepository.findById(request.getFournisseurId())
                    .orElseThrow(() -> new RessourceIntrouvableException(
                            "Fournisseur non trouvé avec l'ID: " + request.getFournisseurId()
                    ));
            produit.setFournisseur(fournisseur);
        }

        Double ancienPrixAchat = produit.getPrixAchat();
        Double ancienPrixVente = produit.getPrixVente();

        if (request.getPrixAchat() != null) {
            if (request.getPrixAchat() <= 0) {
                throw new IllegalArgumentException("Le prix d'achat doit être supérieur à 0");
            }
            produit.setPrixAchat(request.getPrixAchat());
        }

        if (request.getPrixVente() != null) {
            if (request.getPrixVente() <= 0) {
                throw new IllegalArgumentException("Le prix de vente doit être supérieur à 0");
            }
            produit.setPrixVente(request.getPrixVente());
        }

        if (request.getQuantite() != null) {
            if (request.getQuantite() < 0) {
                throw new IllegalArgumentException("La quantité ne peut pas être négative");
            }
            produit.setQuantite(request.getQuantite());
        }

        if (request.getSeuilAlerte() != null) {
            produit.setSeuilAlerte(request.getSeuilAlerte());
        }

        if (request.getCodeBarre() != null) {
            produit.setCodeBarre(request.getCodeBarre().trim());
        }

        if (request.getDateCreation() != null) {
            produit.setDateCreation(request.getDateCreation());
        }

        if (request.getDatePeremption() != null) {
            produit.setDatePeremption(request.getDatePeremption());
        }

        if (request.getLotNumber() != null) {
            produit.setLotNumber(request.getLotNumber());
        }

        if (request.getConditionsStockage() != null) {
            produit.setConditionsStockage(request.getConditionsStockage());
        }

        if (request.getPoidsVolume() != null) {
            produit.setPoidsVolume(request.getPoidsVolume());
        }

        if (request.getUniteMesure() != null) {
            produit.setUniteMesure(request.getUniteMesure());
        }

        if (request.getUniteBase() != null) {
            produit.setUniteBase(request.getUniteBase());
        }

        produit.setBio(request.isBio());

        if (request.getOrigine() != null) {
            produit.setOrigine(request.getOrigine());
        }

        if (request.getTypeVente() != null) {
            produit.setTypeVente(request.getTypeVente());
        }

        Produit saved = produitRepository.save(produit);
        notificationService.notifierMiseAJourStock(saved.getId(), saved.getNom(), saved.getQuantite());
        verifierAlertesStock(saved);

        boolean prixAchatChange = request.getPrixAchat() != null && !request.getPrixAchat().equals(ancienPrixAchat);
        boolean prixVenteChange = request.getPrixVente() != null && !request.getPrixVente().equals(ancienPrixVente);
        if (prixAchatChange || prixVenteChange) {
            enregistrerAuditModificationPrix(saved, ancienPrixAchat, ancienPrixVente, prixAchatChange, prixVenteChange);
        }

        return saved;
    }

    private void enregistrerAuditModificationPrix(Produit produit, Double ancienPrixAchat, Double ancienPrixVente,
                                                   boolean prixAchatChange, boolean prixVenteChange) {
        StringBuilder details = new StringBuilder("Produit #" + produit.getId() + " (" + produit.getNom() + ") : ");
        if (prixVenteChange) {
            details.append("prix vente ").append(ancienPrixVente).append(" F -> ").append(produit.getPrixVente()).append(" F");
        }
        if (prixAchatChange) {
            if (prixVenteChange) details.append(" ; ");
            details.append("prix achat ").append(ancienPrixAchat).append(" F -> ").append(produit.getPrixAchat()).append(" F");
        }

        Utilisateur auteur = getUtilisateurCourantAudit();
        journalAuditService.enregistrer(
                auteur != null ? auteur.getId() : null,
                auteur != null ? auteur.getNomComplet() : null,
                TypeActionAudit.MODIFICATION_PRIX_PRODUIT,
                details.toString());
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

    private void verifierAlertesStock(Produit p) {
        if (p.getQuantite() == 0) {
            notifPersistance.ruptureStock(p.getId(), p.getNom());
        } else if (p.estStockFaible()) {
            notifPersistance.stockFaible(p.getId(), p.getNom(), p.getQuantite(), p.getSeuilAlerte());
        }
    }

    @Override
    @Transactional
    @CacheEvict(value = "produits", allEntries = true)
    public void supprimerProduit(Long id) {
        Produit produit = produitRepository.findById(id)
                .orElseThrow(() -> new RessourceIntrouvableException("Produit non trouvé avec l'ID: " + id));
        if (produitRepository.countLignesVenteByProduitId(id) > 0) {
            throw new IllegalStateException("Impossible de supprimer un produit deja utilise dans des ventes");
        }
        notificationService.notifierMiseAJourDashboard();
        // Photo éventuelle (fonctionnalité IMAGES_PRODUITS) : supprimée avec le produit.
        produitImageRepository.deleteByProduitId(id);
        produitRepository.delete(produit);
    }

    @Override
    public Produit obtenirProduitParId(Long id) {
        return produitRepository.findById(id)
                .orElseThrow(() -> new RessourceIntrouvableException("Produit non trouvé avec l'ID: " + id));
    }

    @Override
    @Cacheable("produits")
    public List<Produit> obtenirTousLesProduits() {
        return produitRepository.findAll();
    }

    @Override
    public List<Produit> obtenirProduitsParCategorie(Long categorieId) {
        return produitRepository.findByCategorieId(categorieId);
    }

    @Override
    public List<Produit> obtenirProduitsParFournisseur(Long fournisseurId) {
        return produitRepository.findByFournisseurId(fournisseurId);
    }

    @Override
    public List<Produit> rechercherProduits(String motCle) {
        if (motCle == null || motCle.trim().isEmpty()) {
            return produitRepository.findAll();
        }
        return produitRepository.findByNomContainingIgnoreCase(motCle);
    }

    @Override
    public List<Produit> obtenirProduitsStockFaible() {
        return produitRepository.trouverProduitsStockFaible();
    }

    @Override
    public List<Produit> obtenirProduitsPerimes() {
        return produitRepository.findByDatePeremptionBefore(LocalDate.now());
    }

    @Override
    public List<Produit> obtenirProduitsProchePeremption(int jours) {
        LocalDate dateAlerte = LocalDate.now().plusDays(jours);
        return produitRepository.trouverProduitsProchePeremption(dateAlerte);
    }

    @Override
    public List<Produit> obtenirProduitsBio() {
        return produitRepository.trouverProduitsBio();
    }

    @Override
    public Map<String, Object> obtenirStatistiquesStock() {
        Map<String, Object> statistiques = new HashMap<>();

        Double valeurTotale = produitRepository.getValeurTotaleStock();
        Long produitsStockFaible = produitRepository.compterProduitsStockFaible();
        List<Produit> produitsRupture = produitRepository.trouverProduitsEnRupture();
        List<Produit> produitsPerimes = obtenirProduitsPerimes();
        List<Produit> produitsProchePeremption = obtenirProduitsProchePeremption(7);
        long totalProduits = produitRepository.count();
        long totalFournisseurs = fournisseurRepository.count();

        statistiques.put("valeurTotale", valeurTotale != null ? valeurTotale : 0);
        statistiques.put("produitsStockFaible", produitsStockFaible != null ? produitsStockFaible : 0);
        statistiques.put("produitsRupture", produitsRupture.size());
        statistiques.put("produitsPerimes", produitsPerimes.size());
        statistiques.put("produitsProchePeremption", produitsProchePeremption.size());
        statistiques.put("totalProduits", totalProduits);
        statistiques.put("totalFournisseurs", totalFournisseurs);

        double pourcentageStockFaible = totalProduits > 0 ?
                (produitsStockFaible != null ? produitsStockFaible : 0) * 100.0 / totalProduits : 0;
        double pourcentageRupture = totalProduits > 0 ?
                produitsRupture.size() * 100.0 / totalProduits : 0;
        double pourcentagePerimes = totalProduits > 0 ?
                produitsPerimes.size() * 100.0 / totalProduits : 0;

        statistiques.put("pourcentageStockFaible", Math.round(pourcentageStockFaible * 100.0) / 100.0);
        statistiques.put("pourcentageRupture", Math.round(pourcentageRupture * 100.0) / 100.0);
        statistiques.put("pourcentagePerimes", Math.round(pourcentagePerimes * 100.0) / 100.0);

        return statistiques;
    }

    @Override
    public Produit obtenirProduitParCodeBarre(String codeBarre) {
        List<Produit> produits = produitRepository.findByCodeBarre(codeBarre);
        if (produits.isEmpty()) {
            throw new RessourceIntrouvableException("Produit non trouvé avec le code barre: " + codeBarre);
        }
        return produits.get(0);
    }

    @Override
    @Transactional
    public Fournisseur creerFournisseur(FournisseurRequest request) {
        validerFournisseurRequest(request);

        if (fournisseurRepository.existsByCode(request.getCode())) {
            throw new RuntimeException("Le code fournisseur '" + request.getCode() + "' existe déjà");
        }

        if (fournisseurRepository.existsByNom(request.getNom())) {
            throw new RuntimeException("Le nom du fournisseur '" + request.getNom() + "' existe déjà");
        }

        Fournisseur fournisseur = new Fournisseur();
        fournisseur.setNom(request.getNom().trim());
        fournisseur.setCode(request.getCode().trim().toUpperCase());
        fournisseur.setAdresse(request.getAdresse());
        fournisseur.setTelephone(request.getTelephone());
        fournisseur.setEmail(request.getEmail());
        fournisseur.setSiteWeb(request.getSiteWeb());
        fournisseur.setContactNom(request.getContactNom());
        fournisseur.setContactTelephone(request.getContactTelephone());
        fournisseur.setContactEmail(request.getContactEmail());
        fournisseur.setDescription(request.getDescription());
        fournisseur.setTypeProduits(request.getTypeProduits());
        fournisseur.setConditionsPaiement(request.getConditionsPaiement());
        fournisseur.setDelaiLivraison(request.getDelaiLivraison());
        fournisseur.setNote(request.getNote());
        fournisseur.setActif(request.isActif());

        return fournisseurRepository.save(fournisseur);
    }

    @Override
    @Transactional
    public Fournisseur modifierFournisseur(Long id, FournisseurRequest request) {
        Fournisseur fournisseur = fournisseurRepository.findById(id)
                .orElseThrow(() -> new RessourceIntrouvableException("Fournisseur non trouvé avec l'ID: " + id));

        if (request.getNom() != null && !request.getNom().trim().isEmpty()) {
            fournisseur.setNom(request.getNom().trim());
        }

        if (request.getCode() != null && !request.getCode().trim().isEmpty()) {
            fournisseur.setCode(request.getCode().trim().toUpperCase());
        }

        if (request.getAdresse() != null) {
            fournisseur.setAdresse(request.getAdresse());
        }

        if (request.getTelephone() != null) {
            fournisseur.setTelephone(request.getTelephone());
        }

        if (request.getEmail() != null) {
            fournisseur.setEmail(request.getEmail());
        }

        if (request.getSiteWeb() != null) {
            fournisseur.setSiteWeb(request.getSiteWeb());
        }

        if (request.getContactNom() != null) {
            fournisseur.setContactNom(request.getContactNom());
        }

        if (request.getContactTelephone() != null) {
            fournisseur.setContactTelephone(request.getContactTelephone());
        }

        if (request.getContactEmail() != null) {
            fournisseur.setContactEmail(request.getContactEmail());
        }

        if (request.getDescription() != null) {
            fournisseur.setDescription(request.getDescription());
        }

        if (request.getTypeProduits() != null) {
            fournisseur.setTypeProduits(request.getTypeProduits());
        }

        if (request.getConditionsPaiement() != null) {
            fournisseur.setConditionsPaiement(request.getConditionsPaiement());
        }

        if (request.getDelaiLivraison() != null) {
            fournisseur.setDelaiLivraison(request.getDelaiLivraison());
        }

        if (request.getNote() != null) {
            fournisseur.setNote(request.getNote());
        }

        fournisseur.setActif(request.isActif());

        return fournisseurRepository.save(fournisseur);
    }

    @Override
    @Transactional
    public void supprimerFournisseur(Long id) {
        Fournisseur fournisseur = fournisseurRepository.findById(id)
                .orElseThrow(() -> new RessourceIntrouvableException("Fournisseur non trouvé avec l'ID: " + id));

        long nombreProduits = produitRepository.countByFournisseurId(id);
        if (nombreProduits > 0) {
            throw new RuntimeException("Impossible de supprimer le fournisseur car il a " +
                    nombreProduits + " produit(s) associé(s)");
        }

        fournisseurRepository.delete(fournisseur);

        Utilisateur auteur = getUtilisateurCourantAudit();
        journalAuditService.enregistrer(
                auteur != null ? auteur.getId() : null,
                auteur != null ? auteur.getNomComplet() : null,
                TypeActionAudit.SUPPRESSION_FOURNISSEUR,
                "Fournisseur #" + fournisseur.getId() + " (" + fournisseur.getNom() + ") supprimé");
    }

    @Override
    public Fournisseur obtenirFournisseurParId(Long id) {
        return fournisseurRepository.findById(id)
                .orElseThrow(() -> new RessourceIntrouvableException("Fournisseur non trouvé avec l'ID: " + id));
    }

    @Override
    @Cacheable("fournisseurs")
    public List<Fournisseur> obtenirTousLesFournisseurs() {
        return fournisseurRepository.findAll();
    }

    @Override
    public List<Fournisseur> rechercherFournisseurs(String motCle) {
        if (motCle == null || motCle.trim().isEmpty()) {
            return fournisseurRepository.findAll();
        }
        return fournisseurRepository.findByNomContainingIgnoreCase(motCle);
    }

    @Override
    public List<Fournisseur> obtenirFournisseursActifs() {
        return fournisseurRepository.findByActifTrue();
    }

    @Override
    public Fournisseur obtenirFournisseurParCode(String code) {
        return fournisseurRepository.findByCode(code)
                .orElseThrow(() -> new RessourceIntrouvableException("Fournisseur non trouvé avec le code: " + code));
    }

    // NOUVELLE METHODE AJOUTEE
    @Override
    @Transactional(readOnly = true)
    public int compterProduitsParFournisseur(Long fournisseurId) {
        return (int) produitRepository.countByFournisseurId(fournisseurId);
    }

    @Override
    @Transactional
    public ImportResult importerProduits(MultipartFile file) throws IOException {
        return excelImportService.importerProduits(file);
    }

    @Override
    public byte[] genererTemplateExcel() throws IOException {
        return excelImportService.genererTemplateExcel();
    }

    @Override
    public byte[] exporterProduitsVersExcel() throws IOException {
        return excelImportService.exporterProduitsVersExcel();
    }

    @Override
    public byte[] exporterFournisseursVersExcel() throws IOException {
        return excelImportService.exporterFournisseursVersExcel();
    }

    @Override
    public ProduitDto convertirEnDto(Produit produit) {
        if (produit == null) return null;

        ProduitDto dto = new ProduitDto();
        dto.setId(produit.getId());
        dto.setNom(produit.getNom());
        dto.setDescription(produit.getDescription());

        if (produit.getCategorie() != null) {
            dto.setCategorieId(produit.getCategorie().getId());
            dto.setCategorieNom(produit.getCategorie().getNom());
        }

        if (produit.getFournisseur() != null) {
            dto.setFournisseurId(produit.getFournisseur().getId());
            dto.setFournisseurNom(produit.getFournisseur().getNom());
        }

        dto.setPrixAchat(produit.getPrixAchat());
        dto.setPrixVente(produit.getPrixVente());
        dto.setQuantite(produit.getQuantite());
        dto.setSeuilAlerte(produit.getSeuilAlerte());
        dto.setCodeBarre(produit.getCodeBarre());
        dto.setDateCreation(produit.getDateCreation());
        dto.setDatePeremption(produit.getDatePeremption());
        dto.setLotNumber(produit.getLotNumber());
        dto.setConditionsStockage(produit.getConditionsStockage());
        dto.setPoidsVolume(produit.getPoidsVolume());
        dto.setUniteMesure(produit.getUniteMesure());
        dto.setBio(produit.isBio());
        dto.setOrigine(produit.getOrigine());
        dto.setTypeVente(produit.getTypeVente());
        dto.setImageVersion(produit.getImageVersion());
        dto.setModeMesure(produit.getModeMesure());

        dto.setStockFaible(produit.estStockFaible());
        dto.setPerime(produit.estPerime());
        dto.setProchePeremption(produit.estProchePeremption(7));
        dto.setMarge(produit.getMarge());
        dto.setTauxMarge(produit.getTauxMarge());
        dto.setJoursAvantPeremption(produit.getJoursAvantPeremption());

        // Niveaux de conditionnement
        if (produit.getNiveaux() != null && !produit.getNiveaux().isEmpty()) {
            List<ProduitNiveauDto> niveauxDto = produit.getNiveaux().stream()
                .map(n -> {
                    ProduitNiveauDto nd = new ProduitNiveauDto();
                    nd.setId(n.getId());
                    nd.setNom(n.getNom());
                    nd.setOrdre(n.getOrdre());
                    nd.setFacteur(n.getFacteur());
                    nd.setPrixVente(n.getPrixVente());
                    nd.setPrixAchat(n.getPrixAchat());
                    nd.setStock(n.getStock());
                    nd.setParentId(n.getParentId());
                    return nd;
                })
                .collect(Collectors.toList());
            dto.setNiveaux(niveauxDto);
        }

        return dto;
    }

    @Override
    public List<ProduitDto> convertirListeEnDto(List<Produit> produits) {
        return produits.stream()
                .map(this::convertirEnDto)
                .collect(Collectors.toList());
    }

    @Override
    public FournisseurDto convertirFournisseurEnDto(Fournisseur fournisseur) {
        if (fournisseur == null) return null;

        FournisseurDto dto = new FournisseurDto();
        dto.setId(fournisseur.getId());
        dto.setNom(fournisseur.getNom());
        dto.setCode(fournisseur.getCode());
        dto.setAdresse(fournisseur.getAdresse());
        dto.setTelephone(fournisseur.getTelephone());
        dto.setEmail(fournisseur.getEmail());
        dto.setSiteWeb(fournisseur.getSiteWeb());
        dto.setContactNom(fournisseur.getContactNom());
        dto.setContactTelephone(fournisseur.getContactTelephone());
        dto.setContactEmail(fournisseur.getContactEmail());
        dto.setDescription(fournisseur.getDescription());
        dto.setTypeProduits(fournisseur.getTypeProduits());
        dto.setConditionsPaiement(fournisseur.getConditionsPaiement());
        dto.setDelaiLivraison(fournisseur.getDelaiLivraison());
        dto.setNote(fournisseur.getNote());
        dto.setActif(fournisseur.isActif());

        // CORRECTION: Utiliser la méthode de comptage au lieu de la collection lazy
        int nombreProduits = compterProduitsParFournisseur(fournisseur.getId());
        dto.setNombreProduits((long) nombreProduits);

        return dto;
    }

    @Override
    public List<FournisseurDto> convertirFournisseursEnDto(List<Fournisseur> fournisseurs) {
        return fournisseurs.stream()
                .map(this::convertirFournisseurEnDto)
                .collect(Collectors.toList());
    }

    private void validerProduitRequest(ProduitRequest request) {
        if (request.getCategorieId() == null) {
            throw new IllegalArgumentException("L'ID de la catégorie est requis");
        }

        if (request.getNom() == null || request.getNom().trim().isEmpty()) {
            throw new IllegalArgumentException("Le nom du produit est requis");
        }

        if (request.getPrixAchat() == null || request.getPrixAchat() <= 0) {
            throw new IllegalArgumentException("Le prix d'achat doit être supérieur à 0");
        }

        if (request.getPrixVente() == null || request.getPrixVente() <= 0) {
            throw new IllegalArgumentException("Le prix de vente doit être supérieur à 0");
        }

        if (request.getQuantite() == null || request.getQuantite() < 0) {
            throw new IllegalArgumentException("La quantité ne peut pas être négative");
        }

        if (request.getDatePeremption() != null &&
                request.getDateCreation() != null &&
                request.getDatePeremption().isBefore(request.getDateCreation())) {
            throw new IllegalArgumentException("La date de péremption ne peut pas être antérieure à la date de création");
        }
    }

    private void validerFournisseurRequest(FournisseurRequest request) {
        if (request.getNom() == null || request.getNom().trim().isEmpty()) {
            throw new IllegalArgumentException("Le nom du fournisseur est requis");
        }

        if (request.getCode() == null || request.getCode().trim().isEmpty()) {
            throw new IllegalArgumentException("Le code du fournisseur est requis");
        }
    }
}