package com.myerp;

import config.AppConfig;
import config.DatabaseConfig;
import config.DatabaseInitializer;
import dao.PatientDAO;
import model.BalanceFilter;
import model.Patient;
import util.PatientSearchHelper;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public class PatientSearchVerificationTest {

    public static void main(String[] args) {
        System.out.println("==============================================================");
        System.out.println("  RUNNING ORDER-INDEPENDENT PATIENT SEARCH VERIFICATION TEST  ");
        System.out.println("==============================================================");

        try {
            Path tempHome = Files.createTempDirectory("clinic_patient_search_verify_");
            System.setProperty("user.home", tempHome.toAbsolutePath().toString());
            AppConfig.setNodeRole(AppConfig.ROLE_HOST);
            AppConfig.setDbPort(19096);
            DatabaseInitializer.initializeDatabase();

            PatientDAO patientDAO = new PatientDAO();

            Patient arabicPatient = new Patient();
            arabicPatient.setName("العمري عبد الرحمان");
            arabicPatient.setGender("Male");
            arabicPatient.setPhone("0550 11 22 33");
            if (!patientDAO.addPatient(arabicPatient)) {
                throw new AssertionError("Failed to insert Arabic test patient");
            }

            Patient latinPatient = new Patient();
            latinPatient.setName("Amine Benali");
            latinPatient.setGender("Male");
            latinPatient.setPhone("0661 44 55 66");
            if (!patientDAO.addPatient(latinPatient)) {
                throw new AssertionError("Failed to insert Latin test patient");
            }

            Patient otherArabicPatient = new Patient();
            otherArabicPatient.setName("العمري محمد");
            otherArabicPatient.setGender("Male");
            otherArabicPatient.setPhone("0770 88 99 00");
            if (!patientDAO.addPatient(otherArabicPatient)) {
                throw new AssertionError("Failed to insert second Arabic test patient");
            }

            // 1. DAO search in exact order and reversed order (Arabic)
            assertSingleMatch(patientDAO.searchPatients("العمري عبد الرحمان"), arabicPatient.getId(), "exact Arabic order");
            assertSingleMatch(patientDAO.searchPatients("عبد الرحمان العمري"), arabicPatient.getId(), "reversed Arabic order");
            assertSingleMatch(patientDAO.searchPatients("  رحمان   العمري  "), arabicPatient.getId(), "partial Arabic tokens out of order");
            assertSingleMatch(patientDAO.searchPatients("عبدالرحمان العمري"), arabicPatient.getId(), "space-collapsed compound Arabic token out of order");

            // 2. DAO search in reversed order (Latin, case-insensitive)
            assertSingleMatch(patientDAO.searchPatients("benali amine"), latinPatient.getId(), "reversed Latin case-insensitive");
            assertSingleMatch(patientDAO.searchPatients("BEN ALI"), latinPatient.getId(), "partial Latin tokens");

            // 3. DAO search rejects partial mismatch where one token does not belong
            List<Patient> mismatch = patientDAO.searchPatients("عبد الرحمان محمد");
            if (!mismatch.isEmpty()) {
                throw new AssertionError("Expected 0 matches when a token does not match, got: " + mismatch.size());
            }

            // 4. DAO search by phone (with and without spaces) and combined with balance filter
            assertSingleMatch(patientDAO.getPatients("0550112233", BalanceFilter.SETTLED), arabicPatient.getId(), "space-insensitive phone with SETTLED filter");
            assertSingleMatch(patientDAO.getPatients("0550 11", BalanceFilter.ALL), arabicPatient.getId(), "partial spaced phone");

            // 5. Wildcard literal safety (% and _ should not act as SQL wildcards)
            List<Patient> wildcardQuery = patientDAO.searchPatients("%");
            if (!wildcardQuery.isEmpty()) {
                throw new AssertionError("Expected '%' literal search to match 0 patients, got: " + wildcardQuery.size());
            }

            // 6. In-memory PatientSearchHelper consistency (used by SessionFormController and AppointmentFormController)
            if (!PatientSearchHelper.matches(arabicPatient, "عبد الرحمان العمري")) {
                throw new AssertionError("PatientSearchHelper failed reversed Arabic match");
            }
            if (!PatientSearchHelper.matches(arabicPatient, "عبدالرحمان العمري")) {
                throw new AssertionError("PatientSearchHelper failed space-collapsed Arabic match");
            }
            if (!PatientSearchHelper.matches(arabicPatient, "0550112233")) {
                throw new AssertionError("PatientSearchHelper failed phone match");
            }
            if (PatientSearchHelper.matches(arabicPatient, "عبد الرحمان محمد")) {
                throw new AssertionError("PatientSearchHelper matched when an extra non-matching token was present");
            }
            if (!PatientSearchHelper.matches(latinPatient, "benali amine")) {
                throw new AssertionError("PatientSearchHelper failed reversed Latin match");
            }

            // 7. Highlight ranges for out-of-order tokens
            List<int[]> ranges = PatientSearchHelper.findHighlightRanges("العمري عبد الرحمان", "عبد الرحمان العمري");
            if (ranges.size() != 3) {
                throw new AssertionError("Expected 3 highlight ranges for 3 matched words, got: " + ranges.size());
            }
            String reconstructed =
                    "العمري عبد الرحمان".substring(ranges.get(0)[0], ranges.get(0)[1]) + "|" +
                    "العمري عبد الرحمان".substring(ranges.get(1)[0], ranges.get(1)[1]) + "|" +
                    "العمري عبد الرحمان".substring(ranges.get(2)[0], ranges.get(2)[1]);
            if (!"العمري|عبد|الرحمان".equals(reconstructed)) {
                throw new AssertionError("Unexpected highlight ranges: " + reconstructed);
            }

            System.out.println("  ✓ All DAO, in-memory selector, phone, wildcard, and highlight checks passed.");
            System.out.println("==============================================================");
            System.out.println("  ALL PATIENT SEARCH VERIFICATION TESTS PASSED!               ");
            System.out.println("==============================================================");
        } catch (Throwable t) {
            System.err.println("\n❌ TEST FAILED: " + t.getMessage());
            t.printStackTrace();
            System.exit(1);
        } finally {
            DatabaseConfig.shutdown();
        }
    }

    private static void assertSingleMatch(List<Patient> results, int expectedId, String scenario) {
        if (results.size() != 1 || results.get(0).getId() != expectedId) {
            throw new AssertionError("Scenario [" + scenario + "] expected single patient ID "
                    + expectedId + ", got count=" + results.size());
        }
    }
}
