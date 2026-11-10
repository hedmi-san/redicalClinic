package com.myerp;

import config.DatabaseConfig;
import config.DatabaseInitializer;
import dao.PatientDAO;
import model.BalanceFilter;
import model.Patient;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.util.List;

public class PatientBalanceFilterVerificationTest {

    public static void main(String[] args) {
        System.out.println("==============================================================");
        System.out.println("  RUNNING PATIENT BALANCE FILTER VERIFICATION TEST            ");
        System.out.println("==============================================================");

        try {
            DatabaseInitializer.initializeDatabase();
            PatientDAO patientDAO = new PatientDAO();

            String suffix = String.valueOf(System.currentTimeMillis());

            // 1. Create Test Patients with distinct balance profiles
            System.out.println("\n[Test 1] Setting up test patients with debt, advance, and settled balances...");

            Patient debtor = new Patient();
            debtor.setName("Test Debtor " + suffix);
            debtor.setPhone("0550 11 22 33");
            debtor.setGender("Homme");
            debtor.setTotalCost(3000.0);
            debtor.setTotalPaid(1000.0); // balance = +2000.0 (in debt)
            if (!patientDAO.addPatient(debtor)) throw new AssertionError("Failed to add debtor patient");
            forceUpdatePatientTotals(debtor.getId(), 3000.0, 1000.0);

            Patient advance = new Patient();
            advance.setName("Test Advance " + suffix);
            advance.setPhone("0660 44 55 66");
            advance.setGender("Femme");
            advance.setTotalCost(1500.0);
            advance.setTotalPaid(4000.0); // balance = -2500.0 (in advance / credit)
            if (!patientDAO.addPatient(advance)) throw new AssertionError("Failed to add advance patient");
            forceUpdatePatientTotals(advance.getId(), 1500.0, 4000.0);

            Patient settled = new Patient();
            settled.setName("Test Settled " + suffix);
            settled.setPhone("0770 77 88 99");
            settled.setGender("Homme");
            settled.setTotalCost(2000.0);
            settled.setTotalPaid(2000.0); // balance = 0.0 (settled)
            if (!patientDAO.addPatient(settled)) throw new AssertionError("Failed to add settled patient");
            forceUpdatePatientTotals(settled.getId(), 2000.0, 2000.0);

            System.out.println("  ✓ 3 test patients created successfully.");

            // 2. Test BalanceFilter.ALL
            System.out.println("\n[Test 2] Testing BalanceFilter.ALL...");
            List<Patient> allPatients = patientDAO.getPatients(suffix, BalanceFilter.ALL);
            if (allPatients.size() != 3) {
                throw new AssertionError("Expected 3 patients for suffix '" + suffix + "', got: " + allPatients.size());
            }
            System.out.println("  ✓ BalanceFilter.ALL returned all 3 test patients.");

            // 3. Test BalanceFilter.DEBT (balance > 0.01)
            System.out.println("\n[Test 3] Testing BalanceFilter.DEBT...");
            List<Patient> debtPatients = patientDAO.getPatients(suffix, BalanceFilter.DEBT);
            if (debtPatients.size() != 1) {
                throw new AssertionError("Expected 1 debt patient, got: " + debtPatients.size());
            }
            if (!debtPatients.get(0).getName().equals(debtor.getName())) {
                throw new AssertionError("Expected debtor '" + debtor.getName() + "', got: " + debtPatients.get(0).getName());
            }
            if (debtPatients.get(0).getBalance() <= 0.01) {
                throw new AssertionError("Debtor balance must be > 0.01, got: " + debtPatients.get(0).getBalance());
            }
            System.out.println("  ✓ BalanceFilter.DEBT returned only the debtor patient (Balance = " + debtPatients.get(0).getBalance() + " DZD).");

            // 4. Test BalanceFilter.ADVANCE (balance < -0.01)
            System.out.println("\n[Test 4] Testing BalanceFilter.ADVANCE...");
            List<Patient> advancePatients = patientDAO.getPatients(suffix, BalanceFilter.ADVANCE);
            if (advancePatients.size() != 1) {
                throw new AssertionError("Expected 1 advance patient, got: " + advancePatients.size());
            }
            if (!advancePatients.get(0).getName().equals(advance.getName())) {
                throw new AssertionError("Expected advance patient '" + advance.getName() + "', got: " + advancePatients.get(0).getName());
            }
            if (advancePatients.get(0).getBalance() >= -0.01) {
                throw new AssertionError("Advance balance must be < -0.01, got: " + advancePatients.get(0).getBalance());
            }
            System.out.println("  ✓ BalanceFilter.ADVANCE returned only the advance patient (Balance = " + advancePatients.get(0).getBalance() + " DZD).");

            // 5. Test BalanceFilter.SETTLED (ABS(balance) <= 0.01)
            System.out.println("\n[Test 5] Testing BalanceFilter.SETTLED...");
            List<Patient> settledPatients = patientDAO.getPatients(suffix, BalanceFilter.SETTLED);
            if (settledPatients.size() != 1) {
                throw new AssertionError("Expected 1 settled patient, got: " + settledPatients.size());
            }
            if (!settledPatients.get(0).getName().equals(settled.getName())) {
                throw new AssertionError("Expected settled patient '" + settled.getName() + "', got: " + settledPatients.get(0).getName());
            }
            if (Math.abs(settledPatients.get(0).getBalance()) > 0.01) {
                throw new AssertionError("Settled balance must be ~0, got: " + settledPatients.get(0).getBalance());
            }
            System.out.println("  ✓ BalanceFilter.SETTLED returned only the settled patient (Balance = " + settledPatients.get(0).getBalance() + " DZD).");

            // 6. Test Multi-Criteria: Text Search + Balance Filter
            System.out.println("\n[Test 6] Testing combined text search and balance filter...");
            List<Patient> combinedMatch = patientDAO.getPatients("Debtor " + suffix, BalanceFilter.DEBT);
            if (combinedMatch.size() != 1 || !combinedMatch.get(0).getName().equals(debtor.getName())) {
                throw new AssertionError("Expected 1 match for debtor with DEBT filter, got: " + combinedMatch.size());
            }
            List<Patient> combinedMismatch = patientDAO.getPatients("Debtor " + suffix, BalanceFilter.ADVANCE);
            if (!combinedMismatch.isEmpty()) {
                throw new AssertionError("Expected 0 matches for debtor with ADVANCE filter, got: " + combinedMismatch.size());
            }
            System.out.println("  ✓ Combined text search and balance filtering work concurrently.");

            // 7. Test Space-Insensitive Phone Search
            System.out.println("\n[Test 7] Testing space-insensitive phone search...");
            // Stored phone: "0550 11 22 33", search with: "0550112233"
            List<Patient> phoneMatch = patientDAO.getPatients("0550112233", BalanceFilter.DEBT);
            if (phoneMatch.size() != 1 || !phoneMatch.get(0).getName().equals(debtor.getName())) {
                throw new AssertionError("Expected phone search '0550112233' to match '0550 11 22 33', got: " + phoneMatch.size());
            }
            System.out.println("  ✓ Space-insensitive phone search matched successfully.");

            // 8. Test BalanceFilter Enum helper logic
            System.out.println("\n[Test 8] Testing BalanceFilter enum logic & parsing...");
            if (!BalanceFilter.DEBT.matches(100.0) || BalanceFilter.DEBT.matches(-50.0) || BalanceFilter.DEBT.matches(0.0)) {
                throw new AssertionError("BalanceFilter.DEBT match logic failure");
            }
            if (!BalanceFilter.ADVANCE.matches(-100.0) || BalanceFilter.ADVANCE.matches(50.0) || BalanceFilter.ADVANCE.matches(0.0)) {
                throw new AssertionError("BalanceFilter.ADVANCE match logic failure");
            }
            if (!BalanceFilter.SETTLED.matches(0.0) || BalanceFilter.SETTLED.matches(10.0) || BalanceFilter.SETTLED.matches(-10.0)) {
                throw new AssertionError("BalanceFilter.SETTLED match logic failure");
            }
            if (BalanceFilter.fromDisplayName("En dette") != BalanceFilter.DEBT ||
                BalanceFilter.fromDisplayName("in debt") != BalanceFilter.DEBT ||
                BalanceFilter.fromDisplayName("En avance") != BalanceFilter.ADVANCE ||
                BalanceFilter.fromDisplayName("in advance") != BalanceFilter.ADVANCE ||
                BalanceFilter.fromDisplayName("réglée") != BalanceFilter.SETTLED ||
                BalanceFilter.fromDisplayName("riglée") != BalanceFilter.SETTLED ||
                BalanceFilter.fromDisplayName("Soldé") != BalanceFilter.SETTLED ||
                BalanceFilter.fromDisplayName("Tous les soldes") != BalanceFilter.ALL) {
                throw new AssertionError("BalanceFilter.fromDisplayName parsing failure");
            }
            System.out.println("  ✓ BalanceFilter enum parsing and match logic verified.");

            // 9. Cleanup test records
            System.out.println("\n[Test 9] Cleaning up test records...");
            patientDAO.deletePatient(debtor.getId());
            patientDAO.deletePatient(advance.getId());
            patientDAO.deletePatient(settled.getId());
            System.out.println("  ✓ Test records cleaned up successfully.");

            System.out.println("\n==============================================================");
            System.out.println("  ALL PATIENT BALANCE FILTER VERIFICATION TESTS PASSED!       ");
            System.out.println("==============================================================");

        } catch (Throwable t) {
            System.err.println("\n❌ TEST FAILED: " + t.getMessage());
            t.printStackTrace();
            System.exit(1);
        }
    }

    private static void forceUpdatePatientTotals(int patientId, double totalCost, double totalPaid) throws Exception {
        String sql = "UPDATE patient SET totalCost = ?, totalPaid = ? WHERE id = ?";
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setDouble(1, totalCost);
            pstmt.setDouble(2, totalPaid);
            pstmt.setInt(3, patientId);
            pstmt.executeUpdate();
        }
    }
}

