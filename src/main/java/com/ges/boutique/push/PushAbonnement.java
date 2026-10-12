package com.ges.boutique.push;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/** Un téléphone (ou navigateur) qui a accepté de recevoir les notifications de cette boutique. */
@Entity
@Table(name = "push_abonnements")
@Data
@NoArgsConstructor
public class PushAbonnement {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Adresse fournie par Apple ou Google pour joindre ce téléphone. */
    @Column(nullable = false, unique = true, length = 700)
    private String endpoint;

    @Column(nullable = false, length = 200)
    private String p256dh;

    @Column(nullable = false, length = 100)
    private String auth;

    private Long utilisateurId;

    @Column(length = 255)
    private String appareil;

    private LocalDateTime dateCreation = LocalDateTime.now();

    private LocalDateTime dernierEnvoi;
}
