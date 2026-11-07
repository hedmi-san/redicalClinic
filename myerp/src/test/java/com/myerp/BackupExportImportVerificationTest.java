package com.myerp;

import config.AppConfig;
import config.BackupManager;
import config.DatabaseConfig;
import config.DatabaseInitializer;
import dao.PatientDAO;
import dao.PaymentDAO;
import dao.SessionDAO;
import model.Patient;
import model.Payment;
import model.Session;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * End-to-end verification test for Admin Data Export & Import (.cni, .sql, .db),
 * SHA-256 manifest verification, hot-swap restoration, legacy schema normalization,
 * payment ledger backfill, and client-node restrictions.
 */
public class BackupExportImportVerificationTest {

    public static void main(String[] args) throws Exception {
        // Use a disposable temporary home directory so real ~/clinic data is never touched
        Path tempHome = Files.createTempDirectory("clinic_backup_test_home_");
        System.setProperty("user.home", tempHome.toAbsolutePath().toString());

        System.out.println("===================================================================");
        System.out.println("  RUNNING ADMIN DATA EXPORT & IMPORT (.CNI / .SQL / .DB) TESTS     ");
        System.out.println("  Disposable Test Home: " + tempHome.toAbsolutePath());
        System.out.println("===================================================================");

        try {
            // 1. Configure as HOST and initialize database
            AppConfig.setNodeRole(AppConfig.ROLE_HOST);
            AppConfig.setDbPort(19092); // Use isolated test port
            DatabaseInitializer.initializeDatabase();

            PatientDAO patientDAO = new PatientDAO();
            SessionDAO sessionDAO = new SessionDAO();
            PaymentDAO paymentDAO = new PaymentDAO();

            // Seed initial data in H2 before .cni export
            Patient p1 = new Patient();
            p1.setName("Amine Benali");
            p1.setGender("Homme");
            p1.setPhone("0551112233");
            if (!patientDAO.addPatient(p1)) {
                throw new AssertionError("Failed to add initial patient p1");
            }
            int p1Id = patientDAO.getAllPatients().get(0).getId();

            Session s1 = new Session(0, p1Id, "2026-11-01", "Détartrage complet", 3000.0, 2000.0);
            s1.setPaied("PARTIAL");
            if (!sessionDAO.addSession(s1, 2000.0, "admin")) {
                throw new AssertionError("Failed to add initial session s1");
            }

            // -----------------------------------------------------------------
            // Test 6.2: .cni Export & SHA-256 Manifest Verification
            // -----------------------------------------------------------------
            System.out.println("\n[Test 6.2] Testing .cni Export and Manifest Integrity...");
            File cniFile = tempHome.resolve("test_export.cni").toFile();
            BackupManager.BackupInspection exportInfo = BackupManager.exportToCni(cniFile);

            if (!cniFile.exists() || cniFile.length() == 0) {
                throw new AssertionError(".cni file was not created or is empty");
            }
            if (exportInfo.patientsCount() != 1 || exportInfo.sessionsCount() != 1) {
                throw new AssertionError("Unexpected export counts: patients=" + exportInfo.patientsCount()
                        + ", sessions=" + exportInfo.sessionsCount());
            }

            BackupManager.BackupInspection inspectedCni = BackupManager.inspectBackupFile(cniFile);
            if (!"CNI".equals(inspectedCni.format()) || !exportInfo.sha256().equalsIgnoreCase(inspectedCni.sha256())) {
                throw new AssertionError("Inspected .cni SHA-256 or format mismatch");
            }
            System.out.println("  ✓ .cni exported and verified (SHA-256: " + inspectedCni.sha256().substring(0, 16) + "...)");

            // -----------------------------------------------------------------
            // Test 6.4a: Legacy .sql Dump Clean-Replace Import (7-column session & 4-column Users)
            // -----------------------------------------------------------------
            System.out.println("\n[Test 6.4a] Testing Legacy .sql Dump Import (7-col session, 4-col Users, Ledger Backfill)...");
            File legacySqlFile = tempHome.resolve("legacy_dump.sql").toFile();
            String sqlContent = """
                    -- Legacy SQLite Dump from old version
                    PRAGMA foreign_keys=OFF;
                    BEGIN TRANSACTION;
                    CREATE TABLE Users (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        fullName TEXT NOT NULL,
                        userName TEXT NOT NULL,
                        passWord TEXT NOT NULL
                    );
                    INSERT INTO Users VALUES (1, 'Dr. Nour', 'drnour', 'secret123');
                    CREATE TABLE patient (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        name VARCHAR(25) NOT NULL,
                        gender VARCHAR(10),
                        phone VARCHAR(10),
                        totalCost REAL,
                        totalPaid REAL
                    );
                    INSERT INTO patient VALUES (10, 'Karim Mansouri', 'Homme', '0661223344', 0, 0);
                    INSERT INTO patient VALUES (20, 'Sara Touati', 'Femme', '0771998877', 0, 0);
                    CREATE TABLE session (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        patientId INTEGER REFERENCES patient(id) ON DELETE CASADE,
                        sessionDate TEXT DEFAULT (datetime('now')),
                        treatment TEXT,
                        paied VARCHAR(10),
                        cost REAL,
                        paidAmount REAL
                    );
                    INSERT INTO session VALUES (100, 10, '2025-05-10', 'Soins caries', 'PARTIAL', 4500.0, 1500.0);
                    INSERT INTO session VALUES (101, 20, '2025-05-11', 'Extraction', 'FULL', 2500.0, 2500.0);
                    CREATE TABLE bill (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        billDate TEXT,
                        totalCost REAL
                    );
                    INSERT INTO bill VALUES (5, '2025-05-10', 8000.0);
                    DELETE FROM sqlite_sequence;
                    COMMIT;
                    """;
            Files.writeString(legacySqlFile.toPath(), sqlContent, StandardCharsets.UTF_8);

            BackupManager.BackupInspection sqlInspection = BackupManager.inspectBackupFile(legacySqlFile);
            if (!"SQL".equals(sqlInspection.format())
                    || sqlInspection.patientsCount() != 2
                    || sqlInspection.sessionsCount() != 2
                    || sqlInspection.billsCount() != 1
                    || sqlInspection.usersCount() != 1) {
                throw new AssertionError("Unexpected SQL inspection counts: " + sqlInspection);
            }

            BackupManager.restoreOrImportDatabase(legacySqlFile);

            // Verify clean-replace occurred and payment ledger was backfilled
            List<Patient> importedPatients = patientDAO.getAllPatients();
            if (importedPatients.size() != 2) {
                throw new AssertionError("Expected 2 patients after SQL import, found: " + importedPatients.size());
            }

            Patient karim = patientDAO.getPatientById(10);
            if (karim == null || Math.abs(karim.getTotalCost() - 4500.0) > 0.01 || Math.abs(karim.getTotalPaid() - 1500.0) > 0.01) {
                throw new AssertionError("Karim totals were not recalculated properly: "
                        + (karim != null ? karim.getTotalCost() + " / " + karim.getTotalPaid() : "null"));
            }
            if (Math.abs(karim.getBalance() - 3000.0) > 0.01) {
                throw new AssertionError("Expected Karim balance 3000.0 DA debt, got: " + karim.getBalance());
            }

            List<Payment> karimPayments = paymentDAO.getPaymentsByPatientId(10);
            if (karimPayments.size() != 1 || Math.abs(karimPayments.get(0).getAmount() - 1500.0) > 0.01) {
                throw new AssertionError("Payment ledger was not backfilled for Karim's legacy session!");
            }

            // Verify H2 identity sequence reset: inserting a new patient should get ID >= 21 (not collide with 10 or 20)
            Patient newPatientAfterSql = new Patient();
            newPatientAfterSql.setName("Nouveau Patient Post-SQL");
            newPatientAfterSql.setGender("Homme");
            newPatientAfterSql.setPhone("0550000000");
            if (!patientDAO.addPatient(newPatientAfterSql)) {
                throw new AssertionError("Failed to insert new patient after SQL import (sequence collision!)");
            }
            System.out.println("  ✓ Legacy .sql dump imported cleanly, payment ledger backfilled, and sequences realigned.");

            // -----------------------------------------------------------------
            // Test 6.4b: Data-Only .sql Dump & Legacy .db SQLite File Import
            // -----------------------------------------------------------------
            System.out.println("\n[Test 6.4b] Testing Legacy .db SQLite File Import...");
            File legacyDbFile = tempHome.resolve("old_clinic.db").toFile();
            try (Connection sqliteConn = DriverManager.getConnection("jdbc:sqlite:" + legacyDbFile.getAbsolutePath());
                 Statement stmt = sqliteConn.createStatement()) {
                stmt.execute("CREATE TABLE patient (id INTEGER PRIMARY KEY, name TEXT, gender TEXT, phone TEXT, totalCost REAL, totalPaid REAL)");
                stmt.execute("INSERT INTO patient VALUES (50, 'Yasmine Kaci', 'Femme', '0559887766', 0, 0)");
                stmt.execute("CREATE TABLE session (id INTEGER PRIMARY KEY, patientId INTEGER, sessionDate TEXT, treatment TEXT, paied TEXT, cost REAL, paidAmount REAL)");
                stmt.execute("INSERT INTO session VALUES (200, 50, '2025-08-01', 'Consultation', 'FULL', 1000.0, 2500.0)");
            }

            BackupManager.BackupInspection dbInspection = BackupManager.inspectBackupFile(legacyDbFile);
            if (!"DB".equals(dbInspection.format()) || dbInspection.patientsCount() != 1 || dbInspection.sessionsCount() != 1) {
                throw new AssertionError("Unexpected DB inspection: " + dbInspection);
            }

            BackupManager.restoreOrImportDatabase(legacyDbFile);
            Patient yasmine = patientDAO.getPatientById(50);
            if (yasmine == null || Math.abs(yasmine.getBalance() - (-1500.0)) > 0.01) {
                throw new AssertionError("Expected Yasmine credit balance -1500.0 DA after .db import, got: "
                        + (yasmine != null ? yasmine.getBalance() : "null"));
            }
            System.out.println("  ✓ Legacy .db SQLite file imported cleanly with accurate credit balance (-1500.00 DA).");

            // -----------------------------------------------------------------
            // Test 6.3: .cni Hot-Swap Restore Verification
            // -----------------------------------------------------------------
            System.out.println("\n[Test 6.3] Testing .cni Atomic Hot-Swap Restore...");
            BackupManager.restoreOrImportDatabase(cniFile);

            List<Patient> restoredPatients = patientDAO.getAllPatients();
            if (restoredPatients.size() != 1 || !"Amine Benali".equals(restoredPatients.get(0).getName())) {
                throw new AssertionError("Expected restored patient 'Amine Benali' from .cni, got: " + restoredPatients.size());
            }
            List<BackupManager.LocalBackupItem> localBackups = BackupManager.listLocalBackups();
            boolean hasPreRestoreSnapshot = localBackups.stream()
                    .anyMatch(b -> b.fileName().startsWith("clinic_pre_restore_"));
            if (!hasPreRestoreSnapshot) {
                throw new AssertionError("Expected pre-restore safety snapshot in ~/clinic/backups/");
            }
            System.out.println("  ✓ .cni hot-swap restore succeeded without file locks; pre-restore snapshots verified (" + localBackups.size() + " backups).");

            // -----------------------------------------------------------------
            // Test 6.5: Corrupted .cni & Invalid .sql Rejection
            // -----------------------------------------------------------------
            System.out.println("\n[Test 6.5] Testing Corrupted .cni and Invalid .sql Rejection...");
            File corruptedCni = tempHome.resolve("corrupted.cni").toFile();
            try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(corruptedCni))) {
                zos.putNextEntry(new ZipEntry("backup_manifest.properties"));
                zos.write("backup.sha256=0000000000000000000000000000000000000000000000000000000000000000\n".getBytes(StandardCharsets.UTF_8));
                zos.closeEntry();
                zos.putNextEntry(new ZipEntry("clinic.mv.db"));
                zos.write("tampered-database-bytes".getBytes(StandardCharsets.UTF_8));
                zos.closeEntry();
            }

