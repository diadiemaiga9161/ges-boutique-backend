package com.ges.boutique.push;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Clés du serveur pour signer les notifications (VAPID). Créées une seule fois, à la première
 * utilisation, et gardées dans la base de la boutique : aucun réglage à faire par boutique.
 */
@Entity
@Table(name = "push_cle_vapid")
@Data
@NoArgsConstructor
public class PushCleVapid {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Clé publique brute (65 octets) en base64url, donnée aux téléphones. */
    @Column(nullable = false, length = 120)
    private String clePublique;

    /** Clé privée brute (32 octets) en base64url, ne quitte jamais le serveur. */
    @Column(nullable = false, length = 80)
    private String clePrivee;

    private LocalDateTime dateCreation = LocalDateTime.now();
}
