package com.myerp;

import config.DatabaseConfig;
import config.DatabaseInitializer;
import dao.PatientDAO;
import dao.PaymentDAO;
import dao.SessionDAO;
import dao.TherapyPlanDAO;
import model.Patient;
import model.Payment;
import model.Session;
import model.TherapyPlan;
import controller.SessionFormController;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.LocalDate;
import java.util.List;

public class PaymentLedgerVerificationTest {

    public static void main(String[] args) {
        System.out.println("================================================================");
        System.out.println("  RUNNING PATIENT PAYMENT LEDGER & CASH REGISTER VERIFICATION   ");
        System.out.println("================================================================");

        try {
            // 1. Initialize Database & run migrations
            System.out.println("\n[Test 1] Testing Database Initializer & Migration Idempotency...");
            DatabaseInitializer.initializeDatabase();
            // Re-run to ensure idempotency
            DatabaseInitializer.initializeDatabase();
            System.out.println("  ✓ Database initialized and migration executed idempotently.");

            PatientDAO patientDAO = new PatientDAO();
            SessionDAO sessionDAO = new SessionDAO();
            PaymentDAO paymentDAO = new PaymentDAO();
            String today = LocalDate.now().toString();

            // 2. Create a test patient
            System.out.println("\n[Test 2] Creating Test Patient...");
            Patient testPatient = new Patient();
            testPatient.setName("Test Ledger Patient " + System.currentTimeMillis());
            testPatient.setPhone("0550123456");
            testPatient.setGender("Homme");
            testPatient.setTotalCost(0);
            testPatient.setTotalPaid(0);

            if (!patientDAO.addPatient(testPatient)) {
                throw new AssertionError("Failed to create test patient!");
            }

            // Retrieve created patient
            List<Patient> patients = patientDAO.getAllPatients();
            Patient patient = patients.stream()
                    .filter(p -> p.getName().equals(testPatient.getName()))
                    .findFirst()
                    .orElseThrow(() -> new AssertionError("Could not find created patient!"));
            int patientId = patient.getId();
            System.out.println("  ✓ Test Patient created with ID: " + patientId);

            // 3. User Motivating Problem: Session 1 costs 500 DA, patient pays 2000 DA cash
            System.out.println("\n[Test 3] Session 1: Cost 500 DA, Cash Paid 2000 DA (1500 DA Advance)...");
            Session session1 = new Session();
            session1.setPatientId(patientId);
            session1.setDate(today);
            session1.setTreatment("Séance Rééducation 1");
            session1.setCost(500.0);
            session1.setPaidAmount(500.0); // Cost portion considered covered
            session1.setPaied("FULL");

            boolean s1Ok = sessionDAO.addSession(session1, 2000.0, "test_admin");
            if (!s1Ok) throw new AssertionError("Failed to save session 1 with 2000 DA cash!");

            Patient pAfterS1 = patientDAO.getPatientById(patientId);
            System.out.printf("  After S1: TotalCost=%.2f, TotalPaid=%.2f, Balance=%.2f%n",
                    pAfterS1.getTotalCost(), pAfterS1.getTotalPaid(), pAfterS1.getBalance());

            if (Math.abs(pAfterS1.getTotalCost() - 500.0) > 0.01) {
                throw new AssertionError("Expected totalCost 500.0, got: " + pAfterS1.getTotalCost());
            }
            if (Math.abs(pAfterS1.getTotalPaid() - 2000.0) > 0.01) {
                throw new AssertionError("Expected totalPaid 2000.0, got: " + pAfterS1.getTotalPaid());
            }
            // Balance = cost - paid = 500 - 2000 = -1500 (Advance credit)
            if (Math.abs(pAfterS1.getBalance() - (-1500.0)) > 0.01) {
                throw new AssertionError("Expected balance -1500.0, got: " + pAfterS1.getBalance());
            }
            System.out.println("  ✓ S1 correctly created 1500 DA advance credit without distorting ledger.");

            // 4. Session 2: Cost 500 DA, Cash Paid 0 DA (Covered automatically by advance)
            System.out.println("\n[Test 4] Session 2: Cost 500 DA, Cash Paid 0 DA (Covered by Advance)...");
            Session session2 = new Session();
            session2.setPatientId(patientId);
            session2.setDate(today);
            session2.setTreatment("Séance Rééducation 2");
            session2.setCost(500.0);
            session2.setPaidAmount(500.0);
            session2.setPaied("FULL");

            boolean s2Ok = sessionDAO.addSession(session2, 0.0, "test_admin");
            if (!s2Ok) throw new AssertionError("Failed to save session 2!");

            Patient pAfterS2 = patientDAO.getPatientById(patientId);
            System.out.printf("  After S2: TotalCost=%.2f, TotalPaid=%.2f, Balance=%.2f%n",
                    pAfterS2.getTotalCost(), pAfterS2.getTotalPaid(), pAfterS2.getBalance());

            if (Math.abs(pAfterS2.getTotalCost() - 1000.0) > 0.01) {
                throw new AssertionError("Expected totalCost 1000.0, got: " + pAfterS2.getTotalCost());
            }
            if (Math.abs(pAfterS2.getTotalPaid() - 2000.0) > 0.01) {
                throw new AssertionError("Expected totalPaid 2000.0 (no phantom cash entered), got: " + pAfterS2.getTotalPaid());
            }
            // Balance = 1000 - 2000 = -1000 DA advance remaining
            if (Math.abs(pAfterS2.getBalance() - (-1000.0)) > 0.01) {
                throw new AssertionError("Expected balance -1000.0, got: " + pAfterS2.getBalance());
            }
            System.out.println("  ✓ S2 consumed 500 DA from advance credit automatically; remaining advance is 1000 DA.");

            // 5. Standalone Deposit (DEPOSIT)
            System.out.println("\n[Test 5] Standalone Free Deposit of 3000 DA...");
            Payment deposit = new Payment();
            deposit.setPatientId(patientId);
            deposit.setPaymentDate(today);
            deposit.setAmount(3000.0);
            deposit.setPaymentType(Payment.TYPE_DEPOSIT);
            deposit.setReason("Versement libre en avance");
            deposit.setCreatedBy("test_admin");

            if (!paymentDAO.recordPayment(deposit)) {
                throw new AssertionError("Failed to record standalone deposit!");
            }
            patientDAO.updatePatientTotals(patientId);

            Patient pAfterDeposit = patientDAO.getPatientById(patientId);
            System.out.printf("  After Deposit: TotalCost=%.2f, TotalPaid=%.2f, Balance=%.2f%n",
                    pAfterDeposit.getTotalCost(), pAfterDeposit.getTotalPaid(), pAfterDeposit.getBalance());

            // Total paid should be 2000 + 3000 = 5000 DA, balance = 1000 - 5000 = -4000 DA
            if (Math.abs(pAfterDeposit.getTotalPaid() - 5000.0) > 0.01) {
                throw new AssertionError("Expected totalPaid 5000.0, got: " + pAfterDeposit.getTotalPaid());
            }
            if (Math.abs(pAfterDeposit.getBalance() - (-4000.0)) > 0.01) {
                throw new AssertionError("Expected balance -4000.0, got: " + pAfterDeposit.getBalance());
            }
            System.out.println("  ✓ Standalone deposit verified: credit balance is now 4000 DA.");

            // 6. Cash Refund of Available Credit
            System.out.println("\n[Test 6] Refunding 1500 DA of Patient Credit...");
            boolean refundOk = paymentDAO.refundCredit(patientId, 1500.0, "Remboursement avance demandé par patient", "test_admin");
            if (!refundOk) {
                throw new AssertionError("Failed to process refund of 1500 DA!");
            }
            patientDAO.updatePatientTotals(patientId);

            Patient pAfterRefund = patientDAO.getPatientById(patientId);
            System.out.printf("  After Refund: TotalCost=%.2f, TotalPaid=%.2f, Balance=%.2f%n",
                    pAfterRefund.getTotalCost(), pAfterRefund.getTotalPaid(), pAfterRefund.getBalance());

            // Total paid should now be 5000 - 1500 = 3500 DA, balance = 1000 - 3500 = -2500 DA
            if (Math.abs(pAfterRefund.getTotalPaid() - 3500.0) > 0.01) {
                throw new AssertionError("Expected totalPaid 3500.0 after refund, got: " + pAfterRefund.getTotalPaid());
            }
            if (Math.abs(pAfterRefund.getBalance() - (-2500.0)) > 0.01) {
                throw new AssertionError("Expected balance -2500.0 after refund, got: " + pAfterRefund.getBalance());
            }
            System.out.println("  ✓ Refund successfully recorded as negative payment (-1500 DA) and deducted from patient credit.");

            // 7. Payment Reversal and Constraint Check
            System.out.println("\n[Test 7] Payment Reversal & Idempotency / Single Reversal Constraint...");
            // Create a small erroneous payment to reverse
            Payment typoPayment = new Payment();
            typoPayment.setPatientId(patientId);
            typoPayment.setPaymentDate(today);
            typoPayment.setAmount(600.0);
            typoPayment.setPaymentType(Payment.TYPE_DEPOSIT);
            typoPayment.setReason("Versement erroné");
            typoPayment.setCreatedBy("test_admin");
            paymentDAO.recordPayment(typoPayment);

            List<Payment> patientPayments = paymentDAO.getPaymentsByPatientId(patientId);
            Payment lastPayment = patientPayments.stream()
                    .filter(p -> "Versement erroné".equals(p.getReason()))
                    .findFirst()
                    .orElseThrow(() -> new AssertionError("Could not find typo payment!"));

            boolean rev1Ok = paymentDAO.reversePayment(lastPayment.getId(), "Annulation erreur de saisie", "test_admin");
            if (!rev1Ok) {
                throw new AssertionError("First reversal should succeed!");
            }
            System.out.println("  ✓ Payment #" + lastPayment.getId() + " reversed successfully.");

            // Attempting second reversal must fail due to uq_reversal constraint
            boolean rev2Ok = false;
            try {
                rev2Ok = paymentDAO.reversePayment(lastPayment.getId(), "Deuxième tentative", "test_admin");
            } catch (Exception e) {
                // Expected to fail!
                System.out.println("  ✓ Expected error caught on second reversal: " + e.getMessage());
                rev2Ok = false;
            }
            if (rev2Ok) {
                throw new AssertionError("Second reversal should have failed due to uq_reversal constraint!");
            }
            System.out.println("  ✓ Second reversal correctly blocked by database constraint / logic.");

            // 8. Daily Cash Register Breakdown
            System.out.println("\n[Test 8] Daily Cash Register Verification for " + today + "...");
            PaymentDAO.DailyCashRegister register = paymentDAO.getDailyCashRegister(today);
            System.out.printf("  Gross Collected: %.2f DA, Refunds: %.2f DA, Reversals: %.2f DA, Net Cash: %.2f DA%n",
                    register.grossCollected(), register.refunds(), register.reversals(), register.netCash());

            if (register.grossCollected() <= 0) {
                throw new AssertionError("Gross collected should be positive!");
            }
            if (register.refunds() < 1500.0) {
                throw new AssertionError("Refunds should be at least 1500.0!");
            }
            if (register.reversals() < 600.0) {
                throw new AssertionError("Reversals should be at least 600.0!");
            }
            // 9. Therapy Plan Lifecycle & Patient Totals Alignment
            System.out.println("\n[Test 9] Therapy Plan Lifecycle & Balance Alignment...");
            TherapyPlanDAO therapyPlanDAO = new TherapyPlanDAO();
            TherapyPlan testPlan = new TherapyPlan();
            testPlan.setPatientId(patientId);
            testPlan.setDate(today);
            testPlan.setCost(10000.0);
            if (!therapyPlanDAO.addTherapyPlan(testPlan)) {
                throw new AssertionError("Failed to add test therapy plan!");
            }
            patientDAO.updatePatientTotals(patientId);

            Patient pWithPlan = patientDAO.getPatientById(patientId);
            if (Math.abs(pWithPlan.getTotalCost() - 11000.0) > 0.01) {
                throw new AssertionError("Expected totalCost 11000.0 with therapy plan, got: " + pWithPlan.getTotalCost());
            }

            // Deleting the therapy plan must restore cost and update patient totals
            if (!therapyPlanDAO.deleteTherapyPlan(testPlan.getId(), patientId)) {
                throw new AssertionError("Failed to delete test therapy plan!");
            }
            Patient pAfterPlanDeleted = patientDAO.getPatientById(patientId);
            if (Math.abs(pAfterPlanDeleted.getTotalCost() - 1000.0) > 0.01) {
                throw new AssertionError("Expected totalCost restored to 1000.0 after plan deletion, got: " + pAfterPlanDeleted.getTotalCost());
            }
            System.out.println("  ✓ Therapy plan creation and deletion properly synchronizes patient totals.");

            // 10. Clean up test patient data
            System.out.println("\n[Test 10] Cleaning Up Test Data...");
            try (Connection conn = DatabaseConfig.getConnection();
                 PreparedStatement nullReversals = conn.prepareStatement("UPDATE payment SET reversalOf = NULL WHERE patientId = ?");
                 PreparedStatement delPayments = conn.prepareStatement("DELETE FROM payment WHERE patientId = ?");
                 PreparedStatement delPlans = conn.prepareStatement("DELETE FROM therapyPlan WHERE patientId = ?");
                 PreparedStatement delSessions = conn.prepareStatement("DELETE FROM session WHERE patientId = ?");
                 PreparedStatement delPatient = conn.prepareStatement("DELETE FROM patient WHERE id = ?")) {
                nullReversals.setInt(1, patientId);
                nullReversals.executeUpdate();

                delPayments.setInt(1, patientId);
                delPayments.executeUpdate();

                delPlans.setInt(1, patientId);
                delPlans.executeUpdate();

                delSessions.setInt(1, patientId);
                delSessions.executeUpdate();

                delPatient.setInt(1, patientId);
                delPatient.executeUpdate();
            }
            System.out.println("  ✓ Test patient and linked ledger rows successfully cleaned up.");

            // 11. Amount Parsing & Locale Formatting Verification
            System.out.println("\n[Test 11] Amount Parsing Verification for French/Algerian Locale & Currencies...");
            assertAmountParsed("2 500,00", 2500.0);
            assertAmountParsed("2\u202F500,00", 2500.0); // Narrow no-break space from Java 21 NumberFormat
            assertAmountParsed("2\u00A0500,00", 2500.0); // Standard non-breaking space
            assertAmountParsed("2 500,00 DA", 2500.0);
            assertAmountParsed("2\u202F500,00 DA", 2500.0);
            assertAmountParsed("2500,00", 2500.0);
            assertAmountParsed("2500.00", 2500.0);
            assertAmountParsed("2 500", 2500.0);
            assertAmountParsed("2.500,00", 2500.0);
            assertAmountParsed("0,00", 0.0);
            assertAmountParsed("0,00 DA", 0.0);
            assertAmountParsed("1 250 500,50 DA", 1250500.50);
            assertAmountParsed("1\u202F250\u202F500,50 DA", 1250500.50);
            assertAmountParsed("", 0.0);
            assertAmountParsed(null, 0.0);
            System.out.println("  ✓ All amount formatting & unicode space variants parsed accurately.");

            System.out.println("\n================================================================");
            System.out.println("  ALL PATIENT PAYMENT LEDGER VERIFICATIONS PASSED SUCCESSFULLY! ");
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

    private static void assertAmountParsed(String input, double expected) {
        double parsed = SessionFormController.parseAmount(input);
        if (Math.abs(parsed - expected) > 0.001) {
            throw new AssertionError(String.format("parseAmount(\"%s\") returned %.2f, expected %.2f", input, parsed, expected));
        }
    }
}
