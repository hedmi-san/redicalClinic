package config;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.sql.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Handles transactional, cross-engine data migration from legacy SQLite (clinic.db)
 * to H2 Database with identity sequence realignment and strict row-count parity verification.
 */
public class DatabaseMigrator {

    private static final String[] TABLES = {
            "worker", "paymentCheck", "bill", "billItem", "Users",
            "patient", "therapyPlan", "session", "sold"
    };

    /**
     * Checks if migration is necessary.
     */
    public static boolean shouldMigrate(String sqliteDbPath) {
        if (AppConfig.isMigrationCompleted()) {
            return false;
        }
        File sqliteFile = new File(sqliteDbPath);
        return sqliteFile.exists() && sqliteFile.length() > 0;
    }

    /**
     * Executes the migration in a single atomic transaction.
     */
    public static boolean migrate(String sqliteDbPath, Connection h2Conn) {
        File sqliteFile = new File(sqliteDbPath);
        if (!sqliteFile.exists() || sqliteFile.length() == 0) {
            return false;
        }

        System.out.println("🔄 Starting transactional migration from SQLite to H2...");

        // 1. Create a safety backup of clinic.db
        createPreMigrationBackup(sqliteDbPath);

        String sqliteUrl = "jdbc:sqlite:" + sqliteDbPath;
        try (Connection sqliteConn = DriverManager.getConnection(sqliteUrl)) {
            boolean originalAutoCommit = h2Conn.getAutoCommit();
            h2Conn.setAutoCommit(false);

            try {
                // 2. Stream and insert data for each table
                migrateWorkers(sqliteConn, h2Conn);
                migratePaymentChecks(sqliteConn, h2Conn);
                migrateBills(sqliteConn, h2Conn);
                migrateBillItems(sqliteConn, h2Conn);
                migrateUsers(sqliteConn, h2Conn);
                migratePatients(sqliteConn, h2Conn);
                migrateTherapyPlans(sqliteConn, h2Conn);
                migrateSessions(sqliteConn, h2Conn);
                migrateSolds(sqliteConn, h2Conn);

                // 3. Reset identity sequences for all tables
                resetSequences(h2Conn);

                // 4. Strict row-count parity check
                verifyParity(sqliteConn, h2Conn);

                // Commit transaction
                h2Conn.commit();
                AppConfig.setMigrationCompleted(true);
                System.out.println("✅ Transactional SQLite -> H2 migration committed successfully! Parity verified across all tables.");
                return true;

            } catch (Exception e) {
                System.err.println("❌ Migration error encountered, rolling back: " + e.getMessage());
                h2Conn.rollback();
                throw new RuntimeException("Migration from SQLite to H2 failed: " + e.getMessage(), e);
            } finally {
                h2Conn.setAutoCommit(originalAutoCommit);
            }

        } catch (SQLException e) {
            System.err.println("❌ Database connection error during migration: " + e.getMessage());
            throw new RuntimeException(e);
        }
    }

    private static void migrateWorkers(Connection src, Connection dest) throws SQLException {
        String select = "SELECT id, name, birthDate, birthPlace, phoneNumber, identityCardNumber, function, famillySituation FROM worker";
        String insert = "INSERT INTO worker (id, name, birthDate, birthPlace, phoneNumber, identityCardNumber, function, famillySituation) VALUES (?, ?, ?, ?, ?, ?, ?, ?)";
        try (Statement s = src.createStatement();
             ResultSet rs = s.executeQuery(select);
             PreparedStatement ps = dest.prepareStatement(insert)) {
            while (rs.next()) {
                ps.setInt(1, rs.getInt("id"));
                ps.setString(2, rs.getString("name"));
                ps.setString(3, rs.getString("birthDate"));
                ps.setString(4, rs.getString("birthPlace"));
                ps.setString(5, rs.getString("phoneNumber"));
                ps.setString(6, rs.getString("identityCardNumber"));
                ps.setString(7, rs.getString("function"));
                ps.setString(8, rs.getString("famillySituation"));
                ps.addBatch();
            }
            ps.executeBatch();
        }
    }

    private static void migratePaymentChecks(Connection src, Connection dest) throws SQLException {
        String select = "SELECT id, workerId, paymentDate, paidAmount, note FROM paymentCheck";
        String insert = "INSERT INTO paymentCheck (id, workerId, paymentDate, paidAmount, note) VALUES (?, ?, ?, ?, ?)";
        try (Statement s = src.createStatement();
             ResultSet rs = s.executeQuery(select);
             PreparedStatement ps = dest.prepareStatement(insert)) {
            while (rs.next()) {
                ps.setInt(1, rs.getInt("id"));
                ps.setInt(2, rs.getInt("workerId"));
                ps.setString(3, rs.getString("paymentDate"));
                ps.setDouble(4, rs.getDouble("paidAmount"));
                ps.setString(5, rs.getString("note"));
                ps.addBatch();
            }
            ps.executeBatch();
        }
    }

