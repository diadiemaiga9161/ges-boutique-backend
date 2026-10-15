package com.ges.boutique.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Set;

/**
 * Mode entraînement des applis : elles marquent leurs demandes avec l'en-tête
 * X-Mode-Entrainement. Toute demande ainsi marquée qui modifierait des données est refusée
 * ici (423), en plus du blocage déjà fait dans l'appli : une vente d'entraînement ne peut
 * jamais être enregistrée, même si l'appli laissait passer la demande.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class ModeEntrainementFilter extends OncePerRequestFilter {

    private static final Set<String> LECTURE = Set.of("GET", "HEAD", "OPTIONS");

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request, @NonNull HttpServletResponse response,
                                    @NonNull FilterChain chain) throws ServletException, IOException {
        String chemin = request.getRequestURI();
        boolean marque = "1".equals(request.getHeader("X-Mode-Entrainement"));
        if (marque && chemin.startsWith("/api/") && !LECTURE.contains(request.getMethod().toUpperCase())
                && !chemin.startsWith("/api/auth/") && !chemin.startsWith("/api/push/desabonnement")) {
            response.setStatus(423);
            response.setContentType("application/json;charset=UTF-8");
            response.getOutputStream().write("{\"message\":\"Mode entraînement : rien n'est enregistré.\",\"errorCode\":\"MODE_ENTRAINEMENT\"}"
                    .getBytes(StandardCharsets.UTF_8));
            return;
        }
        chain.doFilter(request, response);
    }
}
