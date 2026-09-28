package com.myerp;

import config.DatabaseInitializer;
import dao.SoldDAO;
import model.Sold;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.text.PDFTextStripper;
import service.InvoiceService;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

public class SalesProformaVerificationTest {

    public static void main(String[] args) {
        System.out.println("==================================================");
        System.out.println("  RUNNING SALES PROFORMA INVOICE VERIFICATION     ");
        System.out.println("==================================================");

        try {
            // 1. Initialize Database
            System.out.println("\n[Test 1] Testing Database & SoldDAO methods...");
            DatabaseInitializer.initializeDatabase();
            SoldDAO soldDAO = new SoldDAO();

            // Test adding sample sales if needed
            Sold s1 = new Sold();
            s1.setItemName("Bande élastique de résistance");
            s1.setSoldDate("2026-09-15");
            s1.setSoldPrice(1500.0);
            s1.setQuantity(2.0);
            soldDAO.addSold(s1);

            Sold s2 = new Sold();
            s2.setItemName("حزام طبي داعم للظهر (Ceinture lombaire)");
            s2.setSoldDate("2026-09-20");
            s2.setSoldPrice(4500.0);
            s2.setQuantity(1.0);
            soldDAO.addSold(s2);

            List<Sold> allSolds = soldDAO.getAllSolds();
            if (allSolds == null || allSolds.isEmpty()) {
                throw new AssertionError("getAllSolds returned empty list!");
            }
            System.out.println("  ✓ getAllSolds returned " + allSolds.size() + " sales records.");

            List<Sold> filteredRange = soldDAO.getSoldsByDateRange("2026-09-01", "2026-09-30");
            if (filteredRange == null || filteredRange.isEmpty()) {
                throw new AssertionError("getSoldsByDateRange returned empty list!");
            }
            System.out.println("  ✓ getSoldsByDateRange returned " + filteredRange.size() + " records for September 2026.");

            // 2. Test Logo Resource Availability
            System.out.println("\n[Test 2] Checking logo resource availability...");
            try (InputStream logoStream = InvoiceService.class.getResourceAsStream("/img/logo.png")) {
                if (logoStream == null) {
                    throw new AssertionError("Logo resource /img/logo.png not found!");
                }
                System.out.println("  ✓ Logo resource /img/logo.png is available.");
            }

            // 3. Test PDF Invoice Generation
            System.out.println("\n[Test 3] Generating Proforma Invoice PDF with InvoiceService...");
            InvoiceService invoiceService = new InvoiceService();

            List<Sold> testItems = new ArrayList<>();
            testItems.add(s1);
            testItems.add(s2);

            Path tempPdfPath = Files.createTempFile("test_facture_proforma_", ".pdf");
            File tempPdfFile = tempPdfPath.toFile();
            tempPdfFile.deleteOnExit();

            String testClient = "Cabinet Médical El Chifa / الدكتور كمال";
            String testInvoiceDate = "28/09/2026";

            invoiceService.generateSalesProformaInvoice(testClient, testInvoiceDate, testItems, tempPdfFile);

            if (!tempPdfFile.exists() || tempPdfFile.length() == 0) {
                throw new AssertionError("Generated PDF does not exist or is empty!");
            }
            System.out.println("  ✓ PDF successfully generated at: " + tempPdfFile.getAbsolutePath() + " (size: " + tempPdfFile.length() + " bytes)");

            // 4. Verify PDF structure with Apache PDFBox text stripper & render preview PNG
            System.out.println("\n[Test 4] Validating PDF content & rendering preview...");
            try (PDDocument doc = Loader.loadPDF(tempPdfFile)) {
                if (doc.getNumberOfPages() < 1) {
                    throw new AssertionError("PDF should contain at least 1 page!");
                }
                System.out.println("  ✓ Page count: " + doc.getNumberOfPages());

                PDFTextStripper stripper = new PDFTextStripper();
                String text = stripper.getText(doc);

                // Verify key text elements
                String[] expectedSnippets = {
                        "CABINET NOUR EL ISLAM",
                        "Facture Proforma",
                        "Date: 28/09/2026",
                        "TOTAL",
                        "7 500,00 DZD"
                };

                for (String snippet : expectedSnippets) {
                    if (!text.contains(snippet)) {
                        throw new AssertionError("PDF text does not contain expected snippet: '" + snippet + "'\nFull text:\n" + text);
                    }
                    System.out.println("  ✓ Verified presence of: '" + snippet + "'");
                }

                // Render page 0 as PNG image for visual inspection
                PDFRenderer renderer = new PDFRenderer(doc);
                BufferedImage image = renderer.renderImageWithDPI(0, 150);
                Path artifactDir = Paths.get("C:\\Users\\LAPTOP SPIRIT\\.gemini\\antigravity-ide\\brain\\5cfd9434-5fe2-4896-b984-45f894a264c5");
                if (Files.exists(artifactDir)) {
                    File previewFile = artifactDir.resolve("proforma_invoice_preview.png").toFile();
                    ImageIO.write(image, "PNG", previewFile);
                    System.out.println("  ✓ Rendered preview image to: " + previewFile.getAbsolutePath());
                }
            }

            System.out.println("\n==================================================");
            System.out.println("  ALL SALES PROFORMA TESTS PASSED SUCCESSFULLY!   ");
            System.out.println("==================================================");
            System.exit(0);

        } catch (Throwable t) {
            System.err.println("\n❌ TEST FAILED: " + t.getMessage());
            t.printStackTrace();
            System.exit(1);
        }
    }
}
