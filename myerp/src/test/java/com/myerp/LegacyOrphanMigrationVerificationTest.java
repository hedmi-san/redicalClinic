package com.myerp;

import config.AppConfig;
import config.DatabaseConfig;
import config.DatabaseInitializer;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.stream.Stream;

/**
 * Verifies that the boot-time SQLite -> H2 migration tolerates orphan rows left behind by the
 * legacy app (SQLite does not enforce foreign keys): the migration must succeed, skip rows whose
 * parent no longer exists, keep valid rows, and write an orphan report.
 * <p>
 * Reproduces the field failure: "CONSTRAINT_35: PUBLIC.billItem FOREIGN KEY(billId) REFERENCES PUBLIC.bill(id) (1)".
 * Uses a disposable user.home; never touches the real clinic directory.
 */
public class LegacyOrphanMigrationVerificationTest {

    public static void main(String[] args) throws Exception {
        Path tempHome = Files.createTempDirectory("clinic_orphan_migration_test_home_");
        System.setProperty("user.home", tempHome.toAbsolutePath().toString());
        System.out.println("Using disposable user.home: " + tempHome.toAbsolutePath());

        try {
            Path clinicDir = tempHome.resolve("clinic");
            Files.createDirectories(clinicDir);
            createLegacySqliteWithOrphans(clinicDir.resolve("clinic.db"));

            AppConfig.setNodeRole(AppConfig.ROLE_HOST);
            AppConfig.setDbPort(19094); // isolated test port
            check(!AppConfig.isMigrationCompleted(), "migration not yet completed before first start");

            // Previously threw: Referential integrity constraint violation (billItem -> bill #1)
            DatabaseInitializer.initializeDatabase();
            check(AppConfig.isMigrationCompleted(), "migration completed despite orphan rows");

            try (Connection h2 = DatabaseConfig.getConnection()) {
                check(count(h2, "bill") == 1, "valid bill migrated");
                check(count(h2, "billItem") == 1, "only the billItem with an existing bill migrated");
                check(exists(h2, "billItem", 11), "valid billItem #11 kept");
                check(!exists(h2, "billItem", 10), "orphan billItem #10 (bill #1 missing) skipped");

                check(count(h2, "worker") == 1, "worker migrated");
                check(exists(h2, "paymentCheck", 1) && !exists(h2, "paymentCheck", 2),
                        "orphan paymentCheck (missing worker) skipped, valid one kept");

                check(count(h2, "patient") == 1, "patient migrated");
                check(exists(h2, "therapyPlan", 1) && !exists(h2, "therapyPlan", 2),
                        "orphan therapyPlan (missing patient) skipped, valid one kept");

                check(exists(h2, "session", 1), "valid standalone session kept");
                check(exists(h2, "session", 2), "valid session in existing plan kept");
                check(!exists(h2, "session", 3), "session of missing patient skipped");
                check(!exists(h2, "session", 4), "session of skipped/missing therapy plan skipped");
                check(exists(h2, "session", 5), "session with NULL patientId kept (no broken link)");
                try (Statement st = h2.createStatement();
                     ResultSet rs = st.executeQuery("SELECT patientId FROM session WHERE id = 5")) {
                    check(rs.next() && rs.getObject(1) == null, "NULL patientId preserved as NULL (not 0)");
                }
            }

            Path logDir = clinicDir.resolve("logs");
            Path report;
            try (Stream<Path> files = Files.list(logDir)) {
                report = files.filter(p -> p.getFileName().toString().startsWith("migration-orphans_"))
                        .findFirst().orElse(null);
            }
            check(report != null, "orphan report written to ~/clinic/logs");
            String content = Files.readString(report);
            check(content.contains("billItem #10") && content.contains("paymentCheck #2")
                            && content.contains("therapyPlan #2") && content.contains("session #3")
                            && content.contains("session #4"),
                    "orphan report lists every skipped row");

            check(Files.exists(clinicDir.resolve("clinic.db")), "original legacy clinic.db left in place");

            System.out.println("✅ LegacyOrphanMigrationVerificationTest passed");
            DatabaseConfig.stopServerAndResetPool();
            System.exit(0);
        } catch (Throwable t) {
            System.err.println("❌ VERIFICATION FAILED: " + t.getMessage());
            t.printStackTrace();
            DatabaseConfig.stopServerAndResetPool();
            System.exit(1);
        }
    }

