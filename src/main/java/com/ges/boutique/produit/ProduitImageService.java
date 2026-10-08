package com.ges.boutique.produit;

import com.ges.boutique.exception.RessourceIntrouvableException;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class ProduitImageService {

    /** Taille maximale du fichier reçu (les apps réduisent déjà la photo avant l'envoi). */
    public static final long TAILLE_MAX_OCTETS = 8L * 1024 * 1024;
    private static final int COTE_MINIATURE = 200;
    private static final int COTE_IMAGE = 800;

    private final ProduitImageRepository imageRepository;
    private final ProduitRepository produitRepository;

    @Transactional
    @CacheEvict(value = "produits", allEntries = true)
    public long enregistrer(Long produitId, MultipartFile fichier) throws IOException {
        if (!produitRepository.existsById(produitId)) {
            throw new RessourceIntrouvableException("Produit introuvable");
        }
        if (fichier == null || fichier.isEmpty()) {
            throw new IllegalArgumentException("Aucune photo reçue.");
        }
        if (fichier.getSize() > TAILLE_MAX_OCTETS) {
            throw new IllegalArgumentException("La photo est trop lourde (8 Mo maximum).");
        }
        BufferedImage source = ImageIO.read(new ByteArrayInputStream(fichier.getBytes()));
        if (source == null) {
            throw new IllegalArgumentException("Format de photo non reconnu. Utilisez une photo JPG ou PNG.");
        }

        ProduitImage img = imageRepository.findByProduitId(produitId).orElseGet(ProduitImage::new);
        img.setProduitId(produitId);
        img.setMiniature(enJpeg(redimensionner(source, COTE_MINIATURE), 0.78f));
        img.setImage(enJpeg(redimensionner(source, COTE_IMAGE), 0.82f));
        long version = System.currentTimeMillis();
        img.setVersion(version);
        imageRepository.save(img);
        produitRepository.definirImageVersion(produitId, version);
        return version;
    }

    @Transactional
    @CacheEvict(value = "produits", allEntries = true)
    public void supprimer(Long produitId) {
        imageRepository.deleteByProduitId(produitId);
        produitRepository.definirImageVersion(produitId, null);
    }

    @Transactional(readOnly = true)
    public Optional<byte[]> lire(Long produitId, boolean miniature) {
        return imageRepository.findByProduitId(produitId)
                .map(i -> miniature ? i.getMiniature() : i.getImage());
    }

    /** Réduit pour que le plus grand côté ne dépasse pas "cote" (jamais d'agrandissement). */
    private BufferedImage redimensionner(BufferedImage src, int cote) {
        int w = src.getWidth();
        int h = src.getHeight();
        double ratio = Math.min(1.0, (double) cote / Math.max(w, h));
        int nw = Math.max(1, (int) Math.round(w * ratio));
        int nh = Math.max(1, (int) Math.round(h * ratio));
        BufferedImage out = new BufferedImage(nw, nh, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = out.createGraphics();
        try {
            // Fond blanc : les PNG transparents deviendraient noirs en JPEG sinon.
            g.setColor(Color.WHITE);
            g.fillRect(0, 0, nw, nh);
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.drawImage(src, 0, 0, nw, nh, null);
        } finally {
            g.dispose();
        }
        return out;
    }

    private byte[] enJpeg(BufferedImage img, float qualite) throws IOException {
        ImageWriter writer = ImageIO.getImageWritersByFormatName("jpg").next();
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ImageOutputStream ios = ImageIO.createImageOutputStream(baos)) {
            writer.setOutput(ios);
            ImageWriteParam param = writer.getDefaultWriteParam();
            param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
            param.setCompressionQuality(qualite);
            writer.write(null, new IIOImage(img, null, null), param);
        } finally {
            writer.dispose();
        }
        return baos.toByteArray();
    }
}
