package com.ges.boutique.devis;

import com.ges.boutique.client.Client;
import com.ges.boutique.client.ClientRepository;
import com.ges.boutique.produit.Produit;
import com.ges.boutique.produit.ProduitRepository;
import com.ges.boutique.utilisateur.Utilisateur;
import com.ges.boutique.vente.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;

/**
 * Devis (pro forma) : préparés par le gérant, imprimés pour le client, puis transformés en
 * vente d'un clic quand le client accepte. Les prix du devis restent ceux du jour du devis
 * jusqu'à sa date de validité.
 */
@Service
@RequiredArgsConstructor
public class DevisService {

    private static final ZoneId ZONE = ZoneId.of("Africa/Bamako");
    private static final int VALIDITE_DEFAUT = 30;

    private final DevisRepository repository;
    private final ProduitRepository produitRepository;
    private final ClientRepository clientRepository;
    private final VenteService venteService;

    // ================================================================= lecture

    public List<Map<String, Object>> lister() {
        return repository.findAllByOrderByDateCreationDesc().stream().map(this::versMap).toList();
    }

    public Map<String, Object> obtenir(Long id) {
        return versMap(trouver(id));
    }

    // ================================================================= écriture

    @Transactional
    public Map<String, Object> enregistrer(Long id, Map<String, Object> corps, Utilisateur auteur) {
        Devis d;
        if (id == null) {
            d = new Devis();
            d.setDateCreation(LocalDateTime.now(ZONE));
            d.setCreePar(auteur.getNomComplet());
        } else {
            d = trouver(id);
            if (!"EN_ATTENTE".equals(d.getStatut())) {
                throw new IllegalStateException("Ce devis ne peut plus être modifié (déjà transformé ou refusé).");
            }
        }

        // Client : fiche existante ou simple nom
        Long clientId = lireLong(corps.get("clientId"));
        if (clientId != null) {
            Client c = clientRepository.findById(clientId).orElseThrow(() -> new IllegalArgumentException("Client introuvable."));
            d.setClientId(c.getId());
            d.setClientNom(((c.getPrenom() == null ? "" : c.getPrenom() + " ") + c.getNom()).trim());
            d.setClientTelephone(c.getNumeroTelephone());
            d.setClientAdresse(c.getAdresse());
        } else {
            String nom = texte(corps.get("clientNom"), 160);
            if (nom == null) throw new IllegalArgumentException("Indiquez le client (ex : École de Kalaban).");
            d.setClientId(null);
            d.setClientNom(nom);
            d.setClientTelephone(texte(corps.get("clientTelephone"), 40));
            d.setClientAdresse(texte(corps.get("clientAdresse"), 200));
        }
        d.setObjet(texte(corps.get("objet"), 200));
        d.setConditions(texte(corps.get("conditions"), 500));

        LocalDate validite = lireDate(corps.get("dateValidite"));
        if (validite == null) validite = LocalDate.now(ZONE).plusDays(VALIDITE_DEFAUT);
        if (validite.isBefore(LocalDate.now(ZONE))) throw new IllegalArgumentException("La date de validité est déjà passée.");
        d.setDateValidite(validite);

        // Lignes
        Object brut = corps.get("lignes");
        if (!(brut instanceof List<?> liste) || liste.isEmpty()) throw new IllegalArgumentException("Ajoutez au moins un produit.");
        if (liste.size() > 200) throw new IllegalArgumentException("Trop de lignes.");
        d.getLignes().clear();
        double total = 0;
        for (Object o : liste) {
            if (!(o instanceof Map<?, ?> l)) continue;
            Long produitId = lireLong(l.get("produitId"));
            Produit p = produitId == null ? null : produitRepository.findById(produitId).orElse(null);
            if (p == null) throw new IllegalArgumentException("Produit introuvable.");
            if (p.getModeMesure() != null) {
                throw new IllegalArgumentException("« " + p.getNom() + " » est vendu au poids : il ne peut pas être mis dans un devis pour l'instant.");
            }
            Long q = lireLong(l.get("quantite"));
            if (q == null || q <= 0 || q > 1_000_000) throw new IllegalArgumentException("Quantité invalide pour « " + p.getNom() + " ».");
            Double prix = lireDouble(l.get("prixUnitaire"));
            if (prix == null) prix = p.getPrixVente();
            if (prix == null || prix < 0) throw new IllegalArgumentException("Prix invalide pour « " + p.getNom() + " ».");
            LigneDevis ld = new LigneDevis();
            ld.setDevis(d);
            ld.setProduitId(p.getId());
            ld.setDesignation(p.getNom());
            ld.setQuantite(q.intValue());
            ld.setPrixUnitaire(arrondir(prix));
            ld.setSousTotal(arrondir(prix * q));
            d.getLignes().add(ld);
            total += ld.getSousTotal();
        }
        Double remise = lireDouble(corps.get("remise"));
        remise = remise == null || remise < 0 ? 0.0 : arrondir(remise);
        if (remise > total) throw new IllegalArgumentException("La remise est plus grande que le total.");
        d.setRemise(remise);
        d.setMontantTotal(arrondir(total - remise));

        if (d.getNumero() == null) d.setNumero(nouveauNumero());
        repository.save(d);
        return versMap(d);
    }

