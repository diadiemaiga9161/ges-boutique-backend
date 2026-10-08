package com.ges.boutique.rapport;

import com.ges.boutique.feature.CleFonctionnalite;
import com.ges.boutique.feature.RequireFeature;
import com.ges.boutique.vente.LigneVenteRepository;
import com.ges.boutique.vente.Vente;
import com.ges.boutique.vente.VenteRepository;
import com.ges.boutique.produit.ProduitRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/rapports")
@RequiredArgsConstructor
@RequireFeature(CleFonctionnalite.RAPPORTS)
public class RapportAnalytiqueController {

    private final VenteRepository venteRepository;
    private final LigneVenteRepository ligneVenteRepository;
    private final ProduitRepository produitRepository;

    /**
     * GET /api/rapports/ca-30-jours
     * CA groupé par date sur les 30 derniers jours
     */
    @GetMapping("/ca-30-jours")
    public ResponseEntity<List<Map<String, Object>>> ca30Jours() {
        LocalDateTime debut = LocalDate.now().minusDays(29).atStartOfDay();
        LocalDateTime fin = LocalDateTime.now();
        List<Vente> ventes = venteRepository.findByDateRange(debut, fin);

        // Grouper par date et sommer montantTotal
        Map<String, Double> parDate = new TreeMap<>();
        // Initialiser les 30 derniers jours à 0
        for (int i = 29; i >= 0; i--) {
            String date = LocalDate.now().minusDays(i).format(DateTimeFormatter.ISO_LOCAL_DATE);
            parDate.put(date, 0.0);
        }
        for (Vente v : ventes) {
            if (v.getAnnulee() == null || !v.getAnnulee()) {
                String date = v.getDateVente().toLocalDate().format(DateTimeFormatter.ISO_LOCAL_DATE);
                parDate.merge(date, v.getMontantTotal() != null ? v.getMontantTotal() : 0.0, Double::sum);
            }
        }

        List<Map<String, Object>> result = parDate.entrySet().stream()
            .map(e -> {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("date", e.getKey());
                m.put("ca", e.getValue());
                return m;
            }).collect(Collectors.toList());

        return ResponseEntity.ok(result);
    }

    /**
     * GET /api/rapports/top-produits
     * Top 10 produits les plus vendus sur 30 jours
     */
    @GetMapping("/top-produits")
    public ResponseEntity<List<Map<String, Object>>> topProduits() {
        LocalDateTime debut = LocalDate.now().minusDays(29).atStartOfDay();
        LocalDateTime fin = LocalDateTime.now();
        List<Vente> ventes = venteRepository.findByDateRange(debut, fin);

        // Agréger par nom de produit
        Map<String, double[]> agreg = new LinkedHashMap<>(); // [quantite, ca, prixAchat]
        // Vente à la mesure : quantités en kg (pas en g) pour un classement juste.
        Map<String, com.ges.boutique.produit.ModeMesure> modes = new HashMap<>();
        for (Vente v : ventes) {
            if (v.getAnnulee() != null && v.getAnnulee()) continue;
            if (v.getLignes() == null) continue;
            v.getLignes().forEach(l -> {
                String nom = l.getProduitNom() != null ? l.getProduitNom() :
                    (l.getProduit() != null ? l.getProduit().getNom() : "?");
                agreg.computeIfAbsent(nom, k -> new double[3]);
                if (l.getProduit() != null && l.getProduit().getModeMesure() != null) modes.put(nom, l.getProduit().getModeMesure());
                agreg.get(nom)[0] += com.ges.boutique.produit.ModeMesure.qteAffichee(l.getProduit(), l.getQuantite());
                agreg.get(nom)[1] += l.getSousTotal() != null ? l.getSousTotal() : 0;
                agreg.get(nom)[2] += (l.getPrixAchat() != null ? l.getPrixAchat() : 0)
                    * (l.getQuantite() != null ? l.getQuantite() : 0);
            });
        }

        List<Map<String, Object>> result = agreg.entrySet().stream()
            .sorted((a, b) -> Double.compare(b.getValue()[0], a.getValue()[0]))
            .limit(10)
            .map(e -> {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("produitNom", e.getKey());
                m.put("quantiteVendue", (long) e.getValue()[0]);
                m.put("quantiteTexte", com.ges.boutique.produit.ModeMesure.texteAffiche(modes.get(e.getKey()), e.getValue()[0]));
                m.put("ca", e.getValue()[1]);
                return m;
            }).collect(Collectors.toList());

        return ResponseEntity.ok(result);
    }

