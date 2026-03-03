package config;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Creates all clinic database tables on first run.
 * Call DatabaseInitializer.initializeDatabase() once at application startup.
 */
public class DatabaseInitializer {

    public static void initializeDatabase() {
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
                            name        TEXT    NOT NULL,
                            birthDate        TEXT,
                            birthPlace       TEXT,
                            phoneNumber      TEXT,
                            identityCardNumber      TEXT NOT NULL,
                            function    TEXT NOT NULL,
                            famillySituation        TEXT
                        );
                    """);

            // ----------------------------------------------------------------
            // 2. Salary Record
            // ----------------------------------------------------------------
            stmt.execute("""
                        CREATE TABLE IF NOT EXISTS salaryRecord (
                            id      INTEGER PRIMARY KEY AUTOINCREMENT,
                            workerId        INTEGER NOT NULL REFERENCES worker(id)  ON DELETE CASCADE,
                            totalEarned     REAL,
                            totalPaid       REAL
                        );
                    """);

            // ----------------------------------------------------------------
            // 3. Payment Check
            // ----------------------------------------------------------------
            stmt.execute("""
                        CREATE TABLE IF NOT EXISTS paymentCheck (
                            id        INTEGER PRIMARY KEY AUTOINCREMENT,
                            salaryRecordId      INTEGER NOT NULL REFERENCES salaryRecord(id)  ON DELETE CASCADE,
                            paymentDate     TEXT DEFAULT (datetime('now')),
                            salary      REAL,
                            paidAmount REAL
                        );
                    """);

            // ----------------------------------------------------------------
            // 4. Bill
            // ----------------------------------------------------------------
            stmt.execute("""
                        CREATE TABLE IF NOT EXISTS bill (
                            id              INTEGER PRIMARY KEY AUTOINCREMENT,
                            billDate        TEXT DEFAULT (datetime('now')),
                            totalCost       REAL,
                            paidAmount      REAL
                        );
                    """);

            // ----------------------------------------------------------------
            // 5. Bill Item
            // ----------------------------------------------------------------
            stmt.execute("""
                        CREATE TABLE IF NOT EXISTS billItem (
                            id              INTEGER PRIMARY KEY AUTOINCREMENT,
                            billId          INTEGER NOT NULL REFERENCES bill(id)  ON DELETE CASCADE,
                            itemName        TEXT,
                            quantity        INTEGER,
                            unitPrice       REAL
                        );
                    """);

            // ----------------------------------------------------------------
            // 6. User
            // ----------------------------------------------------------------
            stmt.execute("""
                        CREATE TABLE IF NOT EXISTS Users (
                            id              INTEGER PRIMARY KEY AUTOINCREMENT,
                            fullName        TEXT NOT NULL,
                            userName        TEXT NOT NULL,
                            passWord        TEXT(128),
                            userType        TEXT
                        );
                    """);

            // ----------------------------------------------------------------
            // 7. Patient
            // ----------------------------------------------------------------
            stmt.execute("""
                        CREATE TABLE IF NOT EXISTS patient (
                            id          INTEGER PRIMARY KEY AUTOINCREMENT,
                            name        TEXT NOT NULL,
                            phone       TEXT,
                            totalCost       REAL,
                            totalPaid       REAL
                        );
                    """);

            // ----------------------------------------------------------------
            // 8. Session
            // ----------------------------------------------------------------
            stmt.execute("""
                        CREATE TABLE IF NOT EXISTS session (
                            id              INTEGER PRIMARY KEY AUTOINCREMENT,
                            patientId       INTEGER NOT NULL REFERENCES patient(id)  ON DELETE CASCADE,
                            sessionDate     TEXT DEFAULT (datetime('now')),
                            treatment       TEXT,
                            cost        REAL
                        );
                    """);

            // ----------------------------------------------------------------
            // 9. Patient Payment
            // ----------------------------------------------------------------
            stmt.execute("""
                        CREATE TABLE IF NOT EXISTS patientPayment (
                            id          INTEGER PRIMARY KEY AUTOINCREMENT,
                            patientId   INTEGER NOT NULL REFERENCES patient(id) ON DELETE CASCADE,
                            paymentDate TEXT DEFAULT (datetime('now')),
                            amount      REAL
                        );
                    """);

            // ----------------------------------------------------------------
            // 10. Individual
            // ----------------------------------------------------------------
            stmt.execute("""
                        CREATE TABLE IF NOT EXISTS individual (
                            id          INTEGER PRIMARY KEY AUTOINCREMENT,
                            invoiceId   INTEGER NOT NULL REFERENCES invoices(id) ON DELETE CASCADE,
                            amount      REAL    NOT NULL,
                            method      TEXT    DEFAULT 'Cash',  -- Cash/Card/Insurance/Transfer
                            paidAt      TEXT    DEFAULT (datetime('now')),
                            reference   TEXT,
                            notes       TEXT
                        );
                    """);

            System.out.println("Database initialized successfully.");


        } catch (SQLException e) {
            System.err.println("❌ Database initialization failed: " + e.getMessage());
            throw new RuntimeException("Database initialization failed", e);
        }
    }
}
