package com.ges.boutique.email;

import com.lowagie.text.*;
import com.lowagie.text.pdf.*;
import com.ges.boutique.boutique.Boutique;
import com.ges.boutique.facture.Facture;
import com.ges.boutique.facture.LigneFacture;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.text.NumberFormat;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.Locale;

@Slf4j
@Service
@RequiredArgsConstructor
public class PdfFactureService {

    private final QrCodeService qrCodeService;

    private static final Color BLEU_PRINCIPAL = new Color(37, 99, 235);
    private static final Color BLEU_CLAIR = new Color(219, 234, 254);
    private static final Color GRIS_CLAIR = new Color(248, 250, 252);
    private static final Color GRIS_TEXTE = new Color(100, 116, 139);
    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    public byte[] genererPdfFacture(Facture facture) {
        return genererPdfFacture(facture, "");
    }

    public byte[] genererPdfFacture(Facture facture, String baseUrl) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        Document doc = new Document(PageSize.A4, 40, 40, 50, 50);

        try {
            PdfWriter writer = PdfWriter.getInstance(doc, baos);
            doc.open();

            Boutique boutique = facture.getBoutique();
            String nomBoutique = boutique != null ? boutique.getNom() : "Boutique";

            // ---- ENTÊTE : 3 colonnes [logo+infos | titre facture | QR] ----
            PdfPTable entete = new PdfPTable(3);
            entete.setWidthPercentage(100);
            entete.setWidths(new float[]{2.2f, 2.8f, 1f});
            entete.setSpacingAfter(16);

            // Col 1 — Logo + infos boutique (fond bleu)
            PdfPCell cellBoutique = new PdfPCell();
            cellBoutique.setBorder(Rectangle.NO_BORDER);
            cellBoutique.setPadding(10);
            cellBoutique.setBackgroundColor(BLEU_PRINCIPAL);
            cellBoutique.setVerticalAlignment(Element.ALIGN_MIDDLE);

            // Logo (base64)
            if (boutique != null && boutique.getLogo() != null && !boutique.getLogo().isBlank()) {
                try {
                    String logoData = boutique.getLogo();
                    if (logoData.contains(",")) logoData = logoData.split(",")[1];
                    byte[] logoBytes = Base64.getDecoder().decode(logoData.trim());
                    Image logo = Image.getInstance(logoBytes);
                    logo.scaleToFit(80, 55);
                    cellBoutique.addElement(logo);
                    cellBoutique.addElement(new Paragraph(" ", FontFactory.getFont(FontFactory.HELVETICA, 4)));
                } catch (Exception ignored) {}
            }

            cellBoutique.addElement(new Paragraph(nomBoutique,
                    FontFactory.getFont(FontFactory.HELVETICA_BOLD, 13, Font.BOLD, Color.WHITE)));
            if (boutique != null) {
                if (boutique.getAdresse() != null)
                    cellBoutique.addElement(new Paragraph(boutique.getAdresse() + " — " + boutique.getVille(),
                            FontFactory.getFont(FontFactory.HELVETICA, 8, Color.WHITE)));
                if (boutique.getTelephone() != null)
                    cellBoutique.addElement(new Paragraph("Tél : " + boutique.getTelephone(),
                            FontFactory.getFont(FontFactory.HELVETICA, 8, Color.WHITE)));
                if (boutique.getEmail() != null && !boutique.getEmail().isBlank())
                    cellBoutique.addElement(new Paragraph(boutique.getEmail(),
                            FontFactory.getFont(FontFactory.HELVETICA, 8, Color.WHITE)));
                if (boutique.getNumeroRc() != null)
                    cellBoutique.addElement(new Paragraph("RC: " + boutique.getNumeroRc() + " | IFU: " + boutique.getNumeroIfu(),
                            FontFactory.getFont(FontFactory.HELVETICA, 7, new Color(200, 220, 255))));
            }
            entete.addCell(cellBoutique);

            // Col 2 — Titre FACTURE PRO FORMA + infos clés (fond blanc)
            PdfPCell cellTitre = new PdfPCell();
            cellTitre.setBorder(Rectangle.BOX);
            cellTitre.setBorderColor(BLEU_PRINCIPAL);
            cellTitre.setPadding(12);
            cellTitre.setBackgroundColor(Color.WHITE);
            cellTitre.setVerticalAlignment(Element.ALIGN_MIDDLE);
            cellTitre.setHorizontalAlignment(Element.ALIGN_CENTER);

            Paragraph titreFact = new Paragraph("FACTURE PRO FORMA",
                    FontFactory.getFont(FontFactory.HELVETICA_BOLD, 16, Font.BOLD, BLEU_PRINCIPAL));
            titreFact.setAlignment(Element.ALIGN_CENTER);
            cellTitre.addElement(titreFact);

            Paragraph sep = new Paragraph("─────────────────────────",
                    FontFactory.getFont(FontFactory.HELVETICA, 8, BLEU_CLAIR));
            sep.setAlignment(Element.ALIGN_CENTER);
            sep.setSpacingBefore(4);
            cellTitre.addElement(sep);

            Paragraph numFact = new Paragraph("N° " + facture.getNumeroFacture(),
                    FontFactory.getFont(FontFactory.HELVETICA_BOLD, 11, GRIS_TEXTE));
            numFact.setAlignment(Element.ALIGN_CENTER);
            numFact.setSpacingBefore(4);
            cellTitre.addElement(numFact);

            String dateFmt = facture.getDateCreation() != null ? facture.getDateCreation().format(FMT) : "-";
            Paragraph datePara = new Paragraph("Date : " + dateFmt,
                    FontFactory.getFont(FontFactory.HELVETICA, 9, GRIS_TEXTE));
            datePara.setAlignment(Element.ALIGN_CENTER);
            datePara.setSpacingBefore(3);
            cellTitre.addElement(datePara);

            Paragraph statutPara = new Paragraph("Statut : " + facture.getStatut(),
                    FontFactory.getFont(FontFactory.HELVETICA, 9, GRIS_TEXTE));
            statutPara.setAlignment(Element.ALIGN_CENTER);
            cellTitre.addElement(statutPara);
            entete.addCell(cellTitre);

            // Col 3 — QR code (fond bleu)
            String urlFacture = baseUrl + "/api/caisse/factures/" + facture.getId() + "/pdf";
            byte[] qrBytes = qrCodeService.genererQrCode(urlFacture, 120, 120);

            PdfPCell cellQr = new PdfPCell();
            cellQr.setBorder(Rectangle.NO_BORDER);
            cellQr.setBackgroundColor(BLEU_PRINCIPAL);
            cellQr.setHorizontalAlignment(Element.ALIGN_CENTER);
            cellQr.setVerticalAlignment(Element.ALIGN_MIDDLE);
            cellQr.setPadding(6);

            if (qrBytes.length > 0) {
                Image qrImg = Image.getInstance(qrBytes);
                qrImg.scaleToFit(85, 85);
                Paragraph qrPara = new Paragraph();
                qrPara.add(new Chunk(qrImg, 0, 0));
                qrPara.setAlignment(Element.ALIGN_CENTER);
                cellQr.addElement(qrPara);
            }
            Paragraph qrLabel = new Paragraph("Scanner pour\nvoir le PDF",
                    FontFactory.getFont(FontFactory.HELVETICA, 7, Color.WHITE));
            qrLabel.setAlignment(Element.ALIGN_CENTER);
            cellQr.addElement(qrLabel);
            entete.addCell(cellQr);
            doc.add(entete);

            // ---- SECTION CLIENT ----
            PdfPTable infos = new PdfPTable(1);
            infos.setWidthPercentage(60);
            infos.setHorizontalAlignment(Element.ALIGN_LEFT);
            infos.setSpacingAfter(16);

            PdfPCell cellClient = new PdfPCell();
            cellClient.setBorder(Rectangle.BOX);
            cellClient.setBorderColor(BLEU_CLAIR);
            cellClient.setPadding(10);
            cellClient.setBackgroundColor(GRIS_CLAIR);

            Paragraph titreCli = new Paragraph("FACTURÉ À",
                    FontFactory.getFont(FontFactory.HELVETICA_BOLD, 8, Font.BOLD, GRIS_TEXTE));
            titreCli.setSpacingAfter(4);
            cellClient.addElement(titreCli);

            addLabelValeur(cellClient, "",
                    facture.getClientNom() != null ? facture.getClientNom() + (facture.getClientPrenom() != null ? " " + facture.getClientPrenom() : "") : "Client divers");
            if (facture.getClientTelephone() != null)
                addLabelValeur(cellClient, "Tél :", facture.getClientTelephone());
            if (facture.getClientAdresse() != null)
                addLabelValeur(cellClient, "Adresse :", facture.getClientAdresse());
            infos.addCell(cellClient);
            doc.add(infos);

            // ---- TABLEAU LIGNES ----
            PdfPTable tableau = new PdfPTable(5);
            tableau.setWidthPercentage(100);
            tableau.setWidths(new float[]{3f, 1f, 1.5f, 1f, 1.5f});
            tableau.setSpacingAfter(12);

            String[] entetes = {"Désignation", "Qté", "Prix unitaire", "Remise", "Total"};
            for (String h : entetes) {
                PdfPCell cell = new PdfPCell(new Phrase(h,
                        FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10, Font.BOLD, Color.WHITE)));
                cell.setBackgroundColor(BLEU_PRINCIPAL);
                cell.setPadding(8);
                cell.setHorizontalAlignment(Element.ALIGN_CENTER);
                tableau.addCell(cell);
            }

