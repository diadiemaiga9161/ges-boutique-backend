package com.ges.boutique.produit;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;

/**
 * Vente à la mesure (fonctionnalité VENTE_A_LA_MESURE) : un produit vendu au kilo, au
 * litre ou au mètre garde en base des nombres ENTIERS dans une petite unité (gramme,
 * millilitre, centimètre) et des prix PAR petite unité (600 F/kg -> 0,6 F/g). Ainsi tous
 * les calculs existants (prix x quantité, bénéfice, valeur du stock, CUMP, caisse) restent
 * justes sans être modifiés : seuls l'affichage et la saisie convertissent.
 */
public enum ModeMesure {
    KG(1000, "kg", "g"),
    L(1000, "L", "ml"),
    M(100, "m", "cm");

    /** Nombre de petites unités dans une unité affichée (1 kg = 1000 g). */
    private final int facteur;
    private final String unite;
    private final String petiteUnite;

    ModeMesure(int facteur, String unite, String petiteUnite) {
        this.facteur = facteur;
        this.unite = unite;
        this.petiteUnite = petiteUnite;
    }

    public int getFacteur() { return facteur; }
    public String getUnite() { return unite; }
    public String getPetiteUnite() { return petiteUnite; }

    /** "1250" g -> "1,25 kg". */
    public String formaterQuantite(Number quantiteStockee) {
        double v = quantiteStockee == null ? 0 : quantiteStockee.doubleValue() / facteur;
        DecimalFormat df = new DecimalFormat("#,##0.###", DecimalFormatSymbols.getInstance(Locale.FRANCE));
        return df.format(v) + " " + unite;
    }

    /** Prix par petite unité -> prix par unité affichée (0,6 F/g -> 600 F/kg). */
    public double prixAffiche(Double prixStocke) {
        return prixStocke == null ? 0 : prixStocke * facteur;
    }

    /** Pour les documents imprimés : quantité lisible, que le produit soit à la mesure ou non. */
    public static String quantiteLisible(Produit produit, Number quantite) {
        ModeMesure m = produit != null ? produit.getModeMesure() : null;
        if (m == null) return quantite == null ? "" : String.valueOf(quantite);
        return m.formaterQuantite(quantite);
    }

    /** Pour les documents imprimés : prix unitaire tel que le client le connaît (au kg...). */
    public static double prixLisible(Produit produit, Double prix) {
        ModeMesure m = produit != null ? produit.getModeMesure() : null;
        if (prix == null) return 0;
        return m == null ? prix : m.prixAffiche(prix);
    }

    /** Quantité en unité affichée (kg pour la mesure, sinon inchangée) : sert aux classements
     *  et totaux des rapports, pour ne pas additionner des grammes avec des pièces. */
    public static double qteAffichee(Produit produit, Number quantite) {
        double q = quantite == null ? 0 : quantite.doubleValue();
        ModeMesure m = produit != null ? produit.getModeMesure() : null;
        return m == null ? q : q / m.getFacteur();
    }

    /** Texte d'une quantité déjà en unité affichée : "12,5 kg" ou "3". */
    public static String texteAffiche(ModeMesure m, double quantiteAffichee) {
        DecimalFormat df = new DecimalFormat("#,##0.###", DecimalFormatSymbols.getInstance(Locale.FRANCE));
        return m == null ? df.format(quantiteAffichee) : df.format(quantiteAffichee) + " " + m.getUnite();
    }

    /** Suffixe du prix unitaire imprimé : "" ou "/kg". */
    public static String suffixePrix(Produit produit) {
        ModeMesure m = produit != null ? produit.getModeMesure() : null;
        return m == null ? "" : "/" + m.getUnite();
    }
}
