package com.ges.boutique.commission;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/** Prime versée à un vendeur (ce qui reste à payer = primes gagnées - primes versées). */
@Entity
@Table(name = "paiement_commission")
@Getter
@Setter
@NoArgsConstructor
public class PaiementCommission {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "vendeur_id", nullable = false)
    private Long vendeurId;

    @Column(nullable = false)
    private Double montant;

    @Column(name = "date_paiement", nullable = false)
    private LocalDateTime datePaiement;

    @Column(length = 255)
    private String note;

    @Column(name = "enregistre_par", length = 120)
    private String enregistrePar;
}