            NumberFormat nf = NumberFormat.getIntegerInstance(Locale.FRANCE);
            boolean alternatif = false;
            for (LigneFacture ligne : facture.getLignes()) {
                Color bg = alternatif ? GRIS_CLAIR : Color.WHITE;
                alternatif = !alternatif;

                String designation = ligne.getDesignation() != null ? ligne.getDesignation()
                        : (ligne.getProduit() != null ? ligne.getProduit().getNom() : "-");

                addCellTableau(tableau, designation, bg, Element.ALIGN_LEFT);
                // Vente à la mesure : "1,25 kg" et "600 F/kg" plutôt que 1250 et 0,6 F.
                addCellTableau(tableau, com.ges.boutique.produit.ModeMesure.quantiteLisible(ligne.getProduit(), ligne.getQuantite()), bg, Element.ALIGN_CENTER);
                addCellTableau(tableau, nf.format(com.ges.boutique.produit.ModeMesure.prixLisible(ligne.getProduit(), ligne.getPrixUnitaire())) + " F"
                        + com.ges.boutique.produit.ModeMesure.suffixePrix(ligne.getProduit()), bg, Element.ALIGN_RIGHT);
                addCellTableau(tableau, nf.format(ligne.getMontantRemise() != null ? ligne.getMontantRemise() : 0) + " F", bg, Element.ALIGN_RIGHT);
                addCellTableau(tableau, nf.format(ligne.getSousTotal() != null ? ligne.getSousTotal() : 0) + " F", bg, Element.ALIGN_RIGHT);
            }
            doc.add(tableau);

