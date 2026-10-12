package com.ges.boutique.pointage;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalTime;

/**
 * Réglage unique de la boutique pour le pointage (ligne id = 1), et clé secrète
 * qui sert à fabriquer le code QR changeant. Créé à la première utilisation.
 */
@Entity
@Table(name = "parametre_pointage")
@Getter
@Setter
@NoArgsConstructor
public class ParametrePointage {

    @Id
    private Long id = 1L;

    @Column(name = "heure_arrivee_prevue", nullable = false)
    private LocalTime heureArriveePrevue = LocalTime.of(8, 0);

    /** Minutes de retard tolérées avant de compter un retard. */
    @Column(name = "tolerance_minutes", nullable = false)
    private Integer toleranceMinutes = 10;

    /** Jour de repos (MONDAY…SUNDAY) ou AUCUN : ce jour-là, pas d'absence comptée. */
    @Column(name = "jour_repos", length = 10, nullable = false)
    private String jourRepos = "SUNDAY";

    /** Clé secrète (base64url) du code QR, ne quitte jamais le serveur. */
    @Column(name = "cle_secrete", length = 64, nullable = false)
    private String cleSecrete;
}
