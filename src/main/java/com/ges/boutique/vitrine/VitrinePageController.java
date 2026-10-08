package com.ges.boutique.vitrine;

import lombok.RequiredArgsConstructor;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.util.HtmlUtils;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/**
 * Page d'entrée de la vitrine (/vitrine), servie par le serveur avec SON manifeste.
 *
 * index.html déclare le manifeste de l'appli du personnel (start_url "/", page de connexion) ;
 * un script le remplaçait ensuite par celui de la vitrine. Chrome Android suit ce changement,
 * mais l'iPhone (Safari, et Chrome qui utilise le même moteur) garde le premier manifeste :
 * la vitrine installée s'ouvrait sur la connexion. Ici, /vitrine arrive directement avec le
 * manifeste de la vitrine, plus l'icône et le nom que l'iPhone lit à part
 * (apple-touch-icon, apple-mobile-web-app-title). Prioritaire sur HomeController (chemin exact).
 */
@Controller
@RequiredArgsConstructor
public class VitrinePageController {

    private static final String LIEN_MANIFESTE_PERSONNEL = "<link rel=\"manifest\" href=\"manifest.webmanifest\">";

    private final VitrineService vitrineService;
    private final VitrineIconeService iconeService;

    @GetMapping(value = "/vitrine", produces = MediaType.TEXT_HTML_VALUE)
    @ResponseBody
    public ResponseEntity<String> page() throws IOException {
        String html;
        try (InputStream in = new ClassPathResource("static/index.html").getInputStream()) {
            html = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
        String nomBrut = vitrineService.obtenirInfosVitrine().getNom();
        String nom = HtmlUtils.htmlEscape(nomBrut == null || nomBrut.isBlank() ? "Boutique" : nomBrut.trim());
        String icone = "/api/vitrine/icone/192?v=" + iconeService.version();

        html = html.replace(LIEN_MANIFESTE_PERSONNEL,
                "<link rel=\"manifest\" href=\"/api/vitrine/manifest.webmanifest\">");
        html = html.replaceFirst("<title>[^<]*</title>", java.util.regex.Matcher.quoteReplacement("<title>" + nom + "</title>"));
        html = html.replace("</head>",
                "<link rel=\"apple-touch-icon\" href=\"" + icone + "\">\n"
                        + "<meta name=\"apple-mobile-web-app-capable\" content=\"yes\">\n"
                        + "<meta name=\"mobile-web-app-capable\" content=\"yes\">\n"
                        + "<meta name=\"apple-mobile-web-app-title\" content=\"" + nom + "\">\n"
                        + "</head>");

        // Toujours relue (nom, logo ou version du site peuvent changer).
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noCache())
                .contentType(MediaType.TEXT_HTML)
                .body(html);
    }
}
