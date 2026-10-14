package com.myerp;

import config.DatabaseConfig;
import config.DatabaseInitializer;
import dao.PatientDAO;
import dao.PaymentDAO;
import dao.SessionDAO;
import model.Patient;
import model.Session;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.time.LocalDate;

public class DashboardReceivablesVerificationTest {

    public static void main(String[] args) {
        System.out.println("================================================================");
        System.out.println("  RUNNING DASHBOARD RECEIVABLES (RESTE A PERCEVOIR) VERIFICATION");
        System.out.println("================================================================");

        try {
            DatabaseInitializer.initializeDatabase();

            PatientDAO patientDAO = new PatientDAO();
            SessionDAO sessionDAO = new SessionDAO();
            String today = LocalDate.now().toString();

            double initialDebt = patientDAO.getTotalOutstandingDebt();
            System.out.printf("Initial baseline total outstanding debt: %.2f DZD%n", initialDebt);
            if (initialDebt < 0) {
                throw new AssertionError("Outstanding debt must never be negative!");
            }

            // 1. Create Patient A with an unpaid session (debt)
            System.out.println("\n[Test 1] Creating Patient A with 2000 DA unpaid session debt...");
            Patient patientA = new Patient();
            patientA.setName("Debtor Patient A " + System.currentTimeMillis());
            patientA.setPhone("0550111222");
            patientA.setGender("Homme");
            patientA.setTotalCost(0);
            patientA.setTotalPaid(0);
            if (!patientDAO.addPatient(patientA)) throw new AssertionError("Failed to add Patient A");

            Patient dbPatientA = patientDAO.getAllPatients().stream()
                    .filter(p -> p.getName().equals(patientA.getName()))
                    .findFirst().orElseThrow();
            int patientAId = dbPatientA.getId();

            Session sessionA = new Session();
            sessionA.setPatientId(patientAId);
            sessionA.setDate(today);
            sessionA.setTreatment("Test Session A");
            sessionA.setCost(2000.0);
            sessionA.setPaidAmount(0.0);
            sessionA.setPaied("NONE");
            if (!sessionDAO.addSession(sessionA, 0.0, "admin")) throw new AssertionError("Failed to add session A");

            double debtAfterA = patientDAO.getTotalOutstandingDebt();
            System.out.printf("  Total debt after Patient A unpaid session: %.2f DZD (expected %.2f)%n",
                    debtAfterA, initialDebt + 2000.0);
            if (Math.abs(debtAfterA - (initialDebt + 2000.0)) > 0.01) {
                throw new AssertionError("Expected debt to increase by 2000.0, got: " + debtAfterA);
            }
            System.out.println("  ✓ Outstanding debt increased correctly by 2000 DA.");

            // 2. Create Patient B with advance credit (paid 5000 for 1000 session = 4000 advance)
            System.out.println("\n[Test 2] Creating Patient B with 4000 DA advance credit...");
            Patient patientB = new Patient();
            patientB.setName("Credit Patient B " + System.currentTimeMillis());
            patientB.setPhone("0550333444");
            patientB.setGender("Femme");
            patientB.setTotalCost(0);
            patientB.setTotalPaid(0);
            if (!patientDAO.addPatient(patientB)) throw new AssertionError("Failed to add Patient B");

            Patient dbPatientB = patientDAO.getAllPatients().stream()
                    .filter(p -> p.getName().equals(patientB.getName()))
                    .findFirst().orElseThrow();
            int patientBId = dbPatientB.getId();

            Session sessionB = new Session();
            sessionB.setPatientId(patientBId);
            sessionB.setDate(today);
            sessionB.setTreatment("Test Session B");
            sessionB.setCost(1000.0);
            sessionB.setPaidAmount(1000.0);
            sessionB.setPaied("FULL");
            if (!sessionDAO.addSession(sessionB, 5000.0, "admin")) throw new AssertionError("Failed to add session B with advance");

            double debtAfterB = patientDAO.getTotalOutstandingDebt();
            System.out.printf("  Total debt after Patient B advance: %.2f DZD%n", debtAfterB);
            // Crucial assertion: Patient B's advance credit MUST NOT artificially reduce Patient A's debt!
            if (Math.abs(debtAfterB - (initialDebt + 2000.0)) > 0.01) {
                throw new AssertionError("Patient B's credit erroneously reduced total outstanding receivables!");
            }
            System.out.println("  ✓ Patient B's advance credit did not corrupt Patient A's outstanding debt.");

            // 3. Settle Patient A's debt via payment
            System.out.println("\n[Test 3] Settling Patient A's debt with 2000 DA payment...");
            model.Payment settlement = new model.Payment();
            settlement.setPatientId(patientAId);
            settlement.setPaymentDate(today);
            settlement.setAmount(2000.0);
            settlement.setPaymentType(model.Payment.TYPE_SETTLEMENT);
            settlement.setReason("Règlement dette patient A");
            settlement.setCreatedBy("admin");
            new PaymentDAO().recordPayment(settlement);

            double debtAfterSettlement = patientDAO.getTotalOutstandingDebt();
            System.out.printf("  Total debt after settlement: %.2f DZD (expected %.2f)%n",
                    debtAfterSettlement, initialDebt);
            if (Math.abs(debtAfterSettlement - initialDebt) > 0.01) {
                throw new AssertionError("Expected debt to return to baseline " + initialDebt + ", got: " + debtAfterSettlement);
            }
            System.out.println("  ✓ Outstanding debt decreased back to baseline after settlement.");

            // 4. Cleanup
            System.out.println("\n[Test 4] Cleaning up test data...");
            try (Connection conn = DatabaseConfig.getConnection();
                 PreparedStatement delPayments = conn.prepareStatement("DELETE FROM payment WHERE patientId IN (?, ?)");
                 PreparedStatement delSessions = conn.prepareStatement("DELETE FROM session WHERE patientId IN (?, ?)");
                 PreparedStatement delPatients = conn.prepareStatement("DELETE FROM patient WHERE id IN (?, ?)")) {
                delPayments.setInt(1, patientAId);
                delPayments.setInt(2, patientBId);
                delPayments.executeUpdate();

                delSessions.setInt(1, patientAId);
                delSessions.setInt(2, patientBId);
                delSessions.executeUpdate();

                delPatients.setInt(1, patientAId);
                delPatients.setInt(2, patientBId);
                delPatients.executeUpdate();
            }
            System.out.println("  ✓ Cleaned up test patients.");

            System.out.println("\n================================================================");
            System.out.println("  DASHBOARD RECEIVABLES VERIFICATION PASSED SUCCESSFULLY!       ");
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