    /**
     * GET /api/rapports/ventes-par-heure
     * Ventes groupées par heure sur 30 jours
     */
    @GetMapping("/ventes-par-heure")
    public ResponseEntity<List<Map<String, Object>>> ventesParHeure() {
        LocalDateTime debut = LocalDate.now().minusDays(29).atStartOfDay();
        LocalDateTime fin = LocalDateTime.now();
        List<Vente> ventes = venteRepository.findByDateRange(debut, fin);

        Map<Integer, Long> parHeure = new TreeMap<>();
        for (int h = 0; h < 24; h++) parHeure.put(h, 0L);
        for (Vente v : ventes) {
            if (v.getAnnulee() != null && v.getAnnulee()) continue;
            if (v.getDateVente() != null) {
                int h = v.getDateVente().getHour();
                parHeure.merge(h, 1L, Long::sum);
            }
        }

        List<Map<String, Object>> result = parHeure.entrySet().stream()
            .filter(e -> e.getValue() > 0)
            .map(e -> {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("heure", e.getKey());
                m.put("nbVentes", e.getValue());
                return m;
            }).collect(Collectors.toList());

        return ResponseEntity.ok(result);
    }

    /**
     * GET /api/rapports/ventes-par-vendeur?dateDebut=&dateFin=
     * Historique des ventes par vendeur, groupé par jour, séparé comptant / crédit.
     * Une vente à crédit ne compte dans le CA que pour le montant réellement versé
     * (une vente à crédit sans aucun versement ne contribue pas au CA, mais compte
     * quand même dans le nombre de ventes). Une vente annulée ou totalement retournée
     * est exclue (ni comptée, ni sommée) ; une vente partiellement retournée reste
     * comptée mais avec un CA net des articles rendus.
     */
    @GetMapping("/ventes-par-vendeur")
    public ResponseEntity<List<Map<String, Object>>> ventesParVendeur(
            @RequestParam(required = false) String dateDebut,
            @RequestParam(required = false) String dateFin) {
        LocalDateTime debut = (dateDebut != null && !dateDebut.isBlank())
                ? LocalDate.parse(dateDebut).atStartOfDay()
                : LocalDate.now().minusDays(29).atStartOfDay();
        LocalDateTime fin = (dateFin != null && !dateFin.isBlank())
                ? LocalDate.parse(dateFin).atTime(LocalTime.MAX)
                : LocalDateTime.now();

        List<Vente> ventes = venteRepository.findByDateRange(debut, fin);

        Map<String, Map<String, Object>> parVendeurJour = new LinkedHashMap<>();

        for (Vente v : ventes) {
            if (Boolean.TRUE.equals(v.getAnnulee())) continue;
            // Vente totalement retournée = aucune vente nette pour le vendeur, exclue
            // exactement comme une vente annulée. Une vente partiellement retournée
            // reste comptée (le vendeur a bien vendu quelque chose), mais avec un
            // chiffre d'affaires net des articles rendus (voir montantTotal plus bas).
            if (Boolean.TRUE.equals(v.getEstRetourne()) && !Boolean.TRUE.equals(v.getRetourPartiel())) continue;
            if (v.getVendeur() == null || v.getDateVente() == null) continue;

            String date = v.getDateVente().toLocalDate().format(DateTimeFormatter.ISO_LOCAL_DATE);
            Long vendeurId = v.getVendeur().getId();
            String vendeurNom = v.getVendeur().getNomComplet();
            String cle = vendeurId + "_" + date;

            Map<String, Object> ligne = parVendeurJour.computeIfAbsent(cle, k -> {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("vendeurId", vendeurId);
                m.put("vendeurNom", vendeurNom);
                m.put("date", date);
                m.put("nbVentesComptant", 0L);
                m.put("nbVentesCredit", 0L);
                m.put("caComptant", 0.0);
                m.put("caCredit", 0.0);
                return m;
            });

            boolean estCredit = Boolean.TRUE.equals(v.getEstCredit());
            // CA net des retours partiels (montantMarchandiseRetournee = valeur de ce
            // qui a été rendu, cf. Vente.java) — mêmes règles que les autres rapports.
            double montantRetourneCA = v.getMontantMarchandiseRetournee() != null ? v.getMontantMarchandiseRetournee() : 0.0;
            double montantTotal = (v.getMontantTotal() != null ? v.getMontantTotal() : 0.0) - montantRetourneCA;
            double montantVerse = v.getMontantVerse() != null ? v.getMontantVerse() : 0.0;

            if (estCredit) {
                ligne.put("nbVentesCredit", (Long) ligne.get("nbVentesCredit") + 1);
                ligne.put("caCredit", (Double) ligne.get("caCredit") + montantVerse);
            } else {
                ligne.put("nbVentesComptant", (Long) ligne.get("nbVentesComptant") + 1);
                ligne.put("caComptant", (Double) ligne.get("caComptant") + montantTotal);
            }
        }

        List<Map<String, Object>> result = new ArrayList<>();
        for (Map<String, Object> ligne : parVendeurJour.values()) {
            double caComptant = (Double) ligne.get("caComptant");
            double caCredit = (Double) ligne.get("caCredit");
            long nbComptant = (Long) ligne.get("nbVentesComptant");
            long nbCredit = (Long) ligne.get("nbVentesCredit");
            ligne.put("caTotal", caComptant + caCredit);
            ligne.put("nbVentesTotal", nbComptant + nbCredit);
            result.add(ligne);
        }

        result.sort((a, b) -> {
            int cmpDate = ((String) b.get("date")).compareTo((String) a.get("date"));
            if (cmpDate != 0) return cmpDate;
            return ((String) a.get("vendeurNom")).compareTo((String) b.get("vendeurNom"));
        });

        return ResponseEntity.ok(result);
    }

