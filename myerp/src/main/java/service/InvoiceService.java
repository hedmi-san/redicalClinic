package service;

import model.Patient;
import model.Session;
import model.Sold;
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
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

public class InvoiceService {

    private static final float MARGIN = 50;
    private static final float PAGE_WIDTH = PDRectangle.A4.getWidth();
    private static final float PAGE_HEIGHT = PDRectangle.A4.getHeight();
    private static final float CONTENT_WIDTH = PAGE_WIDTH - 2 * MARGIN;

    // Session Table column widths
    private static final float COL_DATE = 100;
    private static final float COL_TREATMENT = 200;
    private static final float COL_COST = 95;
    private static final float COL_PAID = 95;
    private static final float TABLE_WIDTH = COL_DATE + COL_TREATMENT + COL_COST + COL_PAID;
    private static final float TABLE_START_X = MARGIN + (CONTENT_WIDTH - TABLE_WIDTH) / 2;

    // Sales Table column widths (total = 495)
    private static final float SALE_COL_DATE = 75;
    private static final float SALE_COL_ITEM = 200;
    private static final float SALE_COL_PRICE = 80;
    private static final float SALE_COL_QTY = 50;
    private static final float SALE_COL_TOTAL = 90;
    private static final float SALE_TABLE_WIDTH = SALE_COL_DATE + SALE_COL_ITEM + SALE_COL_PRICE + SALE_COL_QTY + SALE_COL_TOTAL;
    private static final float SALE_TABLE_START_X = MARGIN + (CONTENT_WIDTH - SALE_TABLE_WIDTH) / 2;

    private static final float ROW_HEIGHT = 24;

