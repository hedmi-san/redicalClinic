package service;

import model.Patient;
import model.Session;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType0Font;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import util.ArabicTextHelper;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;

public class InvoiceService {

    private static final float MARGIN = 50;
    private static final float PAGE_WIDTH = PDRectangle.A4.getWidth();
    private static final float PAGE_HEIGHT = PDRectangle.A4.getHeight();
    private static final float CONTENT_WIDTH = PAGE_WIDTH - 2 * MARGIN;

    // Table column widths
    private static final float COL_DATE = 100;
    private static final float COL_TREATMENT = 200;
    private static final float COL_COST = 95;
    private static final float COL_PAID = 95;
    private static final float TABLE_WIDTH = COL_DATE + COL_TREATMENT + COL_COST + COL_PAID;
    private static final float TABLE_START_X = MARGIN + (CONTENT_WIDTH - TABLE_WIDTH) / 2;

    private static final float ROW_HEIGHT = 25;

    public void generateInvoice(Patient patient, List<Session> sessions, File outputFile) throws IOException {
        try (PDDocument document = new PDDocument()) {
            PDPage page = new PDPage(PDRectangle.A4);
            document.addPage(page);

            // Load Unicode-capable fonts (Arial supports Arabic + Latin)
            PDFont fontBold = loadBoldFont(document);
            PDFont fontRegular = loadRegularFont(document);

            try (PDPageContentStream cs = new PDPageContentStream(document, page)) {
                float y = PAGE_HEIGHT - MARGIN;

                // === HEADER ===
                y = drawHeader(document, cs, fontBold, fontRegular, y);

                // === PATIENT NAME ===
                y -= 30;
                String patientName = patient.getName() != null ? patient.getName() : "N/A";
                String processedName = ArabicTextHelper.processForPdf(patientName);

                cs.beginText();
                cs.setFont(fontBold, 13);
                cs.newLineAtOffset(MARGIN, y);
                cs.showText("Patient: ");
                cs.setFont(fontRegular, 13);
                cs.showText(processedName);
                cs.endText();

                // === SESSIONS TABLE ===
                y -= 30;
                y = drawSessionTable(cs, fontBold, fontRegular, sessions, y);
            }

            document.save(outputFile);
        }
    }

    /**
     * Loads a regular TrueType font with Arabic support.
     * Falls back to Helvetica if no system font is found.
     */
    private PDFont loadRegularFont(PDDocument document) throws IOException {
        String[] systemFontPaths = {
                "C:\\Windows\\Fonts\\arial.ttf",
                "C:\\Windows\\Fonts\\tahoma.ttf",
                "C:\\Windows\\Fonts\\calibri.ttf",
        };
        for (String path : systemFontPaths) {
            File fontFile = new File(path);
            if (fontFile.exists()) {
                return PDType0Font.load(document, fontFile);
            }
        }
        // Fallback — no Arabic support
        return new PDType1Font(Standard14Fonts.FontName.HELVETICA);
    }

    /**
     * Loads a bold TrueType font with Arabic support.
     * Falls back to Helvetica Bold if no system font is found.
     */
    private PDFont loadBoldFont(PDDocument document) throws IOException {
        String[] systemFontPaths = {
                "C:\\Windows\\Fonts\\arialbd.ttf",
                "C:\\Windows\\Fonts\\tahomabd.ttf",
                "C:\\Windows\\Fonts\\calibrib.ttf",
        };
        for (String path : systemFontPaths) {
            File fontFile = new File(path);
            if (fontFile.exists()) {
                return PDType0Font.load(document, fontFile);
            }
        }
        // Fallback — no Arabic support
        return new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
    }

