package com.ges.boutique.exception;

/**
 * Levée par role.PermissionInterceptor quand le rôle personnalisé de la personne connectée
 * ne lui donne pas le droit demandé (voir com.ges.boutique.role).
 */
public class PermissionRefuseeException extends RuntimeException {
    public PermissionRefuseeException(String message) {
        super(message);
    }
}
