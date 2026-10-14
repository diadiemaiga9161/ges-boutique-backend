package com.ges.boutique.offre;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;

/**
 * Calcul du prix d'une quantité avec des offres « N pour X F ». Le client paie toujours le
 * moins cher possible en combinant les lots et le prix normal à l'unité :
 * 3 bonbons avec « 2 pour 75 » = 75 + 50 = 125 F.
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

    /** Meilleur prix pour exactement {@code n} articles. Aucun palier utile → n × prix unitaire. */
    public static Resultat calculer(int n, double prixUnitaire, List<Palier> paliers) {
        double normal = n * prixUnitaire;
        List<Palier> utiles = paliers == null ? List.of() : paliers.stream()
                .filter(p -> p.quantite() >= 2 && p.quantite() <= n && p.prix() >= 0)
                .toList();
        if (n <= 0 || n > QUANTITE_MAX || utiles.isEmpty()) return new Resultat(arrondir(normal), 0, null);

        double[] cout = new double[n + 1];
        int[] choix = new int[n + 1]; // 0 = un article au prix normal, sinon index du palier + 1
        for (int i = 1; i <= n; i++) {
            cout[i] = cout[i - 1] + prixUnitaire;
            choix[i] = 0;
            for (int k = 0; k < utiles.size(); k++) {
                Palier p = utiles.get(k);
                if (p.quantite() <= i) {
                    double c = cout[i - p.quantite()] + p.prix();
                    if (c < cout[i] - 1e-9) {
                        cout[i] = c;
                        choix[i] = k + 1;
                    }
                }
            }
        }
        double total = arrondir(cout[n]);
        double remise = arrondir(normal - total);
        if (remise <= 0) return new Resultat(arrondir(normal), 0, null);

        // Lots utilisés, du plus grand au plus petit : « 5 pour 150 F + 2 × (2 pour 75 F) ».
        Map<Palier, Integer> compte = new TreeMap<>(Comparator.comparingInt(Palier::quantite).reversed()
                .thenComparingDouble(Palier::prix));
        for (int i = n; i > 0; ) {
            if (choix[i] == 0) { i--; continue; }
            Palier p = utiles.get(choix[i] - 1);
            compte.merge(p, 1, Integer::sum);
            i -= p.quantite();
        }
        StringJoiner sj = new StringJoiner(" + ", "Offre ", "");
        compte.forEach((p, nb) -> sj.add((nb > 1 ? nb + " × " : "") + p.quantite() + " pour " + montant(p.prix()) + " F"));
        return new Resultat(total, remise, sj.toString());
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
