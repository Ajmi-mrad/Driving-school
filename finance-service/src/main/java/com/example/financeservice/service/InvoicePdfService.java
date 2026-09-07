package com.example.financeservice.service;

import com.example.financeservice.client.UserClient;
import com.example.financeservice.client.dto.UserInfo;
import com.example.financeservice.domain.Invoice;
import com.example.financeservice.domain.InvoiceType;
import com.lowagie.text.Document;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.FontFactory;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import org.springframework.stereotype.Service;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * Génère un PDF d'une page pour une facture ou un reçu. Mise en page partagée entre les deux
 * types (seul le titre change). Le nom du client est résolu via l'auth-service ({@link UserClient}).
 */
@Service
public class InvoicePdfService {

    private static final Color BRAND = new Color(0x1F, 0x4E, 0x9C);
    private static final DateTimeFormatter DATE =
            DateTimeFormatter.ofPattern("dd/MM/yyyy", Locale.FRANCE).withZone(ZoneId.systemDefault());

    private final UserClient userClient;

    public InvoicePdfService(UserClient userClient) {
        this.userClient = userClient;
    }

    public String filenameFor(Invoice invoice) {
        return invoice.getNumber() + ".pdf";
    }

    /** Construit le PDF du document. Ne lève pas d'exception métier : purement une transformation. */
    public byte[] render(Invoice invoice) {
        String clientName = resolveClientName(invoice.getClientId());

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Document document = new Document(PageSize.A4, 56, 56, 64, 56);
        PdfWriter.getInstance(document, out);
        document.open();

        Font brandFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 22, BRAND);
        Font subFont = FontFactory.getFont(FontFactory.HELVETICA, 10, Color.GRAY);
        Font titleFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 16, Color.BLACK);
        Font labelFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 11, Color.DARK_GRAY);
        Font valueFont = FontFactory.getFont(FontFactory.HELVETICA, 11, Color.BLACK);
        Font totalFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 14, BRAND);

        Paragraph brand = new Paragraph("AUTO-ÉCOLE", brandFont);
        document.add(brand);
        Paragraph sub = new Paragraph("Gestion d'auto-école", subFont);
        sub.setSpacingAfter(24);
        document.add(sub);

        boolean receipt = invoice.getType() == InvoiceType.RECEIPT;
        Paragraph title = new Paragraph(receipt ? "REÇU" : "FACTURE", titleFont);
        title.setSpacingAfter(4);
        document.add(title);
        Paragraph number = new Paragraph("N° " + invoice.getNumber(), subFont);
        number.setSpacingAfter(20);
        document.add(number);

        PdfPTable table = new PdfPTable(2);
        table.setWidthPercentage(100);
        table.setWidths(new int[]{1, 2});
        table.setSpacingAfter(24);
        addRow(table, "Date", DATE.format(invoice.getIssuedAt()), labelFont, valueFont);
        addRow(table, "Client", clientName, labelFont, valueFont);
        addRow(table, "Inscription", invoice.getEnrollmentId().toString(), labelFont, valueFont);
        addRow(table, "Type", receipt ? "Paiement reçu" : "Facture d'inscription", labelFont, valueFont);
        document.add(table);

        Paragraph total = new Paragraph(
                (receipt ? "Montant réglé : " : "Montant dû : ") + formatAmount(invoice.getAmount()),
                totalFont);
        total.setAlignment(Element.ALIGN_RIGHT);
        document.add(total);

        Paragraph footer = new Paragraph(
                "\n\nDocument généré automatiquement — Auto-École.", subFont);
        footer.setSpacingBefore(48);
        document.add(footer);

        document.close();
        return out.toByteArray();
    }

    private void addRow(PdfPTable table, String label, String value, Font labelFont, Font valueFont) {
        table.addCell(borderless(new Phrase(label, labelFont)));
        table.addCell(borderless(new Phrase(value, valueFont)));
    }

    private PdfPCell borderless(Phrase phrase) {
        PdfPCell cell = new PdfPCell(phrase);
        cell.setBorder(0);
        cell.setPaddingBottom(6);
        return cell;
    }

    private String formatAmount(BigDecimal amount) {
        return String.format(Locale.FRANCE, "%,.2f €", amount);
    }

    private String resolveClientName(String clientId) {
        return userClient.getByKeycloakId(clientId)
                .map(this::fullName)
                .filter(s -> !s.isBlank())
                .orElse(clientId);
    }

    private String fullName(UserInfo u) {
        return ((u.firstName() == null ? "" : u.firstName()) + " "
                + (u.lastName() == null ? "" : u.lastName())).trim();
    }
}