    /**
     * GET /api/rapports/complet?dateDebut=&dateFin=
     * Endpoint UNIQUE regroupant tout ce qu'un rapport PDF (journalier/hebdomadaire/
     * mensuel/annuel/personnalisé — la période est décidée par l'appelant via
     * dateDebut/dateFin, cet endpoint n'impose aucune fenêtre fixe) doit pouvoir afficher :
     * liste des ventes, produits les plus vendus, répartition par mode de paiement, résumé
     * des crédits, nombre de clients servis. Construit une fois ici pour que les 3 fronts
     * (Angular/Ionic/RN) génèrent un PDF avec exactement le même contenu, sans que chacun
     * ré-implémente sa propre agrégation (risque d'incohérence entre plateformes).
     */
    @GetMapping("/complet")
    public ResponseEntity<Map<String, Object>> rapportComplet(
            @RequestParam String dateDebut,
            @RequestParam String dateFin) {
        LocalDateTime debut = LocalDate.parse(dateDebut).atStartOfDay();
        LocalDateTime fin = LocalDate.parse(dateFin).atTime(LocalTime.MAX);

        List<Vente> ventes = venteRepository.findByDateRange(debut, fin).stream()
                .filter(v -> v.getAnnulee() == null || !v.getAnnulee())
                .collect(Collectors.toList());

        // ---- Liste des ventes (comme le PDF actuel) ----
        List<Map<String, Object>> listeVentes = ventes.stream()
                .sorted(Comparator.comparing(Vente::getDateVente, Comparator.nullsLast(Comparator.naturalOrder())))
                .map(v -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("date", v.getDateVente());
                    m.put("numeroVente", v.getNumeroVente());
                    m.put("clientNom", v.getClientNom());
                    m.put("modePaiement", v.getModePaiement() != null ? v.getModePaiement().name() : null);
                    m.put("montantTotal", v.getMontantTotal());
                    return m;
                }).collect(Collectors.toList());
        double totalVentes = ventes.stream().mapToDouble(v -> v.getMontantTotal() != null ? v.getMontantTotal() : 0.0).sum();

