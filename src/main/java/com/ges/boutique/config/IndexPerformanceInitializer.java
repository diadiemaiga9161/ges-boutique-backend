package com.ges.boutique.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Index sur les colonnes utilisées par les recherches par période, par client ou par vendeur.
 * Sans eux, MySQL relit toute la table (toutes les ventes depuis l'ouverture) à chaque recherche :
 * invisible au début, de plus en plus lent quand la boutique grossit.
 *
 * Créés au démarrage, dans un fil à part pour ne pas retarder l'ouverture de l'application
 * (MySQL 8 crée un index sans bloquer les lectures ni les écritures). Un index déjà présent,
 * une table ou une colonne absente sont ignorés ; une erreur est notée dans le journal
 * sans jamais empêcher la boutique de fonctionner.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class IndexPerformanceInitializer {

    private final JdbcTemplate jdbcTemplate;

    private record IndexVoulu(String table, String nom, List<String> colonnes) {}

    private static final List<IndexVoulu> INDEX = List.of(
            new IndexVoulu("ventes", "idx_ventes_date_vente", List.of("date_vente")),
            new IndexVoulu("ventes", "idx_ventes_client", List.of("client_id")),
            new IndexVoulu("ventes", "idx_ventes_vendeur", List.of("vendeur_id")),
            new IndexVoulu("ventes", "idx_ventes_credit", List.of("est_credit", "credit_regle")),
            new IndexVoulu("ventes", "idx_ventes_date_reglement", List.of("date_reglement")),
            new IndexVoulu("ventes", "idx_ventes_date_annulation", List.of("date_annulation")),
            new IndexVoulu("operations_caisse", "idx_operations_caisse_date", List.of("date_operation")),
            new IndexVoulu("mouvements_stock", "idx_mouvements_stock_date", List.of("date_mouvement")),
            new IndexVoulu("journal_audit", "idx_journal_audit_date", List.of("date_action")),
            new IndexVoulu("commandes", "idx_commandes_date", List.of("date_commande")),
            new IndexVoulu("retours_vente", "idx_retours_vente_date", List.of("date_retour")),
            new IndexVoulu("achats_fournisseur", "idx_achats_fournisseur_date", List.of("date_achat")),
            new IndexVoulu("paiements_fournisseur", "idx_paiements_fournisseur_date", List.of("date_paiement")),
            new IndexVoulu("operations_compte", "idx_operations_compte_date", List.of("date_operation")),
            new IndexVoulu("paiements_employe", "idx_paiements_employe_date", List.of("date_paiement")),
            new IndexVoulu("dettes_anciennes", "idx_dettes_anciennes_client", List.of("client_id")),
            new IndexVoulu("reglements_dettes_anciennes", "idx_reglements_dettes_date", List.of("date_reglement")),
            new IndexVoulu("mouvement_fidelite", "idx_mouvement_fidelite_client", List.of("client_id"))
    );

    @EventListener(ApplicationReadyEvent.class)
    public void creerIndexManquants() {
        Thread fil = new Thread(this::creerTous, "index-performance");
        fil.setDaemon(true);
        fil.start();
    }

    private void creerTous() {
        for (IndexVoulu index : INDEX) {
            try {
                creerSiAbsent(index);
            } catch (Exception e) {
                log.warn("Index {} non créé sur {} : {}", index.nom(), index.table(), e.getMessage());
            }
        }
    }

    private void creerSiAbsent(IndexVoulu index) {
        Integer colonnesPresentes = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = DATABASE() "
                        + "AND table_name = ? AND column_name IN (" + marqueurs(index.colonnes().size()) + ")",
                Integer.class, parametres(index));
        if (colonnesPresentes == null || colonnesPresentes < index.colonnes().size()) return;

        // Déjà couvert : même nom, ou un autre index qui commence par la même colonne.
        Integer existant = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.statistics WHERE table_schema = DATABASE() "
                        + "AND table_name = ? AND (index_name = ? OR (seq_in_index = 1 AND column_name = ?))",
                Integer.class, index.table(), index.nom(), index.colonnes().get(0));
        if (existant != null && existant > 0) return;

        long debut = System.currentTimeMillis();
        jdbcTemplate.execute("CREATE INDEX " + index.nom() + " ON " + index.table()
                + " (" + String.join(", ", index.colonnes()) + ")");
        log.info("Index {} créé sur {} en {} ms", index.nom(), index.table(), System.currentTimeMillis() - debut);
    }

    private static String marqueurs(int n) {
        return String.join(", ", java.util.Collections.nCopies(n, "?"));
    }

    private static Object[] parametres(IndexVoulu index) {
        Object[] p = new Object[index.colonnes().size() + 1];
        p[0] = index.table();
        for (int i = 0; i < index.colonnes().size(); i++) p[i + 1] = index.colonnes().get(i);
        return p;
    }
}
