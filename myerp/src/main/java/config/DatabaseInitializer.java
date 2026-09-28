package config;

import dao.UserDAO;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

public class DatabaseInitializer {

    public static void initializeDatabase() {
        // Create an automatic safety backup of existing clinic.db before performing any operations
        backupDatabaseIfExists();

        try (Connection conn = DatabaseConfig.getConnection();
                Statement stmt = conn.createStatement()) {

            // Enable foreign-key enforcement in SQLite
            stmt.execute("PRAGMA foreign_keys = ON;");

            // ----------------------------------------------------------------
            // 1. Workers
            // ----------------------------------------------------------------
            stmt.execute("""
                        CREATE TABLE IF NOT EXISTS worker (
                            id          INTEGER PRIMARY KEY AUTOINCREMENT,
                            name        VARCHAR(25) NOT NULL,
                            birthDate        TEXT,
                            birthPlace       TEXT,
                            phoneNumber      TEXT,
                            identityCardNumber      TEXT NOT NULL,
                            function    TEXT NOT NULL,
                            famillySituation        TEXT
                        );
                    """);

            // ----------------------------------------------------------------
            // 2. Payment Check
            // ----------------------------------------------------------------
            stmt.execute("""
                        CREATE TABLE IF NOT EXISTS paymentCheck (
                            id        INTEGER PRIMARY KEY AUTOINCREMENT,
                            workerId      INTEGER NOT NULL REFERENCES worker(id)  ON DELETE CASCADE,
                            paymentDate     TEXT DEFAULT (datetime('now')),
                            paidAmount REAL,
                            note TEXT
                        );
                    """);

            // ----------------------------------------------------------------
            // 3. Bill
            // ----------------------------------------------------------------
            stmt.execute("""
                        CREATE TABLE IF NOT EXISTS bill (
                            id              INTEGER PRIMARY KEY AUTOINCREMENT,
                            billDate        TEXT DEFAULT (datetime('now')),
                            totalCost       REAL
                        );
                    """);

            // ----------------------------------------------------------------
            // 4. Bill Item
            // ----------------------------------------------------------------
            stmt.execute("""
                        CREATE TABLE IF NOT EXISTS billItem (
                            id              INTEGER PRIMARY KEY AUTOINCREMENT,
                            billId          INTEGER NOT NULL REFERENCES bill(id)  ON DELETE CASCADE,
                            itemName        VARCHAR(25),
                            quantity        INTEGER,
                            unitPrice       REAL
                        );
                    """);

            // ----------------------------------------------------------------
            // 5. User
            // ----------------------------------------------------------------
            stmt.execute("""
                        CREATE TABLE IF NOT EXISTS Users (
                            id              INTEGER PRIMARY KEY AUTOINCREMENT,
                            fullName        TEXT NOT NULL,
                            userName        TEXT NOT NULL,
                            passWord        TEXT NOT NULL,
                            userType        TEXT
                        );
                    """);

            // ----------------------------------------------------------------
            // 6. Patient
            // ----------------------------------------------------------------
            stmt.execute("""
                        CREATE TABLE IF NOT EXISTS patient (
                            id          INTEGER PRIMARY KEY AUTOINCREMENT,
                            name        TEXT NOT NULL,
                            gender      VARCHAR(10),
                            phone       VARCHAR(10),
                            totalCost       REAL,
                            totalPaid       REAL
                        );
                    """);

            // ----------------------------------------------------------------
            // 7. TherapyPlan
            // ----------------------------------------------------------------
            stmt.execute("""
                        CREATE TABLE IF NOT EXISTS therapyPlan (
                            id              INTEGER PRIMARY KEY AUTOINCREMENT,
                            patientId       INTEGER REFERENCES patient(id)  ON DELETE CASCADE,
                            date     TEXT DEFAULT (datetime('now')),
                            cost        REAL
                        );
                    """);

            // ----------------------------------------------------------------
            // 8. Session
            // ----------------------------------------------------------------
            stmt.execute("""
                        CREATE TABLE IF NOT EXISTS session (
                            id              INTEGER PRIMARY KEY AUTOINCREMENT,
                            patientId       INTEGER REFERENCES patient(id)  ON DELETE CASCADE,
                            therapyPlanId   INTEGER REFERENCES therapyPlan(id) ON DELETE CASCADE,
                            sessionDate     TEXT DEFAULT (datetime('now')),
                            treatment       TEXT,
                            paied      VARCHAR(10),
                            cost        REAL,
                            paidAmount      REAL
                        );
                    """);

            // ----------------------------------------------------------------
            // 9. Sold
            // ----------------------------------------------------------------
            stmt.execute("""
                        CREATE TABLE IF NOT EXISTS sold (
                            id              INTEGER PRIMARY KEY AUTOINCREMENT,
                            itemName        VARCHAR(25),
                            soldDate     TEXT DEFAULT (datetime('now')),
                            soldPrice      REAL,
                            quantity       REAL
                        );
                    """);

            // Normalize existing user records without a defined role to 'Admin'
            stmt.execute("UPDATE Users SET userType = 'Admin' WHERE userType IS NULL OR TRIM(userType) = '';");

            System.out.println("Database initialized successfully.");

            // Seed a default admin user if none exists yet
            UserDAO.seedDefaultAdmin();

        } catch (SQLException e) {
            System.err.println("❌ Database initialization failed: " + e.getMessage());
            throw new RuntimeException("Database initialization failed", e);
        }
    }

    /**
     * Creates a timestamped backup copy of clinic.db in ~/clinic/backups/
     * before running any schema or database operations.
     */
    private static void backupDatabaseIfExists() {
        try {
            java.nio.file.Path dbPath = java.nio.file.Paths.get(DatabaseConfig.getDatabasePath());
            if (java.nio.file.Files.exists(dbPath) && java.nio.file.Files.size(dbPath) > 0) {
                java.nio.file.Path backupDir = dbPath.getParent().resolve("backups");
                java.nio.file.Files.createDirectories(backupDir);
                String timestamp = java.time.LocalDateTime.now()
                        .format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
                java.nio.file.Path backupFile = backupDir.resolve("clinic_backup_" + timestamp + ".db");
                java.nio.file.Files.copy(dbPath, backupFile, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                System.out.println("✅ Pre-launch database backup created: " + backupFile);
            }
        } catch (Exception e) {
            System.err.println("⚠️ Warning: Failed to create database backup: " + e.getMessage());
        }
    }
}
