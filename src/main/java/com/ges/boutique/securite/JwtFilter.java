package com.ges.boutique.securite;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.ExpiredJwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class JwtFilter extends OncePerRequestFilter {

    private final JwtUtil jwtUtil;
    private final UserDetailsService userDetailsService;
    private final ObjectMapper objectMapper;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getServletPath();

        return path.startsWith("/ws")   // WebSocket STOMP endpoint
                || path.equals("/")
                || path.equals("/index.html")
                || path.equals("/favicon.ico")

                // Dossiers Angular
                || path.startsWith("/assets/")
                || path.startsWith("/media/")
                || path.startsWith("/fonts/")
                || path.startsWith("/icons/")
                || path.startsWith("/images/")

                // Fichiers Angular / images / icônes / fonts
                || path.endsWith(".js")
                || path.endsWith(".css")
                || path.endsWith(".ico")
                || path.endsWith(".png")
                || path.endsWith(".jpg")
                || path.endsWith(".jpeg")
                || path.endsWith(".gif")
                || path.endsWith(".svg")
                || path.endsWith(".webp")
                || path.endsWith(".avif")
                || path.endsWith(".woff")
                || path.endsWith(".woff2")
                || path.endsWith(".ttf")
                || path.endsWith(".eot")
                || path.endsWith(".otf")
                || path.endsWith(".map")
                || path.endsWith(".json");
    }

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain
    ) throws ServletException, IOException {

        String origin = request.getHeader("Origin");

        if (origin != null) {
            response.setHeader("Access-Control-Allow-Origin", origin);
        }

        response.setHeader("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS, PATCH");
        response.setHeader("Access-Control-Allow-Headers", "Authorization, Content-Type, Accept, Origin, X-Requested-With");
        response.setHeader("Access-Control-Expose-Headers", "Authorization");
        response.setHeader("Access-Control-Allow-Credentials", "true");

        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            response.setStatus(HttpServletResponse.SC_OK);
            return;
        }

        final String authHeader = request.getHeader("Authorization");

        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }

        final String jwt = authHeader.substring(7);

        try {
            final String username = jwtUtil.extractUsername(jwt);

            if (username != null && SecurityContextHolder.getContext().getAuthentication() == null) {

                UserDetails userDetails = this.userDetailsService.loadUserByUsername(username);

                if (jwtUtil.validateToken(jwt, userDetails)) {

                    UsernamePasswordAuthenticationToken authToken =
                            new UsernamePasswordAuthenticationToken(
                                    userDetails,
                                    null,
                                    userDetails.getAuthorities()
                            );

                    authToken.setDetails(
                            new WebAuthenticationDetailsSource().buildDetails(request)
                    );

                    SecurityContextHolder.getContext().setAuthentication(authToken);
                }
            }
        } catch (Exception e) {
            // Token expiré/malformé/signature invalide, ou compte supprimé depuis
            // l'émission du token : ce filtre s'exécute AVANT DispatcherServlet, donc
            // GlobalExceptionHandler (@ControllerAdvice) ne peut pas intercepter cette
            // exception — sans ce catch, elle remontait non gérée et le client recevait
            // une erreur 500 brute au lieu d'un message clair. On répond nous-mêmes ici,
            // avec le même message/format que GlobalExceptionHandler#handleJwtException,
            // pour que le front (déjà câblé sur le 401 pour se déconnecter proprement)
            // se comporte pareil qu'avec un token expiré détecté plus loin dans un contrôleur.
            log.warn("Authentification par token échouée : {}", e.getMessage());
            writeAuthErrorResponse(response, request, e instanceof ExpiredJwtException);
            return;
        }

        filterChain.doFilter(request, response);
    }

    private void writeAuthErrorResponse(
            HttpServletResponse response,
            HttpServletRequest request,
            boolean expire
    ) throws IOException {

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("timestamp", LocalDateTime.now().toString());
        body.put("status", HttpServletResponse.SC_UNAUTHORIZED);
        body.put("error", "Authentication Error");
        body.put("message", expire ? "Token JWT expiré" : "Token JWT invalide");
        body.put("path", request.getRequestURI());
        body.put("errorCode", expire ? "TOKEN_EXPIRED" : "INVALID_TOKEN");

        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write(objectMapper.writeValueAsString(body));
    }
}