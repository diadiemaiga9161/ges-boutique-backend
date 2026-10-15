package com.ges.boutique.offre;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;

/**
 * Calcul du prix d'une quantité avec des offres « N pour X F », valables seulement pour
 * exactement N articles : avec « 3 pour 70 », 3 bonbons = 70 F, mais 4 bonbons = 4 × prix normal.
 *
 * Les paliers sont aussi gardés sur la ligne de vente sous forme de texte « 2:75;5:150 »,
 * pour calculer plus tard un retour avec les offres du jour de la vente.
 *
 * Le même calcul existe dans les applis (offres-quantite.util.ts) : les deux doivent rester
 * identiques, sinon le ticket affiché et la caisse ne seraient pas d'accord.
 */
public final class CalculOffre {

    /** Au-delà, pas d'offre (quantité irréaliste, évite un calcul trop long). */
    static final int QUANTITE_MAX = 100_000;

    private CalculOffre() {}

    public record Palier(int quantite, double prix) {}

    public record Resultat(double prixTotal, double remise, String libelle) {}

    /**
     * Offre pour exactement {@code n} articles : « 3 pour 70 F » ne joue que si le client en
     * prend 3 ; pour 4 ou 6, prix normal, sauf offre créée pour 4 ou pour 6 (demande du gérant).
     */
    public static Resultat calculer(int n, double prixUnitaire, List<Palier> paliers) {
        double normal = n * prixUnitaire;
        if (n < 2 || n > QUANTITE_MAX || paliers == null) return new Resultat(arrondir(normal), 0, null);
        Palier exact = paliers.stream()
                .filter(p -> p.quantite() == n && p.prix() > 0)
                .min(Comparator.comparingDouble(Palier::prix))
                .orElse(null);
        if (exact == null) return new Resultat(arrondir(normal), 0, null);
        double remise = arrondir(normal - exact.prix());
        if (remise <= 0) return new Resultat(arrondir(normal), 0, null);
        return new Resultat(arrondir(exact.prix()), remise, "Offre " + exact.quantite() + " pour " + montant(exact.prix()) + " F");
    }

    public static String versTexte(List<Palier> paliers) {
        StringJoiner sj = new StringJoiner(";");
        paliers.forEach(p -> sj.add(p.quantite() + ":" + montant(p.prix())));
        return sj.toString();
    }

    public static List<Palier> depuisTexte(String texte) {
        List<Palier> res = new ArrayList<>();
        if (texte == null || texte.isBlank()) return res;
        for (String morceau : texte.split(";")) {
            String[] qp = morceau.split(":");
            if (qp.length != 2) continue;
            try {
                res.add(new Palier(Integer.parseInt(qp[0].trim()), Double.parseDouble(qp[1].trim())));
            } catch (NumberFormatException ignored) { }
        }
        return res;
    }

    static double arrondir(double v) {
        return BigDecimal.valueOf(v).setScale(2, RoundingMode.HALF_UP).doubleValue();
    }

    private static String montant(double v) {
        return v == Math.rint(v) ? String.valueOf((long) v) : BigDecimal.valueOf(v).stripTrailingZeros().toPlainString();
    }
}