    private static void createLegacySqliteWithOrphans(Path dbPath) throws Exception {
        try (Connection c = DriverManager.getConnection("jdbc:sqlite:" + dbPath.toAbsolutePath());
             Statement st = c.createStatement()) {
            st.execute("CREATE TABLE worker (id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT NOT NULL, birthDate TEXT, birthPlace TEXT, phoneNumber TEXT, identityCardNumber TEXT NOT NULL, function TEXT NOT NULL, famillySituation TEXT)");
            st.execute("CREATE TABLE paymentCheck (id INTEGER PRIMARY KEY AUTOINCREMENT, workerId INTEGER NOT NULL, paymentDate TEXT, paidAmount REAL, note TEXT)");
            st.execute("CREATE TABLE bill (id INTEGER PRIMARY KEY AUTOINCREMENT, billDate TEXT, totalCost REAL)");
            st.execute("CREATE TABLE billItem (id INTEGER PRIMARY KEY AUTOINCREMENT, billId INTEGER NOT NULL, itemName TEXT, quantity INTEGER, unitPrice REAL)");
            st.execute("CREATE TABLE Users (id INTEGER PRIMARY KEY AUTOINCREMENT, fullName TEXT NOT NULL, userName TEXT NOT NULL, passWord TEXT NOT NULL, userType TEXT)");
            st.execute("CREATE TABLE patient (id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT NOT NULL, gender TEXT, phone TEXT, totalCost REAL, totalPaid REAL)");
            st.execute("CREATE TABLE therapyPlan (id INTEGER PRIMARY KEY AUTOINCREMENT, patientId INTEGER, date TEXT, cost REAL)");
            st.execute("CREATE TABLE session (id INTEGER PRIMARY KEY AUTOINCREMENT, patientId INTEGER, therapyPlanId INTEGER, sessionDate TEXT, treatment TEXT, paied TEXT, cost REAL, paidAmount REAL)");
            st.execute("CREATE TABLE sold (id INTEGER PRIMARY KEY AUTOINCREMENT, itemName TEXT, soldDate TEXT, soldPrice REAL, quantity REAL)");

            st.execute("INSERT INTO Users VALUES (1, 'Admin', 'admin', 'admin123', 'Admin')");
            st.execute("INSERT INTO worker VALUES (1, 'Karim', NULL, NULL, NULL, 'ID1', 'Assistant', NULL)");
            st.execute("INSERT INTO paymentCheck VALUES (1, 1, '2025-01-05', 30000, 'ok')");
            st.execute("INSERT INTO paymentCheck VALUES (2, 99, '2025-01-05', 25000, 'worker deleted')");

            // Bill #1 was deleted in the legacy app, but its item remained (the field failure)
            st.execute("INSERT INTO bill VALUES (2, '2025-02-01', 1500)");
            st.execute("INSERT INTO billItem VALUES (10, 1, 'Gants', 2, 500)");
            st.execute("INSERT INTO billItem VALUES (11, 2, 'Masques', 3, 500)");

            st.execute("INSERT INTO patient VALUES (1, 'Amine', 'Homme', '0550', 0, 0)");
            st.execute("INSERT INTO therapyPlan VALUES (1, 1, '2025-03-01', 10000)");
            st.execute("INSERT INTO therapyPlan VALUES (2, 42, '2025-03-01', 8000)");

            st.execute("INSERT INTO session VALUES (1, 1, NULL, '2025-03-02', 'Soin', 'PAID', 2000, 2000)");
            st.execute("INSERT INTO session VALUES (2, 1, 1, '2025-03-03', 'Forfait', 'PAID', 0, 0)");
            st.execute("INSERT INTO session VALUES (3, 42, NULL, '2025-03-04', 'Soin', 'PAID', 1000, 1000)");
            st.execute("INSERT INTO session VALUES (4, 1, 2, '2025-03-05', 'Forfait', 'PAID', 0, 0)");
            st.execute("INSERT INTO session VALUES (5, NULL, NULL, '2025-03-06', 'Sans patient', 'UNPAID', 500, 0)");

            st.execute("INSERT INTO sold VALUES (1, 'Brosse', '2025-03-07', 300, 1)");
        }
    }

    private static int count(Connection c, String table) throws Exception {
        try (Statement st = c.createStatement(); ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM " + table)) {
            rs.next();
            return rs.getInt(1);
        }
    }

    private static boolean exists(Connection c, String table, int id) throws Exception {
        try (Statement st = c.createStatement();
             ResultSet rs = st.executeQuery("SELECT 1 FROM " + table + " WHERE id = " + id)) {
            return rs.next();
        }
    }

    private static void check(boolean condition, String description) {
        if (!condition) {
            throw new AssertionError("FAILED: " + description);
        }
        System.out.println("  ✔ " + description);
    }
}