    @Transactional
    public Map<String, Object> refuser(Long id) {
        Devis d = trouver(id);
        if (!"EN_ATTENTE".equals(d.getStatut())) throw new IllegalStateException("Ce devis n'est plus en attente.");
        d.setStatut("REFUSE");
        return versMap(d);
    }

    @Transactional
    public Map<String, Object> prolonger(Long id, int jours) {
        Devis d = trouver(id);
        if (!"EN_ATTENTE".equals(d.getStatut())) throw new IllegalStateException("Ce devis n'est plus en attente.");
        if (jours < 1 || jours > 365) throw new IllegalArgumentException("Nombre de jours invalide.");
        LocalDate base = d.getDateValidite().isBefore(LocalDate.now(ZONE)) ? LocalDate.now(ZONE) : d.getDateValidite();
        d.setDateValidite(base.plusDays(jours));
        return versMap(d);
    }

    @Transactional
    public void supprimer(Long id) {
        Devis d = trouver(id);
        if ("TRANSFORME".equals(d.getStatut())) throw new IllegalStateException("Un devis transformé en vente ne peut pas être supprimé.");
        repository.delete(d);
    }

    /**
     * Le client accepte : le devis devient une vente, aux prix du devis. Par défaut c'est une vente
     * à crédit au nom du client (il paiera plus tard) ; « comptant » si le client paie tout de suite.
     * Le stock baisse et la caisse est mise à jour comme pour toute vente.
     */
    @Transactional
    public Map<String, Object> transformer(Long id, Map<String, Object> corps, Utilisateur vendeur) {
        Devis d = trouver(id);
        if (!"EN_ATTENTE".equals(d.getStatut())) throw new IllegalStateException("Ce devis n'est plus en attente.");
        if (d.getDateValidite().isBefore(LocalDate.now(ZONE))) {
            throw new IllegalStateException("Ce devis a expiré le " + d.getDateValidite() + ". Prolongez-le d'abord (les prix ont pu changer).");
        }
        boolean comptant = Boolean.parseBoolean(String.valueOf(corps.get("comptant")));
        ModePaiement mode;
        try {
            mode = corps.get("modePaiement") == null ? ModePaiement.ESPECES : ModePaiement.valueOf(String.valueOf(corps.get("modePaiement")));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Mode de paiement inconnu.");
        }
        // Virement et chèque ne passent pas par le tiroir : la caisse les compterait à tort.
        if (!EnumSet.of(ModePaiement.ESPECES, ModePaiement.ORANGE_MONEY, ModePaiement.MOOV_MONEY, ModePaiement.WAVE_MONEY).contains(mode)) {
            throw new IllegalArgumentException("Choisissez espèces, Orange Money, Moov Money ou Wave.");
        }

        List<LigneVenteRequest> lignes = new ArrayList<>();
        for (LigneDevis l : d.getLignes()) {
            LigneVenteRequest lv = new LigneVenteRequest();
            lv.setProduitId(l.getProduitId());
            lv.setQuantite(l.getQuantite());
            lv.setPrixUnitaire(l.getPrixUnitaire());
            lignes.add(lv);
        }

        VenteRequest req = comptant ? new VenteRequest() : new VenteCreditRequest();
        req.setVendeurId(vendeur.getId());
        req.setLignes(lignes);
        req.setModePaiement(mode);
        req.setReferencePaiement(texte(corps.get("referencePaiement"), 100));
        if (d.getRemise() != null && d.getRemise() > 0) {
            req.setRemiseGlobale(d.getRemise());
            req.setTypeRemiseGlobale(RemiseType.MONTANT_FIXE);
        }
        if (d.getClientId() != null) {
            req.setClientId(d.getClientId());
        } else {
            req.setCreerClient(true);
        }
        req.setClientDivers(false);
        req.setClientNom(d.getClientNom());
        req.setClientTelephone(d.getClientTelephone());
        req.setClientAdresse(d.getClientAdresse());

        Vente vente;
        if (comptant) {
            req.setEstCredit(false);
            req.setMontantVerse(0.0);
            vente = venteService.creerVente(req);
        } else {
            req.setEstCredit(true);
            LocalDate echeance = lireDate(corps.get("dateEcheance"));
            req.setDateEcheance(echeance != null ? echeance : LocalDate.now(ZONE).plusDays(30));
            Double verse = lireDouble(corps.get("montantVerse"));
            req.setMontantVerse(verse == null || verse < 0 ? 0.0 : verse);
            vente = venteService.creerVenteCredit((VenteCreditRequest) req);
        }
        d.setStatut("TRANSFORME");
        d.setVenteId(vente.getId());
        Map<String, Object> res = versMap(d);
        res.put("numeroVente", vente.getNumeroVente());
        res.put("montantVente", vente.getMontantTotal());
        return res;
    }

