package com.ges.boutique.commission;

import com.ges.boutique.exception.RessourceIntrouvableException;
import com.ges.boutique.produit.Categorie;
import com.ges.boutique.produit.CategorieRepository;
import com.ges.boutique.produit.Produit;
import com.ges.boutique.produit.ProduitRepository;
import com.ges.boutique.utilisateur.RoleUtilisateur;
import com.ges.boutique.utilisateur.Utilisateur;
import com.ges.boutique.utilisateur.UtilisateurRepository;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Primes sur produits (fonctionnalité PRIMES_PRODUITS). Rien n'est figé en base : les
 * primes gagnées sont recalculées à chaque consultation depuis les lignes de vente, en
 * excluant les ventes annulées et les quantités retournées.
 */
@Service
@RequiredArgsConstructor
public class CommissionService {

    private static final int TAILLE_LOT = 900;

    private final RegleCommissionRepository regleRepository;
    private final PaiementCommissionRepository paiementRepository;
    private final ParametreCommissionRepository parametreRepository;
    private final ProduitRepository produitRepository;
    private final CategorieRepository categorieRepository;
    private final UtilisateurRepository utilisateurRepository;
    private final EntityManager em;

    // ================================================================= paramètres

    public Set<TypeCommission> typesActifs() {
        return parametreRepository.findById(1L)
                .map(ParametreCommission::getTypesActifs)
                .filter(s -> s != null && !s.isBlank())
                .map(s -> Arrays.stream(s.split(","))
                        .map(String::trim)
                        .filter(t -> Arrays.stream(TypeCommission.values()).anyMatch(v -> v.name().equals(t)))
                        .map(TypeCommission::valueOf)
                        .collect(Collectors.toCollection(() -> EnumSet.noneOf(TypeCommission.class))))
                .orElseGet(() -> EnumSet.allOf(TypeCommission.class));
    }

    public Map<String, Object> parametres() {
        Set<TypeCommission> actifs = typesActifs();
        List<Map<String, Object>> types = new ArrayList<>();
        for (TypeCommission t : TypeCommission.values()) {
            types.add(Map.of("type", t.name(), "libelle", t.getLibelle(), "actif", actifs.contains(t)));
        }
        return Map.of("types", types);
    }

    @Transactional
    public Map<String, Object> definirTypesActifs(List<String> types) {
        Set<TypeCommission> choisis = EnumSet.noneOf(TypeCommission.class);
        if (types != null) {
            for (String t : types) {
                try { choisis.add(TypeCommission.valueOf(t)); } catch (IllegalArgumentException ignored) { }
            }
        }
        if (choisis.isEmpty()) {
            throw new IllegalArgumentException("Gardez au moins un type de prime.");
        }
        ParametreCommission p = parametreRepository.findById(1L).orElseGet(ParametreCommission::new);
        p.setId(1L);
        p.setTypesActifs(choisis.stream().map(Enum::name).collect(Collectors.joining(",")));
        parametreRepository.save(p);
        return parametres();
    }

    // ================================================================= règles

    public List<Map<String, Object>> listerRegles() {
        List<RegleCommission> regles = regleRepository.findAllByOrderByDateDebutDescIdDesc();
        Noms noms = chargerNoms(regles);
        return regles.stream().map(r -> regleEnMap(r, noms)).toList();
    }

    @Transactional
    public Map<String, Object> creerRegle(RegleCommissionRequest req) {
        RegleCommission r = new RegleCommission();
        appliquer(r, req);
        r = regleRepository.save(r);
        return regleEnMap(r, chargerNoms(List.of(r)));
    }

    @Transactional
    public Map<String, Object> modifierRegle(Long id, RegleCommissionRequest req) {
        RegleCommission r = regleRepository.findById(id)
                .orElseThrow(() -> new RessourceIntrouvableException("Règle introuvable"));
        appliquer(r, req);
        r = regleRepository.save(r);
        return regleEnMap(r, chargerNoms(List.of(r)));
    }

