package com.ges.boutique.commande;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CommandeRepository extends JpaRepository<Commande, Long> {
    List<Commande> findAllByOrderByDateCommandeDesc();
    List<Commande> findByStatutOrderByDateCommandeDesc(StatutCommande statut);
    List<Commande> findByClient_IdOrderByDateCommandeDesc(Long clientId);
    List<Commande> findByOrigineAndStatutOrderByDateCommandeDesc(OrigineCommande origine, StatutCommande statut);

    /** Suivi depuis la vitrine (numéro + téléphone vérifiés par VitrineServiceImpl). */
    Optional<Commande> findFirstByNumeroCommandeAndOrigine(String numeroCommande, OrigineCommande origine);

    /** Clients ayant passé au moins une commande depuis la vitrine (badge « en ligne »). */
    @Query("select distinct c.client.id from Commande c where c.origine = :origine and c.client is not null")
    List<Long> findClientIdsByOrigine(@Param("origine") OrigineCommande origine);
}
