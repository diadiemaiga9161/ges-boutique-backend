-- Migration additive : index de performance
-- Aucun DROP, aucune modification de données existantes.
-- MySQL ne supporte pas "CREATE INDEX IF NOT EXISTS" (contrairement a MariaDB/Postgres) :
-- une syntaxe deja invalide ici faisait echouer cette migration sur toute base neuve
-- (jamais remarque avant car les boutiques existantes etaient toutes baselinees apres
-- cette version, donc Flyway ne l'executait jamais chez elles). L'idempotence par
-- execution unique est de toute facon deja garantie par Flyway (table schema_history),
-- pas besoin de IF NOT EXISTS au niveau SQL.

-- Table : ventes
CREATE INDEX idx_vente_date     ON ventes(date_vente);
CREATE INDEX idx_vente_statut   ON ventes(annulee, est_credit, credit_regle);
CREATE INDEX idx_vente_vendeur  ON ventes(vendeur_id);
CREATE INDEX idx_vente_client   ON ventes(client_id);

-- Table : lignes_vente
CREATE INDEX idx_ligne_vente_produit ON lignes_vente(produit_id);
CREATE INDEX idx_ligne_vente_vente   ON lignes_vente(vente_id);

-- Table : produits
CREATE INDEX idx_produit_quantite    ON produits(quantite);
CREATE INDEX idx_produit_categorie   ON produits(categorie_id);
CREATE INDEX idx_produit_fournisseur ON produits(fournisseur_id);

-- Table : mouvements_stock
CREATE INDEX idx_mouvement_stock_date    ON mouvements_stock(date_mouvement);
CREATE INDEX idx_mouvement_stock_produit ON mouvements_stock(produit_id);
CREATE INDEX idx_mouvement_stock_type    ON mouvements_stock(type_mouvement);