    /** Arrête une règle à aujourd'hui : les primes déjà gagnées restent dans l'historique. */
    @Transactional
    public Map<String, Object> arreterRegle(Long id) {
        RegleCommission r = regleRepository.findById(id)
                .orElseThrow(() -> new RessourceIntrouvableException("Règle introuvable"));
        LocalDate aujourdhui = LocalDate.now();
        if (r.getDateFin() == null || r.getDateFin().isAfter(aujourdhui)) {
            r.setDateFin(aujourdhui.isBefore(r.getDateDebut()) ? r.getDateDebut() : aujourdhui);
        }
        r = regleRepository.save(r);
        return regleEnMap(r, chargerNoms(List.of(r)));
    }

    @Transactional
    public void supprimerRegle(Long id) {
        if (!regleRepository.existsById(id)) throw new RessourceIntrouvableException("Règle introuvable");
        regleRepository.deleteById(id);
    }

    private void appliquer(RegleCommission r, RegleCommissionRequest req) {
        if (req.getType() == null) throw new IllegalArgumentException("Choisissez le type de prime.");
        if (!typesActifs().contains(req.getType())) {
            throw new IllegalArgumentException("Ce type de prime n'est pas activé pour la boutique.");
        }
        if (req.getProduitId() == null && req.getCategorieId() == null) {
            throw new IllegalArgumentException("Choisissez un produit ou une catégorie.");
        }
        if (req.getProduitId() != null && !produitRepository.existsById(req.getProduitId())) {
            throw new IllegalArgumentException("Produit introuvable.");
        }
        if (req.getProduitId() == null && !categorieRepository.existsById(req.getCategorieId())) {
            throw new IllegalArgumentException("Catégorie introuvable.");
        }
        if (req.getValeur() == null || req.getValeur() <= 0) {
            throw new IllegalArgumentException("Le montant de la prime doit être supérieur à 0.");
        }
        if (req.getType() == TypeCommission.POURCENTAGE && req.getValeur() > 100) {
            throw new IllegalArgumentException("Le pourcentage ne peut pas dépasser 100.");
        }
        LocalDate debut = req.getDateDebut() != null ? req.getDateDebut() : LocalDate.now();
        if (req.getDateFin() != null && req.getDateFin().isBefore(debut)) {
            throw new IllegalArgumentException("La date de fin doit être après la date de début.");
        }
        if (req.getType() == TypeCommission.OBJECTIF) {
            if (req.getQuantiteObjectif() == null || req.getQuantiteObjectif() <= 0) {
                throw new IllegalArgumentException("Indiquez la quantité à vendre pour gagner la prime.");
            }
            if (req.getDateFin() == null) {
                throw new IllegalArgumentException("Une prime sur objectif a besoin d'une date de fin.");
            }
        }
        if (req.getVendeurId() != null && !utilisateurRepository.existsById(req.getVendeurId())) {
            throw new IllegalArgumentException("Vendeur introuvable.");
        }

        r.setType(req.getType());
        r.setProduitId(req.getProduitId());
        r.setCategorieId(req.getProduitId() != null ? null : req.getCategorieId());
        r.setValeur(req.getValeur());
        r.setQuantiteObjectif(req.getType() == TypeCommission.OBJECTIF ? req.getQuantiteObjectif() : null);
        r.setDateDebut(debut);
        r.setDateFin(req.getDateFin());
        r.setVendeurId(req.getVendeurId());
        String nom = req.getNom() != null ? req.getNom().trim() : "";
        r.setNom(nom.isEmpty() ? nomParDefaut(r) : (nom.length() > 120 ? nom.substring(0, 120) : nom));
    }

