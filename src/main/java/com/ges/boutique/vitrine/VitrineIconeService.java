package com.ges.boutique.vitrine;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Base64;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Icônes d'installation de la vitrine (192 et 512 px), tirées du logo de la boutique.
 *
 * Le logo est stocké en data: URI ; mis tel quel dans le manifeste, il est refusé par
 * les navigateurs (« aucune icône utilisable ») → la vitrine n'était pas installable et
 * le navigateur retombait sur l'appli du personnel (start_url "/", page de connexion).
 * Ici le logo devient une vraie image PNG carrée, à la taille annoncée. Sans logo, ou
 * logo illisible (SVG…), on sert l'icône Ges Lafia par défaut.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class VitrineIconeService {

    public static final int[] TAILLES = {192, 512};
    private static final Pattern DATA_URI = Pattern.compile("^data:[^;,]*;base64,(.*)$", Pattern.DOTALL);

    private final VitrineService vitrineService;

    /** Taille autorisée la plus proche (évite de générer n'importe quelle dimension). */
    public static int tailleValide(int demandee) {
        return demandee <= 192 ? 192 : 512;
    }

    /** Change quand le logo change : ajouté à l'URL de l'icône pour éviter un vieux cache. */
    public String version() {
        String logo = vitrineService.obtenirInfosVitrine().getLogoPath();
        return logo == null || logo.isBlank() ? "0" : Integer.toHexString(logo.hashCode());
    }

    public byte[] icone(int taille) throws IOException {
        BufferedImage logo = lireLogo(vitrineService.obtenirInfosVitrine().getLogoPath());
        if (logo == null) return iconeParDefaut(taille);

        BufferedImage carre = new BufferedImage(taille, taille, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = carre.createGraphics();
        try {
            g.setColor(Color.WHITE);
            g.fillRect(0, 0, taille, taille);
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            // Marge de 10 % : les icônes « maskable » sont rognées en cercle sur Android.
            int zone = Math.round(taille * 0.8f);
            double ratio = Math.min((double) zone / logo.getWidth(), (double) zone / logo.getHeight());
            int w = Math.max(1, (int) Math.round(logo.getWidth() * ratio));
            int h = Math.max(1, (int) Math.round(logo.getHeight() * ratio));
            g.drawImage(logo, (taille - w) / 2, (taille - h) / 2, w, h, null);
        } finally {
            g.dispose();
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(carre, "png", out);
        return out.toByteArray();
    }

    private BufferedImage lireLogo(String logo) {
        if (logo == null || logo.isBlank()) return null;
        Matcher m = DATA_URI.matcher(logo.trim());
        if (!m.matches()) return null;
        try {
            return ImageIO.read(new ByteArrayInputStream(Base64.getDecoder().decode(m.group(1).trim())));
        } catch (IllegalArgumentException | IOException e) {
            log.warn("Logo de la boutique illisible pour l'icône vitrine : {}", e.getMessage());
            return null;
        }
    }

    private byte[] iconeParDefaut(int taille) throws IOException {
        try (InputStream in = new ClassPathResource("static/assets/icons/icon-" + taille + "x" + taille + ".png").getInputStream()) {
            return in.readAllBytes();
        }
    }
}
