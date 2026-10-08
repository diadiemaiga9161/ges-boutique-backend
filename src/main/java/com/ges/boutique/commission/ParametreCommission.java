package com.ges.boutique.commission;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Réglage unique de la boutique : quels types de primes le gérant utilise. Absence de
 * ligne = les trois types sont proposés.
 */
@Entity
@Table(name = "parametre_commission")
@Getter
@Setter
@NoArgsConstructor
public class ParametreCommission {

    @Id
    private Long id = 1L;

    /** Types proposés, séparés par des virgules, ex: "MONTANT_FIXE,OBJECTIF". */
    @Column(name = "types_actifs", length = 100)
    private String typesActifs;
}