            // ---- TOTAUX ----
            PdfPTable totaux = new PdfPTable(2);
            totaux.setWidthPercentage(50);
            totaux.setHorizontalAlignment(Element.ALIGN_RIGHT);
            totaux.setSpacingAfter(20);

            if (facture.getMontantRemiseTotal() != null && facture.getMontantRemiseTotal() > 0) {
                addLigneTotaux(totaux, "Remise totale :", nf.format(facture.getMontantRemiseTotal()) + " FCFA", false);
            }
            addLigneTotaux(totaux, "TOTAL À PAYER :", nf.format(facture.getMontantTotal()) + " FCFA", true);
            doc.add(totaux);

            // ---- NOTES ----
            if (facture.getNotes() != null && !facture.getNotes().isBlank()) {
                Paragraph notes = new Paragraph("Note : " + facture.getNotes(),
                        FontFactory.getFont(FontFactory.HELVETICA_OBLIQUE, 9, GRIS_TEXTE));
                notes.setSpacingBefore(8);
                doc.add(notes);
            }

            // ---- PIED DE PAGE ----
            Paragraph footer = new Paragraph("Merci pour votre confiance.",
                    FontFactory.getFont(FontFactory.HELVETICA, 9, GRIS_TEXTE));
            footer.setAlignment(Element.ALIGN_CENTER);
            footer.setSpacingBefore(20);
            doc.add(footer);

        } catch (Exception e) {
            log.error("Erreur génération PDF facture {}: {}", facture.getId(), e.getMessage());
        } finally {
            doc.close();
        }
        return baos.toByteArray();
    }

    private void addLabelValeur(PdfPCell cell, String label, String valeur) {
        Paragraph p = new Paragraph();
        p.add(new Chunk(label + " ", FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9, GRIS_TEXTE)));
        p.add(new Chunk(valeur, FontFactory.getFont(FontFactory.HELVETICA, 9)));
        p.setSpacingAfter(3);
        cell.addElement(p);
    }

    private void addCellTableau(PdfPTable table, String texte, Color bg, int alignement) {
        PdfPCell cell = new PdfPCell(new Phrase(texte, FontFactory.getFont(FontFactory.HELVETICA, 9)));
        cell.setBackgroundColor(bg);
        cell.setPadding(6);
        cell.setHorizontalAlignment(alignement);
        cell.setBorderColor(BLEU_CLAIR);
        table.addCell(cell);
    }

    private void addLigneTotaux(PdfPTable table, String label, String valeur, boolean gras) {
        Font font = gras
                ? FontFactory.getFont(FontFactory.HELVETICA_BOLD, 11, Font.BOLD, BLEU_PRINCIPAL)
                : FontFactory.getFont(FontFactory.HELVETICA, 10, GRIS_TEXTE);

        PdfPCell cLabel = new PdfPCell(new Phrase(label, font));
        cLabel.setBorder(gras ? Rectangle.TOP : Rectangle.NO_BORDER);
        cLabel.setPadding(6);
        cLabel.setHorizontalAlignment(Element.ALIGN_LEFT);
        if (gras) cLabel.setBackgroundColor(BLEU_CLAIR);
        table.addCell(cLabel);

        PdfPCell cVal = new PdfPCell(new Phrase(valeur, font));
        cVal.setBorder(gras ? Rectangle.TOP : Rectangle.NO_BORDER);
        cVal.setPadding(6);
        cVal.setHorizontalAlignment(Element.ALIGN_RIGHT);
        if (gras) cVal.setBackgroundColor(BLEU_CLAIR);
        table.addCell(cVal);
    }
}
