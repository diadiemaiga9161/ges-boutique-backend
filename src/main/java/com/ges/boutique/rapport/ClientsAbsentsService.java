package com.ges.boutique.rapport;

import com.ges.boutique.client.Client;
import com.ges.boutique.client.ClientRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Bons clients qui ne viennent plus : clients enregistrés qui achetaient souvent (au moins
 * {@code minAchats} achats sur les {@code periodeJours} derniers jours) et qu'on n'a pas revus
 * depuis {@code jours} jours. Lecture seule : rien n'est modifié.
 */
@Service
@RequiredArgsConstructor
public class ClientsAbsentsService {

    static final int JOURS_DEFAUT = 14;
    static final int MIN_ACHATS_DEFAUT = 4;
    static final int PERIODE_DEFAUT = 90;
    private static final int MAX_RESULTATS = 200;
    private static final String CLIENT_DIVERS = "client divers";

    @PersistenceContext
    private EntityManager em;

    private final ClientRepository clientRepository;

    @Transactional(readOnly = true)
    public Map<String, Object> lister(int jours, int minAchats, int periodeJours) {
        LocalDateTime maintenant = LocalDateTime.now();
        LocalDateTime depuis = maintenant.minusDays(periodeJours);
        LocalDateTime limite = maintenant.minusDays(jours);

        // [clientId, nombre d'achats, montant, première visite, dernière visite] sur la période
        List<Object[]> lignes = em.createQuery(
                "SELECT v.client.id, COUNT(v), COALESCE(SUM(v.montantTotal), 0), MIN(v.dateVente), MAX(v.dateVente) "
                        + "FROM Vente v WHERE v.client IS NOT NULL AND (v.annulee IS NULL OR v.annulee = false) "
                        + "AND (v.clientDivers IS NULL OR v.clientDivers = false) AND v.dateVente >= :depuis "
                        + "GROUP BY v.client.id", Object[].class)
                .setParameter("depuis", depuis)
                .getResultList();

        List<Object[]> absents = lignes.stream()
                .filter(l -> ((Number) l[1]).longValue() >= minAchats && ((LocalDateTime) l[4]).isBefore(limite))
                .sorted(Comparator.comparingDouble((Object[] l) -> ((Number) l[2]).doubleValue()).reversed())
                .limit(MAX_RESULTATS)
                .toList();

        Map<Long, Client> clients = clientRepository.findAllById(
                        absents.stream().map(l -> ((Number) l[0]).longValue()).toList()).stream()
                .collect(Collectors.toMap(Client::getId, Function.identity()));
        Map<Long, List<String>> produits = produitsHabituels(clients.keySet(), depuis);

        List<Map<String, Object>> resultat = new ArrayList<>();
        for (Object[] l : absents) {
            Long id = ((Number) l[0]).longValue();
            Client c = clients.get(id);
            if (c == null || (c.getNom() != null && c.getNom().trim().equalsIgnoreCase(CLIENT_DIVERS))) continue;
            long nb = ((Number) l[1]).longValue();
            double montant = ((Number) l[2]).doubleValue();
            LocalDateTime premiere = (LocalDateTime) l[3];
            LocalDateTime derniere = (LocalDateTime) l[4];
            long intervalle = nb > 1 ? Math.max(1, Duration.between(premiere, derniere).toDays() / (nb - 1)) : 0;

            Map<String, Object> m = new LinkedHashMap<>();
            m.put("clientId", id);
            m.put("nom", nomComplet(c));
            m.put("telephone", c.getNumeroTelephone());
            m.put("nbAchats", nb);
            m.put("montantTotal", Math.round(montant));
            m.put("panierMoyen", Math.round(montant / nb));
            m.put("derniereVisite", derniere.toLocalDate());
            m.put("joursAbsence", Duration.between(derniere, maintenant).toDays());
            m.put("intervalleHabituel", intervalle);
            m.put("produits", produits.getOrDefault(id, List.of()));
            resultat.add(m);
        }

        Map<String, Object> res = new LinkedHashMap<>();
        res.put("jours", jours);
        res.put("minAchats", minAchats);
        res.put("periodeJours", periodeJours);
        res.put("total", resultat.size());
        res.put("montantEnJeu", resultat.stream().mapToLong(m -> (Long) m.get("panierMoyen")).sum());
        res.put("clients", resultat);
        return res;
    }

    /** Les 3 produits que chaque client achetait le plus sur la période. */
    private Map<Long, List<String>> produitsHabituels(Set<Long> clientIds, LocalDateTime depuis) {
        if (clientIds.isEmpty()) return Map.of();
        List<Object[]> lignes = em.createQuery(
                "SELECT v.client.id, p.nom, SUM(l.quantite) FROM LigneVente l JOIN l.vente v JOIN l.produit p "
                        + "WHERE v.client.id IN :ids AND (v.annulee IS NULL OR v.annulee = false) AND v.dateVente >= :depuis "
                        + "GROUP BY v.client.id, p.nom", Object[].class)
                .setParameter("ids", clientIds)
                .setParameter("depuis", depuis)
                .getResultList();
        Map<Long, List<Object[]>> parClient = lignes.stream()
                .collect(Collectors.groupingBy(l -> ((Number) l[0]).longValue()));
        Map<Long, List<String>> res = new HashMap<>();
        parClient.forEach((id, liste) -> res.put(id, liste.stream()
                .sorted(Comparator.comparingLong((Object[] l) -> ((Number) l[2]).longValue()).reversed())
                .limit(3)
                .map(l -> (String) l[1])
                .toList()));
        return res;
    }

    private static String nomComplet(Client c) {
        String prenom = c.getPrenom() == null ? "" : c.getPrenom().trim();
        String nom = c.getNom() == null ? "" : c.getNom().trim();
        String complet = (prenom + " " + nom).trim();
        return complet.isEmpty() ? "Client" : complet;
    }
}