    private static final DecimalFormatSymbols DFS = new DecimalFormatSymbols(Locale.FRENCH);
    static {
        DFS.setGroupingSeparator(' ');
        DFS.setDecimalSeparator(',');
    }
    private static final DecimalFormat MONEY_FMT = new DecimalFormat("#,##0.00", DFS);
    private static final DecimalFormat QTY_FMT = new DecimalFormat("#,##0", DFS);

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
                y = drawHeader(document, cs, fontBold, fontRegular, y, "Facture des seances");

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
     * Generates a branded proforma invoice PDF for selected sales items.
     */
    public void generateSalesProformaInvoice(String clientName, String invoiceDate, List<Sold> items, File outputFile) throws IOException {
        try (PDDocument document = new PDDocument()) {
            PDPage page = new PDPage(PDRectangle.A4);
            document.addPage(page);

            PDFont fontBold = loadBoldFont(document);
            PDFont fontRegular = loadRegularFont(document);

            try (PDPageContentStream cs = new PDPageContentStream(document, page)) {
                float y = PAGE_HEIGHT - MARGIN;

                // === HEADER ===
                y = drawHeader(document, cs, fontBold, fontRegular, y, "Facture Proforma");

                // === METADATA (Client & Date) ===
                y -= 25;
                String displayClient = (clientName != null && !clientName.trim().isEmpty())
                        ? clientName.trim()
                        : "Client au comptant";
                String processedClient = ArabicTextHelper.processForPdf(displayClient);

                String displayDate = (invoiceDate != null && !invoiceDate.trim().isEmpty())
                        ? invoiceDate.trim()
                        : LocalDate.now().format(DateTimeFormatter.ofPattern("dd/MM/yyyy"));

                // Client name on left
                cs.beginText();
                cs.setFont(fontBold, 11);
                cs.setNonStrokingColor(0.2f, 0.26f, 0.33f);
                cs.newLineAtOffset(MARGIN, y);
                cs.showText("Client / Bénéficiaire: ");
                cs.setFont(fontRegular, 11);
                cs.showText(processedClient);
                cs.endText();

                // Date on right
                String dateText = "Date: " + displayDate;
                float dateTextWidth = fontBold.getStringWidth(dateText) / 1000 * 11;
                cs.beginText();
                cs.setFont(fontBold, 11);
                cs.setNonStrokingColor(0.2f, 0.26f, 0.33f);
                cs.newLineAtOffset(PAGE_WIDTH - MARGIN - dateTextWidth, y);
                cs.showText(dateText);
                cs.endText();

                // === SALES TABLE ===
                y -= 25;
                y = drawSalesTable(cs, fontBold, fontRegular, items, y);

                // === SIGNATURE / CACHET & LEGAL NOTICE ===
                if (y > MARGIN + 70) {
                    float sigY = Math.min(y - 25, MARGIN + 90);

                    // Notice left
                    cs.beginText();
                    cs.setFont(fontRegular, 8.5f);
                    cs.setNonStrokingColor(0.45f, 0.50f, 0.58f);
                    cs.newLineAtOffset(MARGIN, sigY);
                    cs.showText("Document proforma sans valeur de facture définitive.");
                    cs.endText();

                    // Stamp box right
                    cs.beginText();
                    cs.setFont(fontBold, 10);
                    cs.setNonStrokingColor(0.25f, 0.30f, 0.38f);
                    cs.newLineAtOffset(PAGE_WIDTH - MARGIN - 130, sigY);
                    cs.showText("Cachet et Signature");
                    cs.endText();

                    // Box outline for stamp
                    cs.setStrokingColor(0.78f, 0.82f, 0.87f);
                    cs.setLineWidth(0.8f);
                    cs.addRect(PAGE_WIDTH - MARGIN - 140, sigY - 55, 140, 50);
                    cs.stroke();
                }
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
                             PDFont fontBold, PDFont fontRegular, float y, String title) throws IOException {
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
            System.err.println("Warning: Could not load logo image: " + e.getMessage());
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
        float titleFontSize = 17;
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
            cs.newLineAtOffset(cellX + 6, tableY - ROW_HEIGHT + 7);
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
                cs.newLineAtOffset(cellX + 6, tableY - ROW_HEIGHT + 7);
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
        cs.newLineAtOffset(TABLE_START_X + 6, tableY - ROW_HEIGHT + 7);
        cs.showText("TOTAL");
        cs.endText();

        // Total cost
        cs.beginText();
        cs.setFont(fontBold, 10);
        cs.setNonStrokingColor(0.05f, 0.47f, 0.43f);
        cs.newLineAtOffset(TABLE_START_X + COL_DATE + COL_TREATMENT + 6, tableY - ROW_HEIGHT + 7);
        cs.showText(String.format("%.2f", totalCost));
        cs.endText();

        // Total paid
        cs.beginText();
        cs.setFont(fontBold, 10);
        cs.setNonStrokingColor(0.05f, 0.47f, 0.43f);
        cs.newLineAtOffset(TABLE_START_X + COL_DATE + COL_TREATMENT + COL_COST + 6, tableY - ROW_HEIGHT + 7);
        cs.showText(String.format("%.2f", totalPaid));
        cs.endText();

        return tableY - ROW_HEIGHT;
    }

    private float drawSalesTable(PDPageContentStream cs, PDFont fontBold,
                                 PDFont fontRegular, List<Sold> items, float y) throws IOException {
        float tableY = y;
        String[] headers = {"Date", "Désignation", "Prix (DZD)", "Qté", "Total (DZD)"};
        float[] colWidths = {SALE_COL_DATE, SALE_COL_ITEM, SALE_COL_PRICE, SALE_COL_QTY, SALE_COL_TOTAL};
        boolean[] isRightAligned = {false, false, true, true, true};

        // --- Header row background ---
        cs.setNonStrokingColor(0.96f, 0.97f, 0.98f);
        cs.addRect(SALE_TABLE_START_X, tableY - ROW_HEIGHT, SALE_TABLE_WIDTH, ROW_HEIGHT);
        cs.fill();

        // --- Header row border ---
        cs.setStrokingColor(0.78f, 0.82f, 0.87f);
        cs.setLineWidth(0.6f);
        cs.addRect(SALE_TABLE_START_X, tableY - ROW_HEIGHT, SALE_TABLE_WIDTH, ROW_HEIGHT);
        cs.stroke();

        // --- Header text ---
        float cellX = SALE_TABLE_START_X;
        for (int i = 0; i < headers.length; i++) {
            cs.beginText();
            cs.setFont(fontBold, 9.5f);
            cs.setNonStrokingColor(0.28f, 0.33f, 0.41f);
            if (isRightAligned[i]) {
                float textWidth = fontBold.getStringWidth(headers[i]) / 1000 * 9.5f;
                cs.newLineAtOffset(cellX + colWidths[i] - textWidth - 6, tableY - ROW_HEIGHT + 7);
            } else {
                cs.newLineAtOffset(cellX + 6, tableY - ROW_HEIGHT + 7);
            }
            cs.showText(headers[i]);
            cs.endText();
            cellX += colWidths[i];
        }

        tableY -= ROW_HEIGHT;

        // --- Data rows ---
        double grandTotal = 0.0;

        for (int row = 0; row < items.size(); row++) {
            Sold item = items.get(row);
            double lineTotal = item.getSoldPrice() * item.getQuantity();
            grandTotal += lineTotal;

            // Alternating row color
            if (row % 2 == 0) {
                cs.setNonStrokingColor(1f, 1f, 1f);
            } else {
                cs.setNonStrokingColor(0.985f, 0.985f, 0.99f);
            }
            cs.addRect(SALE_TABLE_START_X, tableY - ROW_HEIGHT, SALE_TABLE_WIDTH, ROW_HEIGHT);
            cs.fill();

            // Row border
            cs.setStrokingColor(0.89f, 0.91f, 0.94f);
            cs.setLineWidth(0.3f);
            cs.addRect(SALE_TABLE_START_X, tableY - ROW_HEIGHT, SALE_TABLE_WIDTH, ROW_HEIGHT);
            cs.stroke();

            // Cell values
            String dateVal = item.getSoldDate() != null ? item.getSoldDate() : "";
            String rawItemName = item.getItemName() != null ? item.getItemName() : "";
            String processedItemName = ArabicTextHelper.processForPdf(truncateText(rawItemName, 36));
            String priceVal = MONEY_FMT.format(item.getSoldPrice());
            String qtyVal = (item.getQuantity() == Math.floor(item.getQuantity()))
                    ? QTY_FMT.format(item.getQuantity())
                    : MONEY_FMT.format(item.getQuantity());
            String totalVal = MONEY_FMT.format(lineTotal);

            String[] rowData = {dateVal, processedItemName, priceVal, qtyVal, totalVal};

            cellX = SALE_TABLE_START_X;
            for (int i = 0; i < rowData.length; i++) {
                cs.beginText();
                cs.setFont(fontRegular, 9);
                cs.setNonStrokingColor(0.2f, 0.26f, 0.33f);
                if (isRightAligned[i]) {
                    float textWidth = fontRegular.getStringWidth(rowData[i]) / 1000 * 9f;
                    cs.newLineAtOffset(cellX + colWidths[i] - textWidth - 6, tableY - ROW_HEIGHT + 7);
                } else {
                    cs.newLineAtOffset(cellX + 6, tableY - ROW_HEIGHT + 7);
                }
                cs.showText(rowData[i]);
                cs.endText();
                cellX += colWidths[i];
            }

            tableY -= ROW_HEIGHT;
        }

        // --- Total row ---
        cs.setNonStrokingColor(0.90f, 0.96f, 0.95f); // light teal tint
        cs.addRect(SALE_TABLE_START_X, tableY - ROW_HEIGHT, SALE_TABLE_WIDTH, ROW_HEIGHT);
        cs.fill();

        cs.setStrokingColor(0.05f, 0.58f, 0.53f); // teal
        cs.setLineWidth(1f);
        cs.addRect(SALE_TABLE_START_X, tableY - ROW_HEIGHT, SALE_TABLE_WIDTH, ROW_HEIGHT);
        cs.stroke();

        // Total label
        cs.beginText();
        cs.setFont(fontBold, 10.5f);
        cs.setNonStrokingColor(0.05f, 0.47f, 0.43f);
        cs.newLineAtOffset(SALE_TABLE_START_X + 6, tableY - ROW_HEIGHT + 7);
        cs.showText("TOTAL GÉNÉRAL");
        cs.endText();

        // Total amount (right aligned in the Total column)
        String grandTotalStr = MONEY_FMT.format(grandTotal) + " DZD";
        float totalStrWidth = fontBold.getStringWidth(grandTotalStr) / 1000 * 10f;
        cs.beginText();
        cs.setFont(fontBold, 10);
        cs.setNonStrokingColor(0.05f, 0.47f, 0.43f);
        cs.newLineAtOffset(SALE_TABLE_START_X + SALE_TABLE_WIDTH - totalStrWidth - 6, tableY - ROW_HEIGHT + 7);
        cs.showText(grandTotalStr);
        cs.endText();

        return tableY - ROW_HEIGHT;
    }

    private String truncateText(String text, int maxLength) {
        if (text == null) return "";
        if (text.length() <= maxLength) return text;
        return text.substring(0, maxLength - 3) + "...";
    }
}