        // ---- Produits les plus vendus SUR CETTE PÉRIODE PRÉCISE ----
        Map<String, double[]> agregProduits = new LinkedHashMap<>(); // [quantite, ca]
        Map<String, com.ges.boutique.produit.ModeMesure> modesProduits = new HashMap<>();
        for (Vente v : ventes) {
            if (v.getLignes() == null) continue;
            v.getLignes().forEach(l -> {
                String nom = l.getProduitNom() != null ? l.getProduitNom() :
                        (l.getProduit() != null ? l.getProduit().getNom() : "?");
                double[] agg = agregProduits.computeIfAbsent(nom, k -> new double[2]);
                if (l.getProduit() != null && l.getProduit().getModeMesure() != null) modesProduits.put(nom, l.getProduit().getModeMesure());
                agg[0] += com.ges.boutique.produit.ModeMesure.qteAffichee(l.getProduit(), l.getQuantite());
                agg[1] += l.getSousTotal() != null ? l.getSousTotal() : 0;
            });
        }
        List<Map<String, Object>> topProduits = agregProduits.entrySet().stream()
                .sorted((a, b) -> Double.compare(b.getValue()[0], a.getValue()[0]))
                .limit(15)
                .map(e -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("produitNom", e.getKey());
                    m.put("quantiteVendue", (long) e.getValue()[0]);
                    m.put("quantiteTexte", com.ges.boutique.produit.ModeMesure.texteAffiche(modesProduits.get(e.getKey()), e.getValue()[0]));
                    m.put("ca", e.getValue()[1]);
                    return m;
                }).collect(Collectors.toList());

        // ---- Répartition par mode de paiement ----
        Map<String, double[]> agregModes = new LinkedHashMap<>(); // [montant, nombre]
        for (Vente v : ventes) {
            String mode = v.getModePaiement() != null ? v.getModePaiement().name() : "INCONNU";
            double[] agg = agregModes.computeIfAbsent(mode, k -> new double[2]);
            agg[0] += v.getMontantTotal() != null ? v.getMontantTotal() : 0.0;
            agg[1] += 1;
        }
        List<Map<String, Object>> repartitionModePaiement = agregModes.entrySet().stream()
                .sorted((a, b) -> Double.compare(b.getValue()[0], a.getValue()[0]))
                .map(e -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("mode", e.getKey());
                    m.put("montant", e.getValue()[0]);
                    m.put("nombre", (long) e.getValue()[1]);
                    return m;
                }).collect(Collectors.toList());

        // ---- Résumé crédits (créés sur la période) ----
        List<Vente> credits = ventes.stream().filter(v -> Boolean.TRUE.equals(v.getEstCredit())).collect(Collectors.toList());
        double totalCredits = credits.stream().mapToDouble(v -> v.getMontantTotal() != null ? v.getMontantTotal() : 0.0).sum();
        double totalVerseCredits = credits.stream().mapToDouble(v -> v.getMontantVerse() != null ? v.getMontantVerse() : 0.0).sum();
        Map<String, Object> resumeCredits = new LinkedHashMap<>();
        resumeCredits.put("nombreCredits", credits.size());
        resumeCredits.put("totalCredits", totalCredits);
        resumeCredits.put("totalVerse", totalVerseCredits);
        resumeCredits.put("totalRestant", totalCredits - totalVerseCredits);

        // ---- Nombre de clients distincts servis ----
        long nombreClients = ventes.stream()
                .map(v -> v.getClientId() != null ? "C" + v.getClientId() : v.getClientNom())
                .filter(Objects::nonNull)
                .distinct()
                .count();

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("dateDebut", dateDebut);
        result.put("dateFin", dateFin);
        result.put("nombreVentes", ventes.size());
        result.put("totalVentes", totalVentes);
        result.put("ventes", listeVentes);
        result.put("topProduits", topProduits);
        result.put("repartitionModePaiement", repartitionModePaiement);
        result.put("resumeCredits", resumeCredits);
        result.put("nombreClients", nombreClients);
        return ResponseEntity.ok(result);
    }

    /**
     * GET /api/rapports/marges
     * Marges par produit sur 30 jours
     */
    @GetMapping("/marges")
    public ResponseEntity<List<Map<String, Object>>> marges() {
        LocalDateTime debut = LocalDate.now().minusDays(29).atStartOfDay();
        LocalDateTime fin = LocalDateTime.now();
        List<Vente> ventes = venteRepository.findByDateRange(debut, fin);

        Map<String, double[]> agreg = new LinkedHashMap<>();
        for (Vente v : ventes) {
            if (v.getAnnulee() != null && v.getAnnulee()) continue;
            if (v.getLignes() == null) continue;
            v.getLignes().forEach(l -> {
                String nom = l.getProduitNom() != null ? l.getProduitNom() :
                    (l.getProduit() != null ? l.getProduit().getNom() : "?");
                agreg.computeIfAbsent(nom, k -> new double[2]); // [ca, coutAchat]
                agreg.get(nom)[0] += l.getSousTotal() != null ? l.getSousTotal() : 0;
                agreg.get(nom)[1] += (l.getPrixAchat() != null ? l.getPrixAchat() : 0)
                    * (l.getQuantite() != null ? l.getQuantite() : 0);
            });
        }

        List<Map<String, Object>> result = agreg.entrySet().stream()
            .sorted((a, b) -> Double.compare(b.getValue()[0], a.getValue()[0]))
            .map(e -> {
                double ca = e.getValue()[0];
                double cout = e.getValue()[1];
                double marge = ca - cout;
                double taux = ca > 0 ? Math.round(marge / ca * 100) : 0;
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("produitNom", e.getKey());
                m.put("ca", ca);
                m.put("coutAchat", cout);
                m.put("marge", marge);
                m.put("tauxMarge", taux);
                return m;
            }).collect(Collectors.toList());

        return ResponseEntity.ok(result);
    }
}
