package com.ges.boutique.exception;

import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;

/**
 * Message sûr à renvoyer au client depuis un catch local de contrôleur (hors
 * GlobalExceptionHandler, qui ne voit pas ces try/catch). Passe le message tel
 * quel s'il vient d'une exception métier volontaire (déjà rédigée en français
 * pour l'utilisateur), retombe sur un message générique pour tout ce qui
 * ressemble à un bug technique (NPE, cast, accès base de données...) — et
 * journalise systématiquement l'exception réelle côté serveur, pour ne pas
 * perdre la trace en cas de vrai bug.
 */
@Slf4j
public final class MessageErreurUtil {

    private static final String MESSAGE_GENERIQUE = "Une erreur est survenue, veuillez réessayer.";

    private MessageErreurUtil() {
    }

    public static String messageClient(Exception e) {
        log.error("Erreur interceptée dans un contrôleur : {}", e.getMessage(), e);

        if (e instanceof NullPointerException
                || e instanceof ClassCastException
                || e instanceof ArrayIndexOutOfBoundsException
                || e instanceof NumberFormatException
                || e instanceof java.io.IOException
                || e instanceof DataAccessException) {
            return MESSAGE_GENERIQUE;
        }

        String msg = e.getMessage();
        return (msg == null || msg.isBlank()) ? MESSAGE_GENERIQUE : msg;
    }
}