    private float drawHeader(PDDocument document, PDPageContentStream cs,
                             PDFont fontBold, PDFont fontRegular, float y) throws IOException {
        float logoSize = 65;
        float startY = y;

        // --- Logo (top left) ---
        try {
            InputStream logoStream = getClass().getResourceAsStream("/img/logo.png");
            if (logoStream != null) {
                byte[] logoBytes = logoStream.readAllBytes();
                logoStream.close();
                PDImageXObject logoImage = PDImageXObject.createFromByteArray(document, logoBytes, "logo.png");
                cs.drawImage(logoImage, MARGIN, startY - logoSize, logoSize, logoSize);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        // --- Clinic name (top right) ---
        String clinicLine1 = "CABINET NOUR EL ISLAM";
        String clinicLine2 = "REEDUCATION FONCTIONNELLE";
        String clinicLine3 = "ET MOTRICE";

        float clinicFontSize = 10;
        float rightX = PAGE_WIDTH - MARGIN;

        cs.beginText();
        cs.setFont(fontBold, clinicFontSize);
        float textW1 = fontBold.getStringWidth(clinicLine1) / 1000 * clinicFontSize;
        cs.newLineAtOffset(rightX - textW1, startY - 15);
        cs.showText(clinicLine1);
        cs.endText();

        cs.beginText();
        cs.setFont(fontRegular, clinicFontSize - 1);
        float textW2 = fontRegular.getStringWidth(clinicLine2) / 1000 * (clinicFontSize - 1);
        cs.newLineAtOffset(rightX - textW2, startY - 28);
        cs.showText(clinicLine2);
        cs.endText();

        cs.beginText();
        cs.setFont(fontRegular, clinicFontSize - 1);
        float textW3 = fontRegular.getStringWidth(clinicLine3) / 1000 * (clinicFontSize - 1);
        cs.newLineAtOffset(rightX - textW3, startY - 40);
        cs.showText(clinicLine3);
        cs.endText();

        // --- Title (center) ---
        String title = "Facture des seances";
        float titleFontSize = 18;
        float titleWidth = fontBold.getStringWidth(title) / 1000 * titleFontSize;
        float titleX = (PAGE_WIDTH - titleWidth) / 2;

        cs.beginText();
        cs.setFont(fontBold, titleFontSize);
        cs.newLineAtOffset(titleX, startY - 35);
        cs.showText(title);
        cs.endText();

        // --- Horizontal separator ---
        float lineY = startY - logoSize - 10;
        cs.setLineWidth(1.2f);
        cs.setStrokingColor(0.13f, 0.58f, 0.53f); // teal
        cs.moveTo(MARGIN, lineY);
        cs.lineTo(PAGE_WIDTH - MARGIN, lineY);
        cs.stroke();

        return lineY;
    }

    private float drawSessionTable(PDPageContentStream cs, PDFont fontBold,
                                   PDFont fontRegular, List<Session> sessions, float y) throws IOException {
        float tableY = y;
        String[] headers = {"Date", "Traitement", "Cout (DZD)", "Paye (DZD)"};
        float[] colWidths = {COL_DATE, COL_TREATMENT, COL_COST, COL_PAID};

        // --- Header row background ---
        cs.setNonStrokingColor(0.96f, 0.97f, 0.98f);
        cs.addRect(TABLE_START_X, tableY - ROW_HEIGHT, TABLE_WIDTH, ROW_HEIGHT);
        cs.fill();

        // --- Header row border ---
        cs.setStrokingColor(0.78f, 0.82f, 0.87f);
        cs.setLineWidth(0.5f);
        cs.addRect(TABLE_START_X, tableY - ROW_HEIGHT, TABLE_WIDTH, ROW_HEIGHT);
        cs.stroke();

        // --- Header text ---
        float cellX = TABLE_START_X;
        for (int i = 0; i < headers.length; i++) {
            cs.beginText();
            cs.setFont(fontBold, 10);
            cs.setNonStrokingColor(0.28f, 0.33f, 0.41f);
            cs.newLineAtOffset(cellX + 6, tableY - ROW_HEIGHT + 8);
            cs.showText(headers[i]);
            cs.endText();
            cellX += colWidths[i];
        }

        tableY -= ROW_HEIGHT;

        // --- Data rows ---
        double totalCost = 0;
        double totalPaid = 0;

        for (int row = 0; row < sessions.size(); row++) {
            Session session = sessions.get(row);
            totalCost += session.getCost();
            totalPaid += session.getPaidAmount();

            // Alternating row color
            if (row % 2 == 0) {
                cs.setNonStrokingColor(1f, 1f, 1f);
            } else {
                cs.setNonStrokingColor(0.98f, 0.98f, 0.99f);
            }
            cs.addRect(TABLE_START_X, tableY - ROW_HEIGHT, TABLE_WIDTH, ROW_HEIGHT);
            cs.fill();

            // Row border
            cs.setStrokingColor(0.89f, 0.91f, 0.94f);
            cs.setLineWidth(0.3f);
            cs.addRect(TABLE_START_X, tableY - ROW_HEIGHT, TABLE_WIDTH, ROW_HEIGHT);
            cs.stroke();

            // Process Arabic text in treatment column
            String treatment = session.getTreatment() != null ? session.getTreatment() : "";
            String processedTreatment = ArabicTextHelper.processForPdf(truncateText(treatment, 38));

            String[] rowData = {
                    session.getDate() != null ? session.getDate() : "",
                    processedTreatment,
                    String.format("%.2f", session.getCost()),
                    String.format("%.2f", session.getPaidAmount())
            };

            cellX = TABLE_START_X;
            for (int i = 0; i < rowData.length; i++) {
                cs.beginText();
                cs.setFont(fontRegular, 9);
                cs.setNonStrokingColor(0.2f, 0.26f, 0.33f);
                cs.newLineAtOffset(cellX + 6, tableY - ROW_HEIGHT + 8);
                cs.showText(rowData[i]);
                cs.endText();
                cellX += colWidths[i];
            }

            tableY -= ROW_HEIGHT;
        }

        // --- Total row ---
        cs.setNonStrokingColor(0.90f, 0.96f, 0.95f); // light teal tint
        cs.addRect(TABLE_START_X, tableY - ROW_HEIGHT, TABLE_WIDTH, ROW_HEIGHT);
        cs.fill();

        cs.setStrokingColor(0.05f, 0.58f, 0.53f); // teal
        cs.setLineWidth(1f);
        cs.addRect(TABLE_START_X, tableY - ROW_HEIGHT, TABLE_WIDTH, ROW_HEIGHT);
        cs.stroke();

        // Total label
        cs.beginText();
        cs.setFont(fontBold, 11);
        cs.setNonStrokingColor(0.05f, 0.47f, 0.43f);
        cs.newLineAtOffset(TABLE_START_X + 6, tableY - ROW_HEIGHT + 8);
        cs.showText("TOTAL");
        cs.endText();

        // Total cost
        cs.beginText();
        cs.setFont(fontBold, 10);
        cs.setNonStrokingColor(0.05f, 0.47f, 0.43f);
        cs.newLineAtOffset(TABLE_START_X + COL_DATE + COL_TREATMENT + 6, tableY - ROW_HEIGHT + 8);
        cs.showText(String.format("%.2f", totalCost));
        cs.endText();

        // Total paid
        cs.beginText();
        cs.setFont(fontBold, 10);
        cs.setNonStrokingColor(0.05f, 0.47f, 0.43f);
        cs.newLineAtOffset(TABLE_START_X + COL_DATE + COL_TREATMENT + COL_COST + 6, tableY - ROW_HEIGHT + 8);
        cs.showText(String.format("%.2f", totalPaid));
        cs.endText();

        return tableY - ROW_HEIGHT;
    }

    private String truncateText(String text, int maxLength) {
        if (text.length() <= maxLength) return text;
        return text.substring(0, maxLength - 3) + "...";
    }
}