            boolean cniRejected = false;
            try {
                BackupManager.restoreOrImportDatabase(corruptedCni);
            } catch (Exception expected) {
                cniRejected = true;
            }
            if (!cniRejected) {
                throw new AssertionError("Corrupted .cni with mismatched SHA-256 should have been rejected!");
            }

            File invalidSql = tempHome.resolve("invalid.sql").toFile();
            Files.writeString(invalidSql.toPath(), "SELECT * FROM unknown_random_table;", StandardCharsets.UTF_8);
            boolean sqlRejected = false;
            try {
                BackupManager.restoreOrImportDatabase(invalidSql);
            } catch (Exception expected) {
                sqlRejected = true;
            }
            if (!sqlRejected) {
                throw new AssertionError("Unrelated/invalid .sql file should have been rejected!");
            }

            // Ensure original database remained intact after rejected restores
            if (patientDAO.getAllPatients().size() != 1 || !"Amine Benali".equals(patientDAO.getAllPatients().get(0).getName())) {
                throw new AssertionError("Active database was altered after rejected restore!");
            }
            System.out.println("  ✓ Corrupted .cni and invalid .sql were rejected safely; active database untouched.");

            // -----------------------------------------------------------------
            // Test 6.6: Client-Node Restriction Verification
            // -----------------------------------------------------------------
            System.out.println("\n[Test 6.6] Testing Client-Node Export Restriction...");
            AppConfig.setNodeRole(AppConfig.ROLE_CLIENT);
            boolean clientBlocked = false;
            try {
                BackupManager.exportToCni(tempHome.resolve("should_fail.cni").toFile());
            } catch (IllegalStateException expected) {
                clientBlocked = true;
            } finally {
                AppConfig.setNodeRole(AppConfig.ROLE_HOST);
            }
            if (!clientBlocked) {
                throw new AssertionError("Export from CLIENT node should have been blocked!");
            }
            System.out.println("  ✓ Client-node export restriction enforced.");

            System.out.println("\n===================================================================");
            System.out.println("  ALL BACKUP, RESTORE & SQL/DB IMPORT VERIFICATIONS PASSED!        ");
            System.out.println("===================================================================");

            DatabaseConfig.stopServerAndResetPool();
            System.exit(0);
        } catch (Throwable t) {
            System.err.println("\n❌ VERIFICATION FAILED: " + t.getMessage());
            t.printStackTrace();
            DatabaseConfig.stopServerAndResetPool();
            System.exit(1);
        }
    }
}
