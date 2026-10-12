package com.ges.boutique.pointage;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

/**
 * Une journée de travail d'un employé : heure d'arrivée et heure de départ.
 * Une seule ligne par employé et par jour.
 */
@Entity
@Table(name = "pointages",
        uniqueConstraints = @UniqueConstraint(name = "uk_pointage_employe_jour", columnNames = {"employe_id", "date_pointage"}),
        indexes = @Index(name = "idx_pointage_date", columnList = "date_pointage"))
@Data
@NoArgsConstructor
public class Pointage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "employe_id", nullable = false)
    private Long employeId;

    @Column(name = "date_pointage", nullable = false)
    private LocalDate datePointage;

    @Column(name = "heure_arrivee", nullable = false)
    private LocalTime heureArrivee;

    @Column(name = "heure_depart")
    private LocalTime heureDepart;

    /** QR (scanné par l'employé) ou MANUEL (saisi par le gérant). */
    @Column(name = "methode_arrivee", length = 10, nullable = false)
    private String methodeArrivee;

    @Column(name = "methode_depart", length = 10)
    private String methodeDepart;

    /** Nom de la personne qui a fait la dernière saisie à la main. */
    @Column(name = "saisi_par", length = 120)
    private String saisiPar;

    @Column(length = 255)
    private String observation;

    @Column(name = "date_modification")
    private LocalDateTime dateModification;
}
