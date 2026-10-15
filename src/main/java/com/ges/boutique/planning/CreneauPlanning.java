package com.ges.boutique.planning;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalTime;

/** Une journée du planning d'un employé : ses horaires, ou repos. Une ligne par employé et par jour. */
@Entity
@Table(name = "planning_creneaux",
        uniqueConstraints = @UniqueConstraint(name = "uk_planning_employe_jour", columnNames = {"employe_id", "date_jour"}),
        indexes = @Index(name = "idx_planning_date", columnList = "date_jour"))
@Data
@NoArgsConstructor
public class CreneauPlanning {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "employe_id", nullable = false)
    private Long employeId;

    @Column(name = "date_jour", nullable = false)
    private LocalDate dateJour;

    @Column(name = "heure_debut")
    private LocalTime heureDebut;

    @Column(name = "heure_fin")
    private LocalTime heureFin;

    @Column(nullable = false)
    private Boolean repos = false;

    @Column(length = 120)
    private String note;
}