    private String nomParDefaut(RegleCommission r) {
        String cible = r.getProduitId() != null
                ? produitRepository.findById(r.getProduitId()).map(Produit::getNom).orElse("Produit")
                : categorieRepository.findById(r.getCategorieId()).map(c -> "Catégorie " + c.getNom()).orElse("Catégorie");
        String s = "Prime " + cible;
        return s.length() > 120 ? s.substring(0, 120) : s;
    }

    // ================================================================= paiements

    public List<Map<String, Object>> listerPaiements(Long vendeurId) {
        List<PaiementCommission> liste = vendeurId != null
                ? paiementRepository.findByVendeurIdOrderByDatePaiementDesc(vendeurId)
                : paiementRepository.findAllByOrderByDatePaiementDesc();
        Map<Long, String> noms = nomsVendeurs(liste.stream().map(PaiementCommission::getVendeurId).collect(Collectors.toSet()));
        return liste.stream().map(p -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", p.getId());
            m.put("vendeurId", p.getVendeurId());
            m.put("vendeurNom", noms.getOrDefault(p.getVendeurId(), "Vendeur #" + p.getVendeurId()));
            m.put("montant", p.getMontant());
            m.put("datePaiement", p.getDatePaiement());
            m.put("note", p.getNote());
            m.put("enregistrePar", p.getEnregistrePar());
            return m;
        }).toList();
    }

    @Transactional
    public void enregistrerPaiement(Long vendeurId, Double montant, String note, Utilisateur auteur) {
        if (vendeurId == null || !utilisateurRepository.existsById(vendeurId)) {
            throw new IllegalArgumentException("Vendeur introuvable.");
        }
        if (montant == null || montant <= 0) {
            throw new IllegalArgumentException("Le montant versé doit être supérieur à 0.");
        }
        PaiementCommission p = new PaiementCommission();
        p.setVendeurId(vendeurId);
        p.setMontant(arrondir(montant));
        p.setNote(note != null && note.length() > 255 ? note.substring(0, 255) : note);
        p.setDatePaiement(LocalDateTime.now());
        p.setEnregistrePar(auteur != null ? auteur.getNomComplet() : null);
        paiementRepository.save(p);
    }

    @Transactional
    public void supprimerPaiement(Long id) {
        if (!paiementRepository.existsById(id)) throw new RessourceIntrouvableException("Paiement introuvable");
        paiementRepository.deleteById(id);
    }

    // ================================================================= calcul

    /**
     * Primes par vendeur sur la période [debut, fin] (incluses). vendeurId non null = un
     * seul vendeur (page "Mes primes").
     */
    @Transactional(readOnly = true)
    public Map<String, Object> calculer(LocalDate debut, LocalDate fin, Long vendeurId) {
        if (debut == null || fin == null || fin.isBefore(debut)) {
            throw new IllegalArgumentException("Période invalide.");
        }
        Set<TypeCommission> types = typesActifs();
        List<RegleCommission> regles = regleRepository.findAll().stream()
                .filter(r -> types.contains(r.getType()))
                .filter(r -> vendeurId == null || r.getVendeurId() == null || r.getVendeurId().equals(vendeurId))
                .toList();
        Noms noms = chargerNoms(regles);

        // Gagné sur la période demandée, détaillé par règle.
        Map<Long, Map<Long, Gain>> periode = calculerGains(regles, debut, fin, vendeurId);
        // Gagné depuis toujours, pour le "reste à payer".
        LocalDate origine = regles.stream().map(RegleCommission::getDateDebut).min(LocalDate::compareTo).orElse(debut);
        Map<Long, Map<Long, Gain>> toujours = calculerGains(regles, origine, LocalDate.now().isAfter(fin) ? LocalDate.now() : fin, vendeurId);

        List<PaiementCommission> paiements = vendeurId != null
                ? paiementRepository.findByVendeurIdOrderByDatePaiementDesc(vendeurId)
                : paiementRepository.findAll();
        Map<Long, Double> payeTotal = new HashMap<>();
        Map<Long, Double> payePeriode = new HashMap<>();
        LocalDateTime d = debut.atStartOfDay();
        LocalDateTime f = fin.plusDays(1).atStartOfDay();
        for (PaiementCommission p : paiements) {
            payeTotal.merge(p.getVendeurId(), p.getMontant(), Double::sum);
            if (!p.getDatePaiement().isBefore(d) && p.getDatePaiement().isBefore(f)) {
                payePeriode.merge(p.getVendeurId(), p.getMontant(), Double::sum);
            }
        }

        Set<Long> vendeurs = new LinkedHashSet<>();
        vendeurs.addAll(periode.keySet());
        vendeurs.addAll(toujours.keySet());
        vendeurs.addAll(payeTotal.keySet());
        if (vendeurId != null) {
            vendeurs.clear();
            vendeurs.add(vendeurId);
        }
        Map<Long, String> nomsV = nomsVendeurs(vendeurs);

        List<Map<String, Object>> lignes = new ArrayList<>();
        double totalPeriode = 0, totalReste = 0;
        for (Long v : vendeurs) {
            Map<Long, Gain> parRegle = periode.getOrDefault(v, Map.of());
            double gagne = parRegle.values().stream().mapToDouble(g -> g.montant).sum();
            double gagneToujours = toujours.getOrDefault(v, Map.of()).values().stream().mapToDouble(g -> g.montant).sum();
            double reste = Math.max(0, gagneToujours - payeTotal.getOrDefault(v, 0.0));

            List<Map<String, Object>> details = new ArrayList<>();
            for (RegleCommission r : regles) {
                Gain g = parRegle.get(r.getId());
                if (g == null) continue;
                Map<String, Object> dm = regleEnMap(r, noms);
                dm.put("quantiteVendue", g.quantite);
                dm.put("montantVendu", arrondir(g.chiffreAffaires));
                dm.put("prime", arrondir(g.montant));
                if (r.getType() == TypeCommission.OBJECTIF) {
                    dm.put("objectifAtteint", g.quantite >= r.getQuantiteObjectif());
                }
                details.add(dm);
            }

            Map<String, Object> m = new LinkedHashMap<>();
            m.put("vendeurId", v);
            m.put("vendeurNom", nomsV.getOrDefault(v, "Vendeur #" + v));
            m.put("primePeriode", arrondir(gagne));
            m.put("payePeriode", arrondir(payePeriode.getOrDefault(v, 0.0)));
            m.put("primeTotale", arrondir(gagneToujours));
            m.put("payeTotal", arrondir(payeTotal.getOrDefault(v, 0.0)));
            m.put("resteAPayer", arrondir(reste));
            m.put("details", details);
            lignes.add(m);
            totalPeriode += gagne;
            totalReste += reste;
        }
        lignes.sort((a, b) -> Double.compare((Double) b.get("primePeriode"), (Double) a.get("primePeriode")));

        Map<String, Object> res = new LinkedHashMap<>();
        res.put("debut", debut);
        res.put("fin", fin);
        res.put("totalPeriode", arrondir(totalPeriode));
        res.put("totalResteAPayer", arrondir(totalReste));
        res.put("vendeurs", lignes);
        return res;
    }

    /** Règles en cours qui concernent un vendeur (pour qu'il sache ce qui rapporte). */
    public List<Map<String, Object>> reglesEnCoursPour(Long vendeurId) {
        LocalDate auj = LocalDate.now();
        Set<TypeCommission> types = typesActifs();
        List<RegleCommission> regles = regleRepository.findAllByOrderByDateDebutDescIdDesc().stream()
                .filter(r -> types.contains(r.getType()))
                .filter(r -> r.getVendeurId() == null || r.getVendeurId().equals(vendeurId))
                .filter(r -> !r.getDateDebut().isAfter(auj) && (r.getDateFin() == null || !r.getDateFin().isBefore(auj)))
                .toList();
        Noms noms = chargerNoms(regles);
        Map<Long, Map<Long, Gain>> gains = calculerGains(regles, auj, auj, vendeurId);
        Map<Long, Gain> mesGains = gains.getOrDefault(vendeurId, Map.of());
        return regles.stream().map(r -> {
            Map<String, Object> m = regleEnMap(r, noms);
            if (r.getType() == TypeCommission.OBJECTIF) {
                Gain g = mesGains.get(r.getId());
                long q = g != null ? g.quantite : 0;
                m.put("quantiteVendue", q);
                m.put("objectifAtteint", q >= r.getQuantiteObjectif());
            }
            return m;
        }).toList();
    }

    private static final class Gain {
        long quantite;
        double chiffreAffaires;
        double montant;
    }

    /** vendeur -> (règle -> gain). */
    private Map<Long, Map<Long, Gain>> calculerGains(List<RegleCommission> regles, LocalDate debut, LocalDate fin, Long vendeurId) {
        Map<Long, Map<Long, Gain>> res = new HashMap<>();
        // Fenêtre de chaque règle : l'objectif se juge toujours sur toute sa propre période,
        // les autres types sur l'intersection avec la période demandée.
        Map<Long, LocalDate[]> fenetres = new HashMap<>();
        for (RegleCommission r : regles) {
            LocalDate rd = r.getDateDebut();
            LocalDate rf = r.getDateFin() != null ? r.getDateFin() : fin;
            if (rf.isBefore(debut) || rd.isAfter(fin)) continue;
            if (r.getType() == TypeCommission.OBJECTIF) {
                fenetres.put(r.getId(), new LocalDate[]{rd, rf});
            } else {
                fenetres.put(r.getId(), new LocalDate[]{rd.isAfter(debut) ? rd : debut, rf.isBefore(fin) ? rf : fin});
            }
        }
        if (fenetres.isEmpty()) return res;

        List<RegleCommission> utiles = regles.stream().filter(r -> fenetres.containsKey(r.getId())).toList();
        LocalDate min = fenetres.values().stream().map(w -> w[0]).min(LocalDate::compareTo).get();
        LocalDate max = fenetres.values().stream().map(w -> w[1]).max(LocalDate::compareTo).get();
        Set<Long> produits = utiles.stream().map(RegleCommission::getProduitId).filter(Objects::nonNull).collect(Collectors.toSet());
        Set<Long> categories = utiles.stream().filter(r -> r.getProduitId() == null)
                .map(RegleCommission::getCategorieId).filter(Objects::nonNull).collect(Collectors.toSet());

        List<LigneCalcul> lignes = chargerLignes(min, max, produits, categories, vendeurId);
        for (LigneCalcul l : lignes) {
            LocalDate jour = l.date.toLocalDate();
            for (RegleCommission r : utiles) {
                LocalDate[] w = fenetres.get(r.getId());
                if (jour.isBefore(w[0]) || jour.isAfter(w[1])) continue;
                if (r.getVendeurId() != null && !r.getVendeurId().equals(l.vendeurId)) continue;
                boolean cible = r.getProduitId() != null
                        ? r.getProduitId().equals(l.produitId)
                        : r.getCategorieId() != null && r.getCategorieId().equals(l.categorieId);
                if (!cible) continue;
                Gain g = res.computeIfAbsent(l.vendeurId, k -> new HashMap<>()).computeIfAbsent(r.getId(), k -> new Gain());
                g.quantite += l.quantiteBase;
                g.chiffreAffaires += l.montant;
            }
        }
        for (Map<Long, Gain> parRegle : res.values()) {
            for (Map.Entry<Long, Gain> e : parRegle.entrySet()) {
                RegleCommission r = utiles.stream().filter(x -> x.getId().equals(e.getKey())).findFirst().orElseThrow();
                Gain g = e.getValue();
                g.montant = switch (r.getType()) {
                    case MONTANT_FIXE -> Math.max(0, g.quantite) * r.getValeur();
                    case POURCENTAGE -> Math.max(0, g.chiffreAffaires) * r.getValeur() / 100.0;
                    case OBJECTIF -> g.quantite >= r.getQuantiteObjectif() ? r.getValeur() : 0;
                };
            }
        }
        // Un objectif ne se compte qu'une fois : rattaché à la période qui contient sa date de fin.
        for (Map<Long, Gain> parRegle : res.values()) {
            for (RegleCommission r : utiles) {
                if (r.getType() != TypeCommission.OBJECTIF) continue;
                Gain g = parRegle.get(r.getId());
                if (g != null && (r.getDateFin().isBefore(debut) || r.getDateFin().isAfter(fin))) g.montant = 0;
            }
        }
        return res;
    }

    private record LigneCalcul(Long ligneId, Long vendeurId, Long produitId, Long categorieId,
                               long quantiteBase, double montant, LocalDateTime date) { }

    private List<LigneCalcul> chargerLignes(LocalDate debut, LocalDate fin, Set<Long> produits,
                                            Set<Long> categories, Long vendeurId) {
        // Listes jamais vides dans un IN (sinon erreur SQL) : -1 n'existe pas.
        List<Long> p = produits.isEmpty() ? List.of(-1L) : new ArrayList<>(produits);
        List<Long> c = categories.isEmpty() ? List.of(-1L) : new ArrayList<>(categories);
        String jpql = "SELECT l.id, v.vendeur.id, p.id, c.id, l.quantite, l.niveauFacteur, l.sousTotal, v.dateVente "
                + "FROM LigneVente l JOIN l.vente v JOIN l.produit p LEFT JOIN p.categorie c "
                + "WHERE (v.annulee = false OR v.annulee IS NULL) AND v.vendeur IS NOT NULL "
                + "AND v.dateVente >= :d AND v.dateVente < :f AND (p.id IN :p OR c.id IN :c)"
                + (vendeurId != null ? " AND v.vendeur.id = :v" : "");
        var q = em.createQuery(jpql, Object[].class)
                .setParameter("d", debut.atStartOfDay())
                .setParameter("f", fin.plusDays(1).atStartOfDay())
                .setParameter("p", p)
                .setParameter("c", c);
        if (vendeurId != null) q.setParameter("v", vendeurId);
        List<Object[]> rows = q.getResultList();

        Map<Long, Object[]> retours = chargerRetours(rows.stream().map(r -> (Long) r[0]).toList());
        List<LigneCalcul> res = new ArrayList<>(rows.size());
        for (Object[] r : rows) {
            Long id = (Long) r[0];
            int quantite = r[4] != null ? (Integer) r[4] : 0;
            int facteur = r[5] != null && (Integer) r[5] > 0 ? (Integer) r[5] : 1;
            double montant = r[6] != null ? (Double) r[6] : 0.0;
            Object[] ret = retours.get(id);
            if (ret != null) {
                quantite -= ret[0] != null ? ((Number) ret[0]).intValue() : 0;
                montant -= ret[1] != null ? ((Number) ret[1]).doubleValue() : 0.0;
            }
            res.add(new LigneCalcul(id, (Long) r[1], (Long) r[2], (Long) r[3],
                    (long) quantite * facteur, montant, (LocalDateTime) r[7]));
        }
        return res;
    }

    /** ligneVenteId -> [quantité retournée, montant retourné]. */
    private Map<Long, Object[]> chargerRetours(List<Long> lignes) {
        Map<Long, Object[]> res = new HashMap<>();
        for (int i = 0; i < lignes.size(); i += TAILLE_LOT) {
            List<Long> lot = lignes.subList(i, Math.min(lignes.size(), i + TAILLE_LOT));
            List<Object[]> rows = em.createQuery(
                            "SELECT lr.ligneVenteId, SUM(lr.quantiteRetournee), SUM(lr.sousTotal) "
                                    + "FROM LigneRetourVente lr WHERE lr.ligneVenteId IN :ids GROUP BY lr.ligneVenteId",
                            Object[].class)
                    .setParameter("ids", lot)
                    .getResultList();
            for (Object[] r : rows) res.put((Long) r[0], new Object[]{r[1], r[2]});
        }
        return res;
    }

    // ================================================================= noms

    private record Noms(Map<Long, Produit> produits, Map<Long, String> categories, Map<Long, String> vendeurs) { }

    private Noms chargerNoms(Collection<RegleCommission> regles) {
        Set<Long> pids = regles.stream().map(RegleCommission::getProduitId).filter(Objects::nonNull).collect(Collectors.toSet());
        Set<Long> cids = regles.stream().map(RegleCommission::getCategorieId).filter(Objects::nonNull).collect(Collectors.toSet());
        Set<Long> vids = regles.stream().map(RegleCommission::getVendeurId).filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Long, Produit> produits = produitRepository.findAllById(pids).stream()
                .collect(Collectors.toMap(Produit::getId, x -> x));
        Map<Long, String> categories = categorieRepository.findAllById(cids).stream()
                .collect(Collectors.toMap(Categorie::getId, Categorie::getNom));
        return new Noms(produits, categories, nomsVendeurs(vids));
    }

    private Map<Long, String> nomsVendeurs(Collection<Long> ids) {
        if (ids.isEmpty()) return Map.of();
        return utilisateurRepository.findAllById(ids).stream()
                .collect(Collectors.toMap(Utilisateur::getId, Utilisateur::getNomComplet));
    }

    /** Vendeurs proposés dans le formulaire de règle (tout le personnel actif). */
    public List<Map<String, Object>> vendeursProposes() {
        return utilisateurRepository.findAll().stream()
                .filter(Utilisateur::isActif)
                .filter(u -> !u.isSuperAdmin())
                .sorted(Comparator.comparing((Utilisateur u) -> u.getRole() == RoleUtilisateur.ADMIN)
                        .thenComparing(Utilisateur::getNomComplet, String.CASE_INSENSITIVE_ORDER))
                .map(u -> Map.<String, Object>of("id", u.getId(), "nomComplet", u.getNomComplet()))
                .toList();
    }

    private Map<String, Object> regleEnMap(RegleCommission r, Noms noms) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", r.getId());
        m.put("nom", r.getNom());
        m.put("type", r.getType().name());
        m.put("typeLibelle", r.getType().getLibelle());
        m.put("produitId", r.getProduitId());
        Produit p = r.getProduitId() != null ? noms.produits().get(r.getProduitId()) : null;
        m.put("produitNom", p != null ? p.getNom() : null);
        m.put("uniteBase", p != null && p.getUniteBase() != null ? p.getUniteBase() : "Unité");
        m.put("categorieId", r.getCategorieId());
        m.put("categorieNom", r.getCategorieId() != null ? noms.categories().get(r.getCategorieId()) : null);
        m.put("valeur", r.getValeur());
        m.put("quantiteObjectif", r.getQuantiteObjectif());
        m.put("dateDebut", r.getDateDebut());
        m.put("dateFin", r.getDateFin());
        m.put("vendeurId", r.getVendeurId());
        m.put("vendeurNom", r.getVendeurId() != null ? noms.vendeurs().get(r.getVendeurId()) : null);
        LocalDate auj = LocalDate.now();
        String statut = r.getDateDebut().isAfter(auj) ? "A_VENIR"
                : (r.getDateFin() != null && r.getDateFin().isBefore(auj)) ? "TERMINEE" : "EN_COURS";
        m.put("statut", statut);
        return m;
    }

    private static double arrondir(double v) {
        return Math.round(v * 100.0) / 100.0;
    }
}
