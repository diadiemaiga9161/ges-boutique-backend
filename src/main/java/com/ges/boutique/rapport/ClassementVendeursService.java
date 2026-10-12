package com.ges.boutique.rapport;

import com.ges.boutique.caisse.TypeOperationCaisse;
import com.ges.boutique.utilisateur.Utilisateur;
import com.ges.boutique.utilisateur.UtilisateurRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;

/**
 * Classement des vendeurs sur une période (page réservée au gérant).
 * Calculs groupés faits par la base (rapides même avec beaucoup de ventes), avec les règles des
 * autres rapports : vente annulée ou entièrement retournée exclue, chiffre d'affaires et bénéfice
 * nets des retours. Les annulations sont comptées à la date d'annulation, les crédits récupérés
 * sont les règlements encaissés par la personne.
 */
@Service
@RequiredArgsConstructor
public class ClassementVendeursService {

    @PersistenceContext
    private EntityManager em;

    private final UtilisateurRepository utilisateurRepository;

    private static final String VENTE_COMPTEE =
            "(v.annulee = false OR v.annulee IS NULL) AND v.vendeur IS NOT NULL "
            + "AND (COALESCE(v.estRetourne, false) = false OR COALESCE(v.retourPartiel, false) = true) "
            + "AND v.dateVente >= :d AND v.dateVente < :f";

    @Transactional(readOnly = true)
    public Map<String, Object> classement(LocalDate debut, LocalDate fin) {
        LocalDateTime d = debut.atStartOfDay();
        LocalDateTime f = fin.plusDays(1).atStartOfDay();
        long jours = ChronoUnit.DAYS.between(debut, fin) + 1;
        LocalDateTime dPrec = debut.minusDays(jours).atStartOfDay();

        Map<Long, Map<String, Object>> parVendeur = new LinkedHashMap<>();

        for (Object[] r : em.createQuery("SELECT v.vendeur.id, COUNT(v), "
                        + "SUM(v.montantTotal - COALESCE(v.montantRetourne, 0)), "
                        + "SUM(COALESCE(v.beneficeTotal, 0) - COALESCE(v.beneficeRetourne, 0)), "
                        + "SUM(CASE WHEN v.estCredit = true THEN 1 ELSE 0 END), "
                        + "SUM(CASE WHEN v.estCredit = true THEN v.montantTotal ELSE 0 END), "
                        + "SUM(COALESCE(v.montantRemiseTotal, 0)) "
                        + "FROM Vente v WHERE " + VENTE_COMPTEE + " GROUP BY v.vendeur.id", Object[].class)
                .setParameter("d", d).setParameter("f", f).getResultList()) {
            Map<String, Object> m = ligne(parVendeur, (Long) r[0]);
            m.put("nombreVentes", ((Number) r[1]).longValue());
            m.put("chiffreAffaires", arrondi(r[2]));
            m.put("benefice", arrondi(r[3]));
            m.put("nombreCredits", ((Number) r[4]).longValue());
            m.put("montantCredits", arrondi(r[5]));
            m.put("remises", arrondi(r[6]));
        }

        for (Object[] r : em.createQuery("SELECT v.vendeur.id, COUNT(v), SUM(v.montantTotal) FROM Vente v "
                        + "WHERE v.annulee = true AND v.vendeur IS NOT NULL "
                        + "AND v.dateAnnulation >= :d AND v.dateAnnulation < :f GROUP BY v.vendeur.id", Object[].class)
                .setParameter("d", d).setParameter("f", f).getResultList()) {
            Map<String, Object> m = ligne(parVendeur, (Long) r[0]);
            m.put("nombreAnnulations", ((Number) r[1]).longValue());
            m.put("montantAnnulations", arrondi(r[2]));
        }

        for (Object[] r : em.createQuery("SELECT o.utilisateur.id, SUM(o.montant) FROM OperationCaisse o "
                        + "WHERE o.type = :t AND o.annule = false AND (o.venteAnnulee = false OR o.venteAnnulee IS NULL) "
                        + "AND o.utilisateur IS NOT NULL AND o.dateOperation >= :d AND o.dateOperation < :f "
                        + "GROUP BY o.utilisateur.id", Object[].class)
                .setParameter("t", TypeOperationCaisse.REGLEMENT_CREDIT)
                .setParameter("d", d).setParameter("f", f).getResultList()) {
            ligne(parVendeur, (Long) r[0]).put("creditsRecuperes", arrondi(r[1]));
        }

        Map<Long, Double> caPrecedent = new HashMap<>();
        for (Object[] r : em.createQuery("SELECT v.vendeur.id, SUM(v.montantTotal - COALESCE(v.montantRetourne, 0)) "
                        + "FROM Vente v WHERE " + VENTE_COMPTEE + " GROUP BY v.vendeur.id", Object[].class)
                .setParameter("d", dPrec).setParameter("f", d).getResultList()) {
            caPrecedent.put((Long) r[0], arrondi(r[1]));
        }

        Map<Long, Utilisateur> personnes = new HashMap<>();
        utilisateurRepository.findAllById(parVendeur.keySet()).forEach(u -> personnes.put(u.getId(), u));

        List<Map<String, Object>> lignes = new ArrayList<>(parVendeur.values());
        for (Map<String, Object> m : lignes) {
            Long id = (Long) m.get("vendeurId");
            Utilisateur u = personnes.get(id);
            m.put("nom", u != null ? u.getNomComplet() : "Vendeur #" + id);
            m.put("actif", u == null || u.isActif());
            double ca = (Double) m.get("chiffreAffaires");
            long nb = (Long) m.get("nombreVentes");
            m.put("panierMoyen", nb > 0 ? arrondi(ca / nb) : 0.0);
            double prec = caPrecedent.getOrDefault(id, 0.0);
            m.put("chiffreAffairesPrecedent", prec);
            m.put("evolution", prec > 0 ? arrondi((ca - prec) / prec * 100) : null);
        }
        lignes.sort(Comparator.comparingDouble((Map<String, Object> m) -> (Double) m.get("chiffreAffaires")).reversed());
        for (int i = 0; i < lignes.size(); i++) lignes.get(i).put("rang", i + 1);

        Map<String, Object> res = new LinkedHashMap<>();
        res.put("dateDebut", debut.toString());
        res.put("dateFin", fin.toString());
        res.put("vendeurs", lignes);
        res.put("totalChiffreAffaires", arrondi(lignes.stream().mapToDouble(m -> (Double) m.get("chiffreAffaires")).sum()));
        res.put("totalVentes", lignes.stream().mapToLong(m -> (Long) m.get("nombreVentes")).sum());
        return res;
    }

    private static Map<String, Object> ligne(Map<Long, Map<String, Object>> parVendeur, Long id) {
        return parVendeur.computeIfAbsent(id, k -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("vendeurId", k);
            m.put("nombreVentes", 0L);
            m.put("chiffreAffaires", 0.0);
            m.put("benefice", 0.0);
            m.put("nombreCredits", 0L);
            m.put("montantCredits", 0.0);
            m.put("remises", 0.0);
            m.put("nombreAnnulations", 0L);
            m.put("montantAnnulations", 0.0);
            m.put("creditsRecuperes", 0.0);
            return m;
        });
    }

    private static Double arrondi(Object v) {
        double d = v instanceof Number n ? n.doubleValue() : 0.0;
        return BigDecimal.valueOf(d).setScale(0, RoundingMode.HALF_UP).doubleValue();
    }
}
