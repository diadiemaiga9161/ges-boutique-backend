package com.ges.boutique.vente;

import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Endpoint public (sans auth) pour visualiser une facture vente en HTML.
 * Utilisé par les QR codes sur les PDF de factures.
 */
@RestController
@RequestMapping("/api/public")
@RequiredArgsConstructor
public class PublicFactureController {

    private final VenteRepository venteRepository;

    @GetMapping(value = "/ventes/{venteId}/facture", produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<String> voirFacturePublique(@PathVariable Long venteId) {
        Vente vente = venteRepository.findById(venteId).orElse(null);
        if (vente == null || Boolean.TRUE.equals(vente.getAnnulee())) {
            return ResponseEntity.notFound().build();
        }

        String dateStr = vente.getDateVente() != null
                ? vente.getDateVente().format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm"))
                : "—";

        String clientNom = vente.getClientNom() != null ? vente.getClientNom() : "Client divers";
        String clientTel = vente.getClientTelephone() != null ? vente.getClientTelephone() : "";

        List<LigneVente> lignes = vente.getLignes();
        StringBuilder lignesHtml = new StringBuilder();
        for (LigneVente l : lignes) {
            lignesHtml.append("<tr>")
                .append("<td>").append(esc(l.getProduitNom())).append("</td>")
                // Vente à la mesure : "1,25 kg" et "600 F/kg" plutôt que 1250 et 0,6 F.
                .append("<td style='text-align:center'>").append(esc(com.ges.boutique.produit.ModeMesure.quantiteLisible(l.getProduit(), l.getQuantite()))).append("</td>")
                .append("<td style='text-align:right'>").append(fmt(com.ges.boutique.produit.ModeMesure.prixLisible(l.getProduit(), l.getPrixUnitaire())))
                .append(com.ges.boutique.produit.ModeMesure.suffixePrix(l.getProduit())).append("</td>")
                .append("<td style='text-align:right;font-weight:700'>").append(fmt(l.getSousTotal())).append("</td>")
                .append("</tr>");
        }

        boolean estCredit = Boolean.TRUE.equals(vente.getEstCredit());
        String statutStr = estCredit
                ? (Boolean.TRUE.equals(vente.getCreditRegle()) ? "CRÉDIT — Soldé" : "CRÉDIT — En cours")
                : "COMPTANT";

        String html = "<!DOCTYPE html><html lang='fr'><head>"
                + "<meta charset='utf-8'><meta name='viewport' content='width=device-width,initial-scale=1'>"
                + "<title>Facture " + esc(vente.getNumeroVente()) + "</title>"
                + "<style>"
                + "*{box-sizing:border-box;margin:0;padding:0}"
                + "body{font-family:'Segoe UI',Arial,sans-serif;background:#f0f4f8;padding:16px;font-size:14px;color:#1e293b}"
                + ".sheet{background:#fff;max-width:480px;margin:0 auto;border-radius:14px;overflow:hidden;box-shadow:0 4px 24px rgba(0,0,0,.12)}"
                + ".hdr{background:linear-gradient(135deg,#081648,#1a56db);color:#fff;padding:20px 24px;text-align:center}"
                + ".hdr h1{font-size:1.2rem;font-weight:900;margin-bottom:4px}"
                + ".hdr p{font-size:0.78rem;opacity:.8}"
                + ".body{padding:20px 24px}"
                + ".info-row{display:flex;justify-content:space-between;margin-bottom:6px;font-size:0.85rem}"
                + ".info-label{color:#64748b}"
                + ".info-val{font-weight:600}"
                + ".divider{border:none;border-top:1px solid #e2e8f0;margin:14px 0}"
                + "table{width:100%;border-collapse:collapse;font-size:0.82rem;margin-top:10px}"
                + "th{background:#eff6ff;padding:8px;text-align:left;font-weight:700;color:#1a56db}"
                + "td{padding:7px 8px;border-bottom:1px solid #f1f5f9}"
                + ".total-row{background:#eff6ff;font-weight:900;font-size:1rem}"
                + ".badge{display:inline-block;padding:3px 10px;border-radius:12px;font-size:0.75rem;font-weight:700}"
                + ".badge-green{background:#dcfce7;color:#166534}"
                + ".badge-blue{background:#dbeafe;color:#1e40af}"
                + ".footer{text-align:center;padding:14px;font-size:0.75rem;color:#94a3b8;border-top:1px solid #f1f5f9}"
                + "</style></head><body>"
                + "<div class='sheet'>"
                + "<div class='hdr'>"
                + "<h1>Facture N° " + esc(vente.getNumeroVente()) + "</h1>"
                + "<p>" + dateStr + "</p>"
                + "</div>"
                + "<div class='body'>"
                + "<div class='info-row'><span class='info-label'>Client</span><span class='info-val'>" + esc(clientNom) + "</span></div>"
                + (clientTel.isEmpty() ? "" : "<div class='info-row'><span class='info-label'>Tél.</span><span class='info-val'>" + esc(clientTel) + "</span></div>")
                + "<div class='info-row'><span class='info-label'>Statut</span><span class='badge " + (estCredit ? "badge-blue" : "badge-green") + "'>" + statutStr + "</span></div>"
                + "<hr class='divider'>"
                + "<table><thead><tr><th>Produit</th><th>Qté</th><th>Prix</th><th>Total</th></tr></thead>"
                + "<tbody>" + lignesHtml + "</tbody>"
                + "<tfoot><tr class='total-row'><td colspan='3'>TOTAL</td><td style='text-align:right'>" + fmt(vente.getMontantTotal()) + "</td></tr>"
                + (estCredit && !Boolean.TRUE.equals(vente.getCreditRegle())
                    ? "<tr><td colspan='3'>Versé</td><td style='text-align:right;color:#16a34a;font-weight:700'>" + fmt(vente.getMontantVerse()) + "</td></tr>"
                    + "<tr><td colspan='3'>Reste dû</td><td style='text-align:right;color:#dc2626;font-weight:700'>" + fmt(vente.getMontantRestant()) + "</td></tr>"
                    : "")
                + "</tfoot></table>"
                + "</div>"
                + "<div class='footer'>Facture générée par Ges Boutique</div>"
                + "</div></body></html>";

        return ResponseEntity.ok(html);
    }

    private String esc(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    private String fmt(Double v) {
        if (v == null) return "0 F";
        long lv = Math.round(v);
        String s = Long.toString(lv);
        StringBuilder sb = new StringBuilder();
        int start = s.length() % 3;
        if (start > 0) sb.append(s, 0, start);
        for (int i = start; i < s.length(); i += 3) {
            if (sb.length() > 0) sb.append(' ');
            sb.append(s, i, i + 3);
        }
        return sb + " F";
    }
}