    private static void migrateBills(Connection src, Connection dest) throws SQLException {
        String select = "SELECT id, billDate, totalCost FROM bill";
        String insert = "INSERT INTO bill (id, billDate, totalCost) VALUES (?, ?, ?)";
        try (Statement s = src.createStatement();
             ResultSet rs = s.executeQuery(select);
             PreparedStatement ps = dest.prepareStatement(insert)) {
            while (rs.next()) {
                ps.setInt(1, rs.getInt("id"));
                ps.setString(2, rs.getString("billDate"));
                ps.setDouble(3, rs.getDouble("totalCost"));
                ps.addBatch();
            }
            ps.executeBatch();
        }
    }

    private static void migrateBillItems(Connection src, Connection dest) throws SQLException {
        String select = "SELECT id, billId, itemName, quantity, unitPrice FROM billItem";
        String insert = "INSERT INTO billItem (id, billId, itemName, quantity, unitPrice) VALUES (?, ?, ?, ?, ?)";
        try (Statement s = src.createStatement();
             ResultSet rs = s.executeQuery(select);
             PreparedStatement ps = dest.prepareStatement(insert)) {
            while (rs.next()) {
                ps.setInt(1, rs.getInt("id"));
                ps.setInt(2, rs.getInt("billId"));
                ps.setString(3, rs.getString("itemName"));
                ps.setInt(4, rs.getInt("quantity"));
                ps.setDouble(5, rs.getDouble("unitPrice"));
                ps.addBatch();
            }
            ps.executeBatch();
        }
    }

    private static void migrateUsers(Connection src, Connection dest) throws SQLException {
        String select = "SELECT id, fullName, userName, passWord, userType FROM Users";
        String insert = "INSERT INTO Users (id, fullName, userName, passWord, userType) VALUES (?, ?, ?, ?, ?)";
        try (Statement s = src.createStatement();
             ResultSet rs = s.executeQuery(select);
             PreparedStatement ps = dest.prepareStatement(insert)) {
            while (rs.next()) {
                ps.setInt(1, rs.getInt("id"));
                ps.setString(2, rs.getString("fullName"));
                ps.setString(3, rs.getString("userName"));
                ps.setString(4, rs.getString("passWord"));
                String userType = rs.getString("userType");
                if (userType == null || userType.trim().isEmpty()) {
                    userType = "Admin";
                }
                ps.setString(5, userType);
                ps.addBatch();
            }
            ps.executeBatch();
        }
    }

    private static void migratePatients(Connection src, Connection dest) throws SQLException {
        String select = "SELECT id, name, gender, phone, totalCost, totalPaid FROM patient";
        String insert = "INSERT INTO patient (id, name, gender, phone, totalCost, totalPaid) VALUES (?, ?, ?, ?, ?, ?)";
        try (Statement s = src.createStatement();
             ResultSet rs = s.executeQuery(select);
             PreparedStatement ps = dest.prepareStatement(insert)) {
            while (rs.next()) {
                ps.setInt(1, rs.getInt("id"));
                ps.setString(2, rs.getString("name"));
                ps.setString(3, rs.getString("gender"));
                ps.setString(4, rs.getString("phone"));
                ps.setDouble(5, rs.getDouble("totalCost"));
                ps.setDouble(6, rs.getDouble("totalPaid"));
                ps.addBatch();
            }
            ps.executeBatch();
        }
    }

    private static void migrateTherapyPlans(Connection src, Connection dest) throws SQLException {
        String select = "SELECT id, patientId, date, cost FROM therapyPlan";
        String insert = "INSERT INTO therapyPlan (id, patientId, date, cost) VALUES (?, ?, ?, ?)";
        try (Statement s = src.createStatement();
             ResultSet rs = s.executeQuery(select);
             PreparedStatement ps = dest.prepareStatement(insert)) {
            while (rs.next()) {
                ps.setInt(1, rs.getInt("id"));
                ps.setInt(2, rs.getInt("patientId"));
                ps.setString(3, rs.getString("date"));
                ps.setDouble(4, rs.getDouble("cost"));
                ps.addBatch();
            }
            ps.executeBatch();
        }
    }

