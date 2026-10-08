package com.ges.boutique.client;

import com.ges.boutique.boutique.Boutique;
import com.ges.boutique.boutique.BoutiqueRepository;
import com.lowagie.text.*;
import com.lowagie.text.Font;
import com.lowagie.text.pdf.*;
import com.lowagie.text.pdf.draw.LineSeparator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.awt.*;
import java.io.ByteArrayOutputStream;
import java.text.NumberFormat;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * PDF "Situation client" (GET /api/clients/{id}/releve-pdf), ouvert par Ionic et React Native.
 *
 * Les montants et les lignes viennent de {@link ClientReleveApiService} — la même source que
 * l'écran Situation client des 3 fronts — pour que le PDF affiche exactement la même chose :
 * chaque achat avec ses produits, chaque versement, et un paiement groupé sur une seule ligne
 * avec les ventes qu'il a réglées.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ClientReleveService {

    private final ClientRepository clientRepository;
    private final BoutiqueRepository boutiqueRepository;
    private final ClientReleveApiService clientReleveApiService;

    private static final Color BLEU_PRIMAIRE = new Color(30, 80, 162);
    private static final Color BLEU_CLAIR = new Color(219, 234, 254);
    private static final Color ORANGE_CREDIT = new Color(234, 88, 12);
    private static final Color VERT_REGLE = new Color(22, 163, 74);
    private static final Color ROUGE_RETARD = new Color(220, 38, 38);
    private static final Color GRIS_CLAIR = new Color(248, 250, 252);
    private static final Color GRIS_TEXTE = new Color(71, 85, 105);
    private static final Color GRIS_BORDURE = new Color(226, 232, 240);
    private static final Color TEXTE = new Color(30, 30, 30);
    private static final Color FOND_VENTE = new Color(255, 251, 235);
    private static final Color FOND_VERSEMENT = new Color(240, 253, 244);
    private static final Color FOND_RETOUR = new Color(239, 246, 255);
    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter FMT_LONG = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    public byte[] genererReleve(Long clientId) {
        return genererReleve(clientId, null, null, null);
    }

    @SuppressWarnings("unchecked")
    public byte[] genererReleve(Long clientId, LocalDate dateDebut, LocalDate dateFin, String type) {
        Client client = clientRepository.findById(clientId)
                .orElseThrow(() -> new RuntimeException("Client introuvable"));

        // Toutes les lignes de la période en une seule page (le PDF n'est pas paginé).
        Map<String, Object> situation = clientReleveApiService.genererReleve(
                clientId, 0, Integer.MAX_VALUE, dateDebut, dateFin, type);
        List<ClientReleveLigneDto> lignes = (List<ClientReleveLigneDto>) situation.get("lignes");

        Boutique boutique = boutiqueRepository.findFirstByActifTrue()
                .orElse(new Boutique());

        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            // Paysage : 10 colonnes (même tableau que la Situation client à l'écran).
            Document doc = new Document(PageSize.A4.rotate(), 30, 30, 36, 36);
            PdfWriter writer = PdfWriter.getInstance(doc, out);
            doc.open();

            // Le PDF ne montre QUE ce que l'écran affiche pour la période et le filtre demandés :
            // pas de section "crédits en cours", et des totaux limités au type filtré.
            ajouterEnTete(doc, writer, boutique, client, libellePeriode(dateDebut, dateFin, type));
            ajouterResume(doc, situation, type);
            ajouterSituation(doc, lignes);
            ajouterPied(doc, boutique);

            doc.close();
            return out.toByteArray();
        } catch (Exception e) {
            log.error("Erreur génération situation PDF client {}: {}", clientId, e.getMessage());
            throw new RuntimeException("Erreur génération PDF", e);
        }
    }

    private void ajouterEnTete(Document doc, PdfWriter writer, Boutique boutique, Client client, String periode) throws DocumentException {
        Font fontBoutiqueNom = new Font(Font.HELVETICA, 18, Font.BOLD, Color.WHITE);
        Font fontBoutiqueAdresse = new Font(Font.HELVETICA, 9, Font.NORMAL, new Color(200, 220, 255));
        Font fontTitreReleve = new Font(Font.HELVETICA, 12, Font.BOLD, new Color(180, 210, 255));

        PdfPTable header = new PdfPTable(2);
        header.setWidthPercentage(100);
        header.setWidths(new float[]{1.5f, 1f});

        PdfPCell cellGauche = new PdfPCell();
        cellGauche.setBorder(0);
        cellGauche.setBackgroundColor(BLEU_PRIMAIRE);
        cellGauche.setPadding(10);
        cellGauche.addElement(new Paragraph(texte(boutique.getNom()), fontBoutiqueNom));
        cellGauche.addElement(new Paragraph(texte(boutique.getAdresse()) + " — " + texte(boutique.getTelephone()), fontBoutiqueAdresse));

        PdfPCell cellDroite = new PdfPCell();
        cellDroite.setBorder(0);
        cellDroite.setBackgroundColor(BLEU_PRIMAIRE);
        cellDroite.setPadding(10);
        Paragraph titre = new Paragraph("SITUATION CLIENT", fontTitreReleve);
        titre.setAlignment(Element.ALIGN_RIGHT);
        cellDroite.addElement(titre);
        Font fontDate = new Font(Font.HELVETICA, 9, Font.NORMAL, Color.WHITE);
        Paragraph edite = new Paragraph("Édité le " + LocalDate.now().format(FMT), fontDate);
        edite.setAlignment(Element.ALIGN_RIGHT);
        cellDroite.addElement(edite);
        Paragraph pPeriode = new Paragraph(periode, fontDate);
        pPeriode.setAlignment(Element.ALIGN_RIGHT);
        cellDroite.addElement(pPeriode);

        header.addCell(cellGauche);
        header.addCell(cellDroite);
        doc.add(header);

        // Infos client
        PdfPTable infoClient = new PdfPTable(2);
        infoClient.setWidthPercentage(100);
        infoClient.setWidths(new float[]{1f, 1f});
        infoClient.setSpacingBefore(10);

        Font fontLabel = new Font(Font.HELVETICA, 8, Font.BOLD, GRIS_TEXTE);
        Font fontValeur = new Font(Font.HELVETICA, 10, Font.BOLD, new Color(15, 23, 42));
        Font fontValeurNormal = new Font(Font.HELVETICA, 9, Font.NORMAL, GRIS_TEXTE);

        PdfPCell cellClient = new PdfPCell();
        cellClient.setBorderColor(BLEU_CLAIR);
        cellClient.setBorderWidth(1.5f);
        cellClient.setBackgroundColor(GRIS_CLAIR);
        cellClient.setPadding(10);
        String nomAffiche = (texte(client.getNom()) + " " + texte(client.getPrenom())).trim();
        cellClient.addElement(new Paragraph("CLIENT", fontLabel));
        cellClient.addElement(new Paragraph(nomAffiche, fontValeur));
        String contact = java.util.stream.Stream.of(client.getNumeroTelephone(), client.getEmail(), client.getAdresse())
                .filter(s -> s != null && !s.isBlank())
                .collect(Collectors.joining("  ·  "));
        if (!contact.isEmpty()) cellClient.addElement(new Paragraph(contact, fontValeurNormal));

        PdfPCell cellDateClient = new PdfPCell();
        cellDateClient.setBorderColor(BLEU_CLAIR);
        cellDateClient.setBorderWidth(1.5f);
        cellDateClient.setBackgroundColor(GRIS_CLAIR);
        cellDateClient.setPadding(10);
        Paragraph labelMembre = new Paragraph("MEMBRE DEPUIS", fontLabel);
        labelMembre.setAlignment(Element.ALIGN_RIGHT);
        cellDateClient.addElement(labelMembre);
        if (client.getDateCreation() != null) {
            Paragraph dateMembre = new Paragraph(client.getDateCreation().toLocalDate().format(FMT), fontValeur);
            dateMembre.setAlignment(Element.ALIGN_RIGHT);
            cellDateClient.addElement(dateMembre);
        }

        infoClient.addCell(cellClient);
        infoClient.addCell(cellDateClient);
        doc.add(infoClient);
    }

    /**
     * Totaux d'en-tête, limités au type filtré : "achats uniquement" n'affiche pas le total des
     * versements, et inversement. "Reste à payer" (ce que le client doit aujourd'hui) reste
     * toujours affiché — c'est l'information principale d'une situation client.
     */
    private void ajouterResume(Document doc, Map<String, Object> situation, String type) throws DocumentException {
        boolean achatsSeuls = "VENTE".equalsIgnoreCase(type);
        boolean versementsSeuls = "VERSEMENT".equalsIgnoreCase(type);
        double soldeActuel = nombre(situation.get("soldeActuel"));

        PdfPTable resume = new PdfPTable(achatsSeuls || versementsSeuls ? 2 : 3);
        resume.setWidthPercentage(100);
        resume.setSpacingBefore(10);
        resume.setSpacingAfter(12);

        if (!versementsSeuls) {
            addResumeCard(resume, "Total des achats", formatMontant(nombre(situation.get("totalVentes"))), BLEU_PRIMAIRE);
        }
        if (!achatsSeuls) {
            addResumeCard(resume, "Total des versements", formatMontant(nombre(situation.get("totalVersements"))), VERT_REGLE);
        }
        addResumeCard(resume, "Reste à payer", formatMontant(soldeActuel),
                soldeActuel > 0 ? ROUGE_RETARD : GRIS_TEXTE);
        doc.add(resume);
    }

    private void addResumeCard(PdfPTable table, String label, String valeur, Color bg) {
        PdfPCell cell = new PdfPCell();
        cell.setBackgroundColor(bg);
        cell.setPadding(10);
        cell.setBorderColor(Color.WHITE);
        cell.setBorderWidth(3);
        Paragraph pLabel = new Paragraph(label.toUpperCase(), new Font(Font.HELVETICA, 8, Font.BOLD, new Color(235, 242, 255)));
        pLabel.setAlignment(Element.ALIGN_CENTER);
        cell.addElement(pLabel);
        Paragraph p = new Paragraph(valeur, new Font(Font.HELVETICA, 14, Font.BOLD, Color.WHITE));
        p.setAlignment(Element.ALIGN_CENTER);
        cell.addElement(p);
        table.addCell(cell);
    }

    private void ajouterSituation(Document doc, List<ClientReleveLigneDto> lignes) throws DocumentException {
        Font fontTitre = new Font(Font.HELVETICA, 11, Font.BOLD, BLEU_PRIMAIRE);
        Paragraph titre = new Paragraph("ACHATS ET VERSEMENTS", fontTitre);
        titre.setSpacingAfter(6);
        doc.add(titre);

        if (lignes == null || lignes.isEmpty()) {
            doc.add(new Paragraph("Aucune opération sur cette période.", new Font(Font.HELVETICA, 9, Font.ITALIC, GRIS_TEXTE)));
            doc.add(new Paragraph(" "));
            return;
        }

        PdfPTable table = new PdfPTable(10);
        table.setWidthPercentage(100);
        table.setWidths(new float[]{1.15f, 0.8f, 1.55f, 3.6f, 0.45f, 0.95f, 1f, 1f, 1f, 1f});
        table.setHeaderRows(1);

        String[] headers = {"Date", "Type", "Référence", "Désignation", "Qté", "P.U.", "Vente", "Versement", "Reste à payer", "Par"};
        for (String h : headers) {
            PdfPCell cell = new PdfPCell(new Phrase(h, new Font(Font.HELVETICA, 7.5f, Font.BOLD, Color.WHITE)));
            cell.setBackgroundColor(BLEU_PRIMAIRE);
            cell.setBorderColor(BLEU_PRIMAIRE);
            cell.setPadding(5);
            cell.setHorizontalAlignment(Element.ALIGN_CENTER);
            table.addCell(cell);
        }

        Font fontCell = new Font(Font.HELVETICA, 7.5f, Font.NORMAL, TEXTE);
        Font fontCellBold = new Font(Font.HELVETICA, 7.5f, Font.BOLD, TEXTE);
        Font fontDetail = new Font(Font.HELVETICA, 7f, Font.NORMAL, GRIS_TEXTE);

        for (ClientReleveLigneDto l : lignes) {
            Color bg;
            Color couleurType;
            String typeLabel;
            switch (l.getType() == null ? "" : l.getType()) {
                case "VERSEMENT" -> { bg = FOND_VERSEMENT; couleurType = VERT_REGLE; typeLabel = "Versement"; }
                case "RETOUR" -> { bg = FOND_RETOUR; couleurType = BLEU_PRIMAIRE; typeLabel = "Retour"; }
                default -> { bg = FOND_VENTE; couleurType = ORANGE_CREDIT; typeLabel = "Vente"; }
            }

            addCell(table, l.getDate() != null ? l.getDate().format(FMT_LONG) : "-", fontCell, bg, Element.ALIGN_LEFT);
            addCell(table, typeLabel, new Font(Font.HELVETICA, 7.5f, Font.BOLD, couleurType), bg, Element.ALIGN_LEFT);
            String reference = l.getReferenceVente() != null ? l.getReferenceVente() : l.getReferenceReglement();
            addCell(table, reference != null ? reference : "-", fontCellBold, bg, Element.ALIGN_LEFT);

            // Désignation : produit pour un achat ; produits payés pour un versement simple. Un paiement
            // groupé reste une ligne courte (la Référence dit déjà "Paiement groupé — N ventes réglées") :
            // la liste de ses ventes rendait la ligne illisible dès quelques ventes.
            PdfPCell designation = celluleVide(bg);
            List<ClientReleveLigneDto.VenteReglee> reglees = l.getVentesReglees();
            if ("VERSEMENT".equals(l.getType()) && reglees != null && reglees.size() == 1) {
                designation.addElement(paragrapheSerre(new Chunk(texteProduits(reglees.get(0)), fontDetail)));
            } else if ("VENTE".equals(l.getType())) {
                designation.addElement(new Paragraph(l.getProduitNom() != null ? l.getProduitNom() : "-", fontCell));
            } else {
                designation.addElement(new Paragraph("-", fontCell));
            }
            table.addCell(designation);

            addCell(table, l.getQuantiteTexte() != null ? l.getQuantiteTexte()
                    : (l.getQuantite() != null ? String.valueOf(l.getQuantite()) : ""), fontCell, bg, Element.ALIGN_RIGHT);
            addCell(table, l.getPrixUnitaire() != null ? formatMontant(l.getPrixUnitaire()) : "", fontCell, bg, Element.ALIGN_RIGHT);
            addCell(table, l.getMontantVente() != null ? formatMontant(l.getMontantVente()) : "", fontCellBold, bg, Element.ALIGN_RIGHT);
            addCell(table, l.getMontantVersement() != null ? formatMontant(l.getMontantVersement()) : "",
                    new Font(Font.HELVETICA, 7.5f, Font.BOLD, VERT_REGLE), bg, Element.ALIGN_RIGHT);
            Double reste = l.getResteAPayerApres();
            addCell(table, reste != null ? formatMontant(reste) : "",
                    new Font(Font.HELVETICA, 7.5f, Font.BOLD, reste != null && reste > 0 ? ROUGE_RETARD : TEXTE), bg, Element.ALIGN_RIGHT);
            addCell(table, l.getUtilisateurNom() != null ? l.getUtilisateurNom() : "", fontCell, bg, Element.ALIGN_LEFT);
        }

        doc.add(table);
        doc.add(new Paragraph(" "));
    }

    private void ajouterPied(Document doc, Boutique boutique) throws DocumentException {
        Font fontPied = new Font(Font.HELVETICA, 8, Font.ITALIC, GRIS_TEXTE);
        Paragraph pied = new Paragraph("Document généré par " + texte(boutique.getNom()) +
                " — " + texte(boutique.getTelephone()) + " — " + LocalDate.now().format(FMT), fontPied);
        pied.setAlignment(Element.ALIGN_CENTER);
        doc.add(new LineSeparator());
        doc.add(pied);
    }

    // ==================== HELPERS ====================

    private String libellePeriode(LocalDate dateDebut, LocalDate dateFin, String type) {
        String periode;
        if (dateDebut != null && dateFin != null) {
            periode = dateDebut.equals(dateFin)
                    ? "Journée du " + dateDebut.format(FMT)
                    : "Du " + dateDebut.format(FMT) + " au " + dateFin.format(FMT);
        } else if (dateDebut != null) {
            periode = "Depuis le " + dateDebut.format(FMT);
        } else if (dateFin != null) {
            periode = "Jusqu'au " + dateFin.format(FMT);
        } else {
            periode = "Tout l'historique";
        }
        if ("VENTE".equalsIgnoreCase(type)) periode += " — achats uniquement";
        else if ("VERSEMENT".equalsIgnoreCase(type)) periode += " — versements uniquement";
        return periode;
    }

    private String texteProduits(ClientReleveLigneDto.VenteReglee vr) {
        if (vr.getProduits() == null || vr.getProduits().isEmpty()) return "-";
        return vr.getProduits().stream()
                .map(p -> texte(p.getProduitNom()) + (p.getQuantiteTexte() != null ? " ×" + p.getQuantiteTexte()
                        : (p.getQuantite() != null ? " ×" + p.getQuantite() : "")))
                .collect(Collectors.joining(", "));
    }

    /** Paragraphe à interligne serré (l'interligne par défaut d'une cellule est trop aéré en 7 pt). */
    private Paragraph paragrapheSerre(Chunk debut) {
        Paragraph p = new Paragraph();
        p.setLeading(0, 1.25f);
        p.add(debut);
        return p;
    }

    private PdfPCell celluleVide(Color bg) {
        PdfPCell cell = new PdfPCell();
        cell.setBackgroundColor(bg);
        cell.setBorderColor(GRIS_BORDURE);
        cell.setPadding(4);
        cell.setPaddingTop(1);
        return cell;
    }

    private void addCell(PdfPTable table, String text, Font font, Color bg, int align) {
        PdfPCell cell = new PdfPCell(new Phrase(text != null ? text : "", font));
        cell.setBackgroundColor(bg);
        cell.setBorderColor(GRIS_BORDURE);
        cell.setPadding(4);
        cell.setHorizontalAlignment(align);
        table.addCell(cell);
    }

    private static String texte(String valeur) {
        return valeur != null ? valeur : "";
    }

    private static double nombre(Object valeur) {
        return valeur instanceof Number n ? n.doubleValue() : 0.0;
    }

    private String formatMontant(double montant) {
        // Espaces insécables fines (U+202F) absentes des polices standard du PDF : espace simple.
        return NumberFormat.getNumberInstance(Locale.FRANCE).format(Math.round(montant))
                .replace(' ', ' ').replace(' ', ' ') + " F";
    }
}
