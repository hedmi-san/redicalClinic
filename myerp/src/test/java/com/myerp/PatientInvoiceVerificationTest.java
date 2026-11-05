package com.myerp;

import config.DatabaseConfig;
import config.DatabaseInitializer;
import controller.PatientSessionInvoiceFormController;
import model.Patient;
import model.Session;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import service.InvoiceService;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class PatientInvoiceVerificationTest {

    public static void main(String[] args) {
        System.out.println("==========================================================");
        System.out.println("  RUNNING PATIENT SESSION INVOICE REDESIGN VERIFICATION   ");
        System.out.println("==========================================================");

        try {
            // 1. Initialize Database
            System.out.println("\n[Test 1] Initializing Database...");
            DatabaseInitializer.initializeDatabase();
            System.out.println("  ✓ Database initialized successfully.");

            InvoiceService invoiceService = new InvoiceService();

            // 2. Test Scenario A: Patient with Advance Credit (Balance < 0)
            System.out.println("\n[Test 2] Testing PDF generation with Advance Credit (balance < 0)...");
            Patient creditPatient = new Patient(101, "Karim Boudiaf / كريم بوضياف", "0550 11 22 33");
            // totalCost = 4000, totalPaid = 6000 -> balance = -2000 (credit)
            creditPatient.setTotalCost(4000.0);
            creditPatient.setTotalPaid(6000.0);

            List<Session> creditSessions = new ArrayList<>();
            creditSessions.add(new Session(1, 101, "15/10/2026", "Séance kinésithérapie dos", 1500.0, 0.0));
            creditSessions.add(new Session(2, 101, "18/10/2026", "Rééducation fonctionnelle membre supérieur", 1500.0, 0.0));

            Path creditPdfPath = Files.createTempFile("test_invoice_credit_", ".pdf");
            File creditPdfFile = creditPdfPath.toFile();
            creditPdfFile.deleteOnExit();

            invoiceService.generateInvoice(creditPatient, creditSessions, "20/10/2026", creditPdfFile);
            assertPdfContent(creditPdfFile,
                    "Facture des seances",
                    "Patient",
                    "Telephone",
                    "0550 11 22 33",
                    "Prestation / Traitement",
                    "Montant (DZD)",
                    "TOTAL DES PRESTATIONS",
                    "3 000,00 DZD",
                    "Montant regle :",
                    "Avance disponible :",
                    "2 000,00 DZD"
            );
            System.out.println("  ✓ Advance credit PDF verified (prestation total=3000, covered=3000, credit=2000).");

            // 3. Test Scenario B: Patient with Outstanding Debt (Balance > 0)
            System.out.println("\n[Test 3] Testing PDF generation with Outstanding Debt (balance > 0)...");
            Patient debtPatient = new Patient(102, "Amina Mansouri", "0661 44 55 66");
            // totalCost = 5000, totalPaid = 3500 -> balance = 1500 due
            debtPatient.setTotalCost(5000.0);
            debtPatient.setTotalPaid(3500.0);

            List<Session> debtSessions = new ArrayList<>();
            debtSessions.add(new Session(3, 102, "01/10/2026", "Massage rééducation", 1500.0, 0.0));
            debtSessions.add(new Session(4, 102, "05/10/2026", "Drainage lymphatique", 1500.0, 0.0));

            Path debtPdfPath = Files.createTempFile("test_invoice_debt_", ".pdf");
            File debtPdfFile = debtPdfPath.toFile();
            debtPdfFile.deleteOnExit();

            invoiceService.generateInvoice(debtPatient, debtSessions, "10/10/2026", debtPdfFile);
            assertPdfContent(debtPdfFile,
                    "Facture des seances",
                    "Amina Mansouri",
                    "0661 44 55 66",
                    "TOTAL DES PRESTATIONS",
                    "3 000,00 DZD",
                    "Montant regle :",
                    "1 500,00 DZD",
                    "Reste a payer :",
                    "1 500,00 DZD"
            );
            System.out.println("  ✓ Debt PDF verified (prestation total=3000, regle=1500, reste a payer=1500).");

            // 4. Test Scenario C: Patient with Settled Account (Balance == 0)
            System.out.println("\n[Test 4] Testing PDF generation with Settled Account (balance == 0)...");
            Patient settledPatient = new Patient(103, "Yacine Brahimi", "0770 99 88 77");
            settledPatient.setTotalCost(3000.0);
            settledPatient.setTotalPaid(3000.0);

            List<Session> settledSessions = new ArrayList<>();
            settledSessions.add(new Session(5, 103, "02/10/2026", "Séance ultrasonothérapie", 1500.0, 1500.0));
            settledSessions.add(new Session(6, 103, "04/10/2026", "Séance électrothérapie", 1500.0, 1500.0));

            Path settledPdfPath = Files.createTempFile("test_invoice_settled_", ".pdf");
            File settledPdfFile = settledPdfPath.toFile();
            settledPdfFile.deleteOnExit();

            invoiceService.generateInvoice(settledPatient, settledSessions, "05/10/2026", settledPdfFile);
            assertPdfContent(settledPdfFile,
                    "Facture des seances",
                    "Yacine Brahimi",
                    "TOTAL DES PRESTATIONS",
                    "3 000,00 DZD",
                    "Montant regle :",
                    "3 000,00 DZD",
                    "Compte solde :",
                    "0,00 DZD"
            );
            System.out.println("  ✓ Settled PDF verified (prestation total=3000, regle=3000, compte solde=0).");

            // 5. Test Scenario D: Selection Controller & Dynamic Totals Model
            System.out.println("\n[Test 5] Testing SessionSelection logic and live summation...");
            PatientSessionInvoiceFormController.SessionSelection s1 =
                    new PatientSessionInvoiceFormController.SessionSelection(debtSessions.get(0));
            PatientSessionInvoiceFormController.SessionSelection s2 =
                    new PatientSessionInvoiceFormController.SessionSelection(debtSessions.get(1));

            if (s1.isSelected() || s2.isSelected()) {
                throw new AssertionError("Sessions should initially be unselected!");
            }

            s1.setSelected(true);
            if (!s1.isSelected()) {
                throw new AssertionError("s1 should be selected!");
            }

            double selectedTotal = (s1.isSelected() ? s1.getSession().getCost() : 0.0) +
                                   (s2.isSelected() ? s2.getSession().getCost() : 0.0);
            if (Math.abs(selectedTotal - 1500.0) > 0.001) {
                throw new AssertionError("Expected selected total 1500.0, got: " + selectedTotal);
            }

            s2.setSelected(true);
            selectedTotal = (s1.isSelected() ? s1.getSession().getCost() : 0.0) +
                            (s2.isSelected() ? s2.getSession().getCost() : 0.0);
            if (Math.abs(selectedTotal - 3000.0) > 0.001) {
                throw new AssertionError("Expected selected total 3000.0, got: " + selectedTotal);
            }
            System.out.println("  ✓ Selection model and summation verified.");

            System.out.println("\n==========================================================");
            System.out.println("  ALL VERIFICATION TESTS PASSED SUCCESSFULLY!             ");
            System.out.println("==========================================================");
            DatabaseConfig.shutdown();
            System.exit(0);

        } catch (Throwable t) {
            System.err.println("\n❌ VERIFICATION TEST FAILED: " + t.getMessage());
            t.printStackTrace();
            DatabaseConfig.shutdown();
            System.exit(1);
        }
    }

    private static void assertPdfContent(File pdfFile, String... expectedKeywords) throws Exception {
        try (PDDocument document = Loader.loadPDF(pdfFile)) {
            PDFTextStripper stripper = new PDFTextStripper();
            String text = stripper.getText(document);

            for (String kw : expectedKeywords) {
                if (!text.contains(kw)) {
                    throw new AssertionError("Expected keyword '" + kw + "' not found in generated PDF!\nExtracted text:\n" + text);
                }
            }
        }
    }
}

