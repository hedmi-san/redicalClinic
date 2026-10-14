package com.myerp;

import config.DatabaseConfig;
import config.DatabaseInitializer;
import controller.SessionFormController;
import dao.PatientDAO;
import dao.SessionDAO;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.control.TextField;
import model.Patient;
import model.Session;

import java.lang.reflect.Field;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.time.LocalDate;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public class SessionFormEditVerificationTest {

    public static void main(String[] args) {
        System.out.println("================================================================");
        System.out.println("  RUNNING SESSION FORM EDIT (PAID AMOUNT = 0) VERIFICATION      ");
        System.out.println("================================================================");

        try {
            // Start JavaFX runtime
            CountDownLatch fxLatch = new CountDownLatch(1);
            try {
                Platform.startup(fxLatch::countDown);
            } catch (IllegalStateException e) {
                // Toolkit already initialized
                fxLatch.countDown();
            }
            if (!fxLatch.await(5, TimeUnit.SECONDS)) {
                throw new AssertionError("Failed to initialize JavaFX toolkit");
            }

            DatabaseInitializer.initializeDatabase();

            PatientDAO patientDAO = new PatientDAO();
            SessionDAO sessionDAO = new SessionDAO();
            String today = LocalDate.now().toString();

            // 1. Create a test patient with 0 debt/credit
            System.out.println("\n[Step 1] Creating test patient...");
            Patient testPatient = new Patient();
            testPatient.setName("Test Session Edit " + System.currentTimeMillis());
            testPatient.setPhone("0550999888");
            testPatient.setGender("Homme");
            testPatient.setTotalCost(0);
            testPatient.setTotalPaid(0);

            if (!patientDAO.addPatient(testPatient)) {
                throw new AssertionError("Failed to insert test patient");
            }
            Patient patient = patientDAO.getAllPatients().stream()
                    .filter(p -> p.getName().equals(testPatient.getName()))
                    .findFirst()
                    .orElseThrow(() -> new AssertionError("Could not find created patient"));
            int patientId = patient.getId();
            System.out.println("  ✓ Patient created with ID: " + patientId + ", balance: " + patient.getBalance());

            // 2. Add a session with cost = 4000, cash received = 0 (unpaid)
            System.out.println("\n[Step 2] Adding session: Cost = 4000 DA, Paid = 0 DA...");
            Session session = new Session();
            session.setPatientId(patientId);
            session.setDate(today);
            session.setTreatment("ACL");
            session.setCost(4000.0);
            session.setPaidAmount(0.0);
            session.setPaied("NONE");

            boolean addOk = sessionDAO.addSession(session, 0.0, "admin");
            if (!addOk) throw new AssertionError("Failed to add session");
            System.out.println("  ✓ Session created with ID: " + session.getId());

            Patient pAfterAdd = patientDAO.getPatientById(patientId);
            System.out.printf("  Patient balance after unpaid session: TotalCost=%.2f, TotalPaid=%.2f, Balance=%.2f%n",
                    pAfterAdd.getTotalCost(), pAfterAdd.getTotalPaid(), pAfterAdd.getBalance());
            if (Math.abs(pAfterAdd.getBalance() - 4000.0) > 0.01) {
                throw new AssertionError("Expected patient balance to be 4000.0 due, got: " + pAfterAdd.getBalance());
            }

            // 3. Load SessionFormController and call setSession(session, patientId)
            System.out.println("\n[Step 3] Loading SessionFormController in edit mode for this session...");
            CountDownLatch uiLatch = new CountDownLatch(1);
            Throwable[] uiError = new Throwable[1];
            String[] paidText = new String[1];
            String[] costText = new String[1];

            Platform.runLater(() -> {
                try {
                    FXMLLoader loader = new FXMLLoader(SessionFormEditVerificationTest.class.getResource("/fxml/pages/session_form.fxml"));
                    Parent root = loader.load();
                    SessionFormController controller = loader.getController();

                    // Set session in edit mode
                    controller.setSession(session, patientId);

                    // Read fields via reflection or helper
                    Field paidField = SessionFormController.class.getDeclaredField("paidAmountField");
                    paidField.setAccessible(true);
                    TextField tfPaid = (TextField) paidField.get(controller);

                    Field costFieldRef = SessionFormController.class.getDeclaredField("costField");
                    costFieldRef.setAccessible(true);
                    TextField tfCost = (TextField) costFieldRef.get(controller);

                    paidText[0] = tfPaid.getText();
                    costText[0] = tfCost.getText();

                    double cash = controller.getCashReceived();
                    if (Math.abs(cash - 0.0) > 0.001) {
                        throw new AssertionError("controller.getCashReceived() returned " + cash + ", expected 0.0!");
                    }

                } catch (Throwable t) {
                    uiError[0] = t;
                } finally {
                    uiLatch.countDown();
                }
            });

            if (!uiLatch.await(5, TimeUnit.SECONDS)) {
                throw new AssertionError("UI operation timed out");
            }
            if (uiError[0] != null) {
                throw new AssertionError("UI error: " + uiError[0].getMessage(), uiError[0]);
            }

            System.out.println("  Cost field text: '" + costText[0] + "' (expected 4 000,00)");
            System.out.println("  Paid field text: '" + paidText[0] + "' (expected 0,00)");

            double parsedPaid = SessionFormController.parseAmount(paidText[0]);
            if (Math.abs(parsedPaid - 0.0) > 0.001) {
                throw new AssertionError("BUG DETECTED: paidAmountField was filled with " + parsedPaid + " instead of 0,00!");
            }
            System.out.println("  ✓ SUCCESS: paidAmountField correctly remained 0,00 in modify form!");

            // 4. Clean up test data
            System.out.println("\n[Step 4] Cleaning up test data...");
            try (Connection conn = DatabaseConfig.getConnection();
                 PreparedStatement delPayments = conn.prepareStatement("DELETE FROM payment WHERE patientId = ?");
                 PreparedStatement delSessions = conn.prepareStatement("DELETE FROM session WHERE patientId = ?");
                 PreparedStatement delPatient = conn.prepareStatement("DELETE FROM patient WHERE id = ?")) {
                delPayments.setInt(1, patientId);
                delPayments.executeUpdate();
                delSessions.setInt(1, patientId);
                delSessions.executeUpdate();
                delPatient.setInt(1, patientId);
                delPatient.executeUpdate();
            }
            System.out.println("  ✓ Cleaned up test patient & session");

            System.out.println("\n================================================================");
            System.out.println("  VERIFICATION TEST PASSED! SESSION EDIT PAID AMOUNT IS 0,00    ");
            System.out.println("================================================================");
            config.DatabaseConfig.shutdown();
            System.exit(0);

        } catch (Throwable t) {
            System.err.println("\n❌ VERIFICATION TEST FAILED: " + t.getMessage());
            t.printStackTrace();
            config.DatabaseConfig.shutdown();
            System.exit(1);
        }
    }
}