    private static void migrateSessions(Connection src, Connection dest) throws SQLException {
        String select = "SELECT id, patientId, therapyPlanId, sessionDate, treatment, paied, cost, paidAmount FROM session";
        String insert = "INSERT INTO session (id, patientId, therapyPlanId, sessionDate, treatment, paied, cost, paidAmount) VALUES (?, ?, ?, ?, ?, ?, ?, ?)";
        try (Statement s = src.createStatement();
             ResultSet rs = s.executeQuery(select);
             PreparedStatement ps = dest.prepareStatement(insert)) {
            while (rs.next()) {
                ps.setInt(1, rs.getInt("id"));
                ps.setInt(2, rs.getInt("patientId"));
                int planId = rs.getInt("therapyPlanId");
                if (rs.wasNull()) {
                    ps.setNull(3, Types.INTEGER);
                } else {
                    ps.setInt(3, planId);
                }
                ps.setString(4, rs.getString("sessionDate"));
                ps.setString(5, rs.getString("treatment"));
                ps.setString(6, rs.getString("paied"));
                ps.setDouble(7, rs.getDouble("cost"));
                ps.setDouble(8, rs.getDouble("paidAmount"));
                ps.addBatch();
            }
            ps.executeBatch();
        }
    }

    private static void migrateSolds(Connection src, Connection dest) throws SQLException {
        String select = "SELECT id, itemName, soldDate, soldPrice, quantity FROM sold";
        String insert = "INSERT INTO sold (id, itemName, soldDate, soldPrice, quantity) VALUES (?, ?, ?, ?, ?)";
        try (Statement s = src.createStatement();
             ResultSet rs = s.executeQuery(select);
             PreparedStatement ps = dest.prepareStatement(insert)) {
            while (rs.next()) {
                ps.setInt(1, rs.getInt("id"));
                ps.setString(2, rs.getString("itemName"));
                ps.setString(3, rs.getString("soldDate"));
                ps.setDouble(4, rs.getDouble("soldPrice"));
                ps.setDouble(5, rs.getDouble("quantity"));
                ps.addBatch();
            }
            ps.executeBatch();
        }
    }

    /**
     * Resets identity sequences on all tables to MAX(id) + 1.
     */
    public static void resetSequences(Connection conn) throws SQLException {
        try (Statement stmt = conn.createStatement()) {
            for (String table : TABLES) {
                int maxId = 0;
                try (ResultSet rs = stmt.executeQuery("SELECT COALESCE(MAX(id), 0) FROM " + table)) {
                    if (rs.next()) {
                        maxId = rs.getInt(1);
                    }
                }
                int nextId = maxId + 1;
                stmt.execute("ALTER TABLE " + table + " ALTER COLUMN id RESTART WITH " + nextId);
                System.out.println("   Identity sequence for " + table + " reset to " + nextId);
            }
        }
    }

    /**
     * Verifies that table row counts between SQLite and H2 match exactly.
     */
    public static void verifyParity(Connection sqliteConn, Connection h2Conn) throws SQLException {
        Map<String, Integer> sqliteCounts = new LinkedHashMap<>();
        Map<String, Integer> h2Counts = new LinkedHashMap<>();

        try (Statement s1 = sqliteConn.createStatement();
             Statement s2 = h2Conn.createStatement()) {

            for (String table : TABLES) {
                int c1 = 0;
                try (ResultSet rs = s1.executeQuery("SELECT COUNT(*) FROM " + table)) {
                    if (rs.next()) c1 = rs.getInt(1);
                }
                sqliteCounts.put(table, c1);

                int c2 = 0;
                try (ResultSet rs = s2.executeQuery("SELECT COUNT(*) FROM " + table)) {
                    if (rs.next()) c2 = rs.getInt(1);
                }
                h2Counts.put(table, c2);

                if (c1 != c2) {
                    throw new IllegalStateException("Parity check failed for table [" + table +
                            "]: SQLite has " + c1 + " records, but H2 has " + c2 + " records.");
                }
                System.out.println("   ✔ Parity confirmed for " + table + ": " + c1 + " rows");
            }
        }
    }

    private static void createPreMigrationBackup(String sqliteDbPath) {
        try {
            Path dbPath = Paths.get(sqliteDbPath);
            if (Files.exists(dbPath) && Files.size(dbPath) > 0) {
                Path backupDir = dbPath.getParent().resolve("backups");
                Files.createDirectories(backupDir);
                String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
                Path backupFile = backupDir.resolve("clinic_pre_migration_" + timestamp + ".db");
                Files.copy(dbPath, backupFile, StandardCopyOption.REPLACE_EXISTING);
                System.out.println("📦 Pre-migration backup created at: " + backupFile);
            }
        } catch (Exception e) {
            System.err.println("⚠️ Warning: Pre-migration backup creation failed: " + e.getMessage());
        }
    }
}
