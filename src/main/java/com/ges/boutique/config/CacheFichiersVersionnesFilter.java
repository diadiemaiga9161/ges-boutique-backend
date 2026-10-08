package com.ges.boutique.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpServletResponseWrapper;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.regex.Pattern;

/**
 * Fichiers JS/CSS du site et du mobile dont le nom contient une empreinte qui change à chaque
 * build (site : "main-EYM7QSCM.js", mobile : "mobile/main.9577fdc0f2c41eeb.js") : le navigateur
 * peut les garder un an, une nouvelle version porte un autre nom. Sans ce filtre ils partaient
 * en "no-store" (rechargés en entier à chaque visite, lourd sur une connexion faible).
 * Les fichiers à nom fixe (index.html, ngsw-worker.js, ngsw.json...) ne sont jamais concernés.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class CacheFichiersVersionnesFilter extends OncePerRequestFilter {

    private static final Pattern FICHIER_VERSIONNE =
            Pattern.compile("^/(?:[^/]+-[A-Z0-9]{8}|mobile/[^/]+\\.[0-9a-f]{16})\\.(?:js|css)$");

    private static final String UN_AN = "public, max-age=31536000, immutable";

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !"GET".equals(request.getMethod())
                || !FICHIER_VERSIONNE.matcher(request.getRequestURI()).matches();
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        response.setHeader("Cache-Control", UN_AN);
        // Les en-têtes "ne pas garder" posés plus loin (ressources statiques) sont ignorés.
        chain.doFilter(request, new HttpServletResponseWrapper(response) {
            private boolean enTeteCache(String nom) {
                return "Cache-Control".equalsIgnoreCase(nom) || "Pragma".equalsIgnoreCase(nom) || "Expires".equalsIgnoreCase(nom);
            }

            @Override
            public void setHeader(String nom, String valeur) {
                if (!enTeteCache(nom)) super.setHeader(nom, valeur);
            }

            @Override
            public void addHeader(String nom, String valeur) {
                if (!enTeteCache(nom)) super.addHeader(nom, valeur);
            }

            @Override
            public void setDateHeader(String nom, long date) {
                if (!enTeteCache(nom)) super.setDateHeader(nom, date);
            }

            @Override
            public void addDateHeader(String nom, long date) {
                if (!enTeteCache(nom)) super.addDateHeader(nom, date);
            }
        });
    }
}
