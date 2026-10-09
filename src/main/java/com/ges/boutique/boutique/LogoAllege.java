package com.ges.boutique.boutique;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.Base64;
import java.util.Iterator;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Logo envoyé aux écrans en version allégée (400 px de côté au plus).
 *
 * Le logo est stocké tel qu'il a été envoyé (souvent une photo de plusieurs centaines de Ko) et
 * partait à chaque ouverture du site : la plus grosse donnée de toute la session sur une connexion
 * lente. La version réduite garde le même aspect à l'écran ; l'original reste en base pour les
 * factures PDF et les icônes de la vitrine. Calculée une fois, puis gardée tant que le logo ne change pas.
 * Logo illisible (SVG…) ou déjà petit : renvoyé tel quel.
 */
@Slf4j
@Component
public class LogoAllege {

    static final int COTE_MAX = 400;
    private static final Pattern DATA_URI = Pattern.compile("^data:(image/[a-zA-Z0-9.+-]+);base64,(.*)$", Pattern.DOTALL);

    private String dernierOriginal;
    private String dernierAllege;

    public synchronized String alleger(String logo) {
        if (logo == null || logo.isBlank()) return logo;
        if (logo.equals(dernierOriginal)) return dernierAllege;
        String allege = calculer(logo);
        dernierOriginal = logo;
        dernierAllege = allege;
        return allege;
    }

    private String calculer(String logo) {
        Matcher m = DATA_URI.matcher(logo.trim());
        if (!m.matches()) return logo;
        try {
            String type = m.group(1).toLowerCase();
            byte[] octets = Base64.getMimeDecoder().decode(m.group(2));
            BufferedImage image = ImageIO.read(new ByteArrayInputStream(octets));
            if (image == null || Math.max(image.getWidth(), image.getHeight()) <= COTE_MAX) return logo;

            boolean jpeg = type.equals("image/jpeg") || type.equals("image/jpg");
            double ratio = (double) COTE_MAX / Math.max(image.getWidth(), image.getHeight());
            int w = Math.max(1, (int) Math.round(image.getWidth() * ratio));
            int h = Math.max(1, (int) Math.round(image.getHeight() * ratio));
            BufferedImage petite = new BufferedImage(w, h, jpeg ? BufferedImage.TYPE_INT_RGB : BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = petite.createGraphics();
            try {
                if (jpeg) {
                    g.setColor(Color.WHITE);
                    g.fillRect(0, 0, w, h);
                }
                g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
                g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g.drawImage(image, 0, 0, w, h, null);
            } finally {
                g.dispose();
            }

            byte[] resultat = jpeg ? jpeg(petite) : png(petite);
            if (resultat == null || resultat.length >= octets.length) return logo;
            return "data:" + (jpeg ? "image/jpeg" : "image/png") + ";base64," + Base64.getEncoder().encodeToString(resultat);
        } catch (Exception e) {
            log.warn("Logo non allégé, envoyé tel quel : {}", e.getMessage());
            return logo;
        }
    }

    private static byte[] png(BufferedImage image) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        return ImageIO.write(image, "png", out) ? out.toByteArray() : null;
    }

    private static byte[] jpeg(BufferedImage image) throws Exception {
        Iterator<ImageWriter> writers = ImageIO.getImageWritersByFormatName("jpeg");
        if (!writers.hasNext()) return null;
        ImageWriter writer = writers.next();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (ImageOutputStream ios = ImageIO.createImageOutputStream(out)) {
            writer.setOutput(ios);
            ImageWriteParam param = writer.getDefaultWriteParam();
            param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
            param.setCompressionQuality(0.85f);
            writer.write(null, new IIOImage(image, null, null), param);
        } finally {
            writer.dispose();
        }
        return out.toByteArray();
    }
}