    // ================================================================= outils

    private Devis trouver(Long id) {
        return repository.findById(id).orElseThrow(() -> new IllegalArgumentException("Devis introuvable."));
    }

    private String nouveauNumero() {
        int annee = LocalDate.now(ZONE).getYear();
        String prefixe = "DEV-" + annee + "-";
        // Le plus grand numéro de l'année + 1 (jamais le nombre de devis : un devis supprimé
        // ferait sinon redonner un numéro déjà utilisé).
        long n = repository.findTopByNumeroStartingWithOrderByNumeroDesc(prefixe)
                .map(d -> Long.parseLong(d.getNumero().substring(prefixe.length())))
                .orElse(0L) + 1;
        return prefixe + String.format("%04d", n);
    }

    private Map<String, Object> versMap(Devis d) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", d.getId());
        m.put("numero", d.getNumero());
        m.put("clientId", d.getClientId());
        m.put("clientNom", d.getClientNom());
        m.put("clientTelephone", d.getClientTelephone());
        m.put("clientAdresse", d.getClientAdresse());
        m.put("objet", d.getObjet());
        m.put("conditions", d.getConditions());
        m.put("dateCreation", d.getDateCreation());
        m.put("dateValidite", d.getDateValidite());
        boolean expire = "EN_ATTENTE".equals(d.getStatut()) && d.getDateValidite().isBefore(LocalDate.now(ZONE));
        m.put("statut", expire ? "EXPIRE" : d.getStatut());
        m.put("venteId", d.getVenteId());
        m.put("creePar", d.getCreePar());
        double sousTotal = d.getLignes().stream().mapToDouble(LigneDevis::getSousTotal).sum();
        m.put("sousTotal", arrondir(sousTotal));
        m.put("remise", d.getRemise());
        m.put("montantTotal", d.getMontantTotal());
        m.put("lignes", d.getLignes().stream().map(l -> {
            Map<String, Object> x = new LinkedHashMap<>();
            x.put("produitId", l.getProduitId());
            x.put("designation", l.getDesignation());
            x.put("quantite", l.getQuantite());
            x.put("prixUnitaire", l.getPrixUnitaire());
            x.put("sousTotal", l.getSousTotal());
            return x;
        }).toList());
        return m;
    }

    private static double arrondir(double v) {
        return BigDecimal.valueOf(v).setScale(2, RoundingMode.HALF_UP).doubleValue();
    }

    private static String texte(Object v, int max) {
        if (v == null) return null;
        String s = String.valueOf(v).trim();
        if (s.isEmpty() || "null".equals(s)) return null;
        return s.length() > max ? s.substring(0, max) : s;
    }

    private static Long lireLong(Object v) {
        if (v == null || String.valueOf(v).isBlank()) return null;
        try { return (long) Double.parseDouble(String.valueOf(v)); } catch (NumberFormatException e) { return null; }
    }

    private static Double lireDouble(Object v) {
        if (v == null || String.valueOf(v).isBlank()) return null;
        try { return Double.parseDouble(String.valueOf(v).replace(',', '.')); } catch (NumberFormatException e) { return null; }
    }

    private static LocalDate lireDate(Object v) {
        if (v == null || String.valueOf(v).isBlank()) return null;
        try { return LocalDate.parse(String.valueOf(v).trim().substring(0, 10)); }
        catch (RuntimeException e) { throw new IllegalArgumentException("Date invalide."); }
    }
}
