package config;

import dao.UserDAO;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.sql.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Handles transactional, cross-engine data migration from legacy SQLite (.db) and SQL dump (.sql)
 * files into the H2 Database with schema normalization, payment ledger backfill,
 * identity sequence realignment, and strict row-count parity verification.
 */
public class DatabaseMigrator {

    private static final String[] TABLES = {
            "worker", "paymentCheck", "bill", "billItem", "Users",
            "patient", "therapyPlan", "session", "sold"
    };

    private static final String[] ALL_TABLES_WITH_PAYMENT = {
            "worker", "paymentCheck", "bill", "billItem", "Users",
            "patient", "therapyPlan", "session", "sold", "payment"
    };

    /**
     * Checks if boot-time migration from ~/clinic/clinic.db is necessary.
     */
    public static boolean shouldMigrate(String sqliteDbPath) {
        if (AppConfig.isMigrationCompleted()) {
            return false;
        }
        File sqliteFile = new File(sqliteDbPath);
        return sqliteFile.exists() && sqliteFile.length() > 0;
    }

    /**
     * Executes the boot-time migration in a single atomic transaction.
     */
    public static boolean migrate(String sqliteDbPath, Connection h2Conn) {
        File sqliteFile = new File(sqliteDbPath);
        if (!sqliteFile.exists() || sqliteFile.length() == 0) {
            return false;
        }

        System.out.println("🔄 Starting transactional migration from SQLite to H2...");

        // 1. Create a safety backup of clinic.db
        createPreMigrationBackup(sqliteDbPath);

        // Ensure H2 columns are widened to TEXT to avoid string length limits
        widenH2ColumnsToText(h2Conn);

        String sqliteUrl = "jdbc:sqlite:" + sqliteDbPath;
        try (Connection sqliteConn = DriverManager.getConnection(sqliteUrl)) {
            normalizeSqliteSchema(sqliteConn);

            boolean originalAutoCommit = h2Conn.getAutoCommit();
            h2Conn.setAutoCommit(false);

            try {
                // 2. Stream and insert data for each table (orphan rows are skipped and reported)
                OrphanReport report = new OrphanReport();
                migrateAllCoreTables(sqliteConn, h2Conn, report);

                // 3. Reset identity sequences for all tables
                resetSequences(h2Conn);

                // 4. Strict row-count parity check (accounting for reported orphan rows)
                verifyParity(sqliteConn, h2Conn, report);

                // Commit transaction
                h2Conn.commit();
                report.writeLogIfNeeded();
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

    /**
     * Stages a legacy .sql dump or .db SQLite file into a temporary normalized SQLite database.
     * Validates that recognizable clinic tables or statements exist and returns the path to the staged SQLite DB.
     */
    public static Path stageLegacySqlOrDb(File sourceFile) throws IOException, SQLException {
        if (sourceFile == null || !sourceFile.exists() || sourceFile.length() == 0) {
            throw new IllegalArgumentException("Le fichier sélectionné est vide ou introuvable.");
        }

        String nameLower = sourceFile.getName().toLowerCase(Locale.ROOT);
        Path stagingDbPath = Files.createTempFile("clinic_legacy_stage_", ".db");

        try {
            if (nameLower.endsWith(".db") || nameLower.endsWith(".sqlite") || nameLower.endsWith(".sqlite3")) {
                // Validate SQLite file header ("SQLite format 3\0")
                byte[] header = new byte[16];
                try (var in = Files.newInputStream(sourceFile.toPath())) {
                    int read = in.read(header);
                    String magic = read >= 15 ? new String(header, 0, 15, StandardCharsets.US_ASCII) : "";
                    if (!"SQLite format 3".equals(magic)) {
                        throw new IllegalArgumentException("Le fichier .db n'est pas une base SQLite valide.");
                    }
                }
                Files.copy(sourceFile.toPath(), stagingDbPath, StandardCopyOption.REPLACE_EXISTING);

                String sqliteUrl = "jdbc:sqlite:" + stagingDbPath.toAbsolutePath();
                try (Connection sqliteConn = DriverManager.getConnection(sqliteUrl)) {
                    if (!hasAnyRecognizedClinicTable(sqliteConn)) {
                        throw new IllegalArgumentException("Aucune table de la clinique n'a été trouvée dans ce fichier .db.");
                    }
                    normalizeSqliteSchema(sqliteConn);
                }
                return stagingDbPath;

            } else if (nameLower.endsWith(".sql")) {
                stageSqlScriptIntoSqlite(sourceFile.toPath(), stagingDbPath);
                return stagingDbPath;
            } else {
                throw new IllegalArgumentException("Format de fichier non pris en charge : " + sourceFile.getName());
            }
        } catch (Exception e) {
            try {
                Files.deleteIfExists(stagingDbPath);
            } catch (IOException ignored) {
            }
            if (e instanceof IOException ioEx) throw ioEx;
            if (e instanceof SQLException sqlEx) throw sqlEx;
            if (e instanceof IllegalArgumentException argEx) throw argEx;
            throw new IllegalArgumentException("Erreur lors de l'analyse du fichier : " + e.getMessage(), e);
        }
    }

    /**
     * Parses a .sql script and executes it against a temporary SQLite database,
     * handling both DDL+DML dumps and INSERT-only dumps as well as legacy 7-column session
     * and 4-column Users schemas.
     */
    private static void stageSqlScriptIntoSqlite(Path sqlFilePath, Path stagingDbPath) throws IOException, SQLException {
        String content;
        try {
            content = Files.readString(sqlFilePath, StandardCharsets.UTF_8);
        } catch (IOException e) {
            content = Files.readString(sqlFilePath, StandardCharsets.ISO_8859_1);
        }

        // Remove UTF-8 BOM if present
        if (content.startsWith("\uFEFF")) {
            content = content.substring(1);
        }

        List<String> rawStatements = splitSqlStatements(content);
        if (rawStatements.isEmpty()) {
            throw new IllegalArgumentException("Le fichier SQL ne contient aucune instruction valide.");
        }

        boolean hasCreateTable = false;
        int sessionPositionalCols = 8;
        int usersPositionalCols = 5;
        int recognizedStatementCount = 0;

        Pattern insertPositionalPattern = Pattern.compile(
                "(?is)^INSERT\\s+(?:OR\\s+\\w+\\s+)?INTO\\s+[`\"']?(?:PUBLIC[`\"']?\\.[`\"']?)?(\\w+)[`\"']?\\s+VALUES\\s*\\((.*)\\)$"
        );
        Pattern tableRefPattern = Pattern.compile(
                "(?is)\\b(worker|paymentCheck|bill|billItem|Users|patient|therapyPlan|therapy_plan|session|sold|payment)\\b"
        );

        for (String stmt : rawStatements) {
            String upper = stmt.trim().toUpperCase(Locale.ROOT);
            if (upper.startsWith("CREATE TABLE") || upper.startsWith("CREATE CACHED TABLE") || upper.startsWith("CREATE MEMORY TABLE")) {
                hasCreateTable = true;
            }
            if (tableRefPattern.matcher(stmt).find()) {
                recognizedStatementCount++;
            }
            Matcher m = insertPositionalPattern.matcher(stmt.trim());
            if (m.matches()) {
                String tbl = m.group(1);
                String firstRowTuple = extractFirstTupleContent(m.group(2));
                int colCount = countTupleValues(firstRowTuple);
                if ("session".equalsIgnoreCase(tbl) && (colCount == 7 || colCount == 8)) {
                    sessionPositionalCols = colCount;
                } else if ("users".equalsIgnoreCase(tbl) && (colCount == 4 || colCount == 5)) {
                    usersPositionalCols = colCount;
                }
            }
        }

        if (recognizedStatementCount == 0) {
            throw new IllegalArgumentException("Le fichier SQL ne contient aucune table ou donnée reconnue de la clinique.");
        }

        String sqliteUrl = "jdbc:sqlite:" + stagingDbPath.toAbsolutePath();
        try (Connection sqliteConn = DriverManager.getConnection(sqliteUrl)) {
            try (Statement pragmaStmt = sqliteConn.createStatement()) {
                pragmaStmt.execute("PRAGMA foreign_keys = OFF;");
            }

            sqliteConn.setAutoCommit(false);
            try (Statement stmt = sqliteConn.createStatement()) {
                // If data-only SQL dump (no CREATE TABLE), pre-create tables matching legacy column counts
                if (!hasCreateTable) {
                    createBaselineSqliteTables(stmt, sessionPositionalCols == 8, usersPositionalCols == 5);
                }

                int executedDataOrDdl = 0;
                for (String raw : rawStatements) {
                    String normalized = normalizeSqlStatementForSqlite(raw);
                    if (normalized == null || normalized.isBlank()) {
                        continue;
                    }
                    stmt.execute(normalized);
                    executedDataOrDdl++;
                }

                if (executedDataOrDdl == 0) {
                    throw new IllegalArgumentException("Aucune instruction SQL applicable n'a pu être exécutée.");
                }

                sqliteConn.commit();
            } catch (Exception e) {
                sqliteConn.rollback();
                throw e;
            } finally {
                sqliteConn.setAutoCommit(true);
            }

            // Ensure all modern tables and columns exist after executing the SQL script
            normalizeSqliteSchema(sqliteConn);
        }
    }

    /**
     * Performs a clean-replace migration from a staged SQLite database into H2
     * inside an atomic transaction, backfilling the payment ledger, recalculating
     * patient balances, and resetting all H2 IDENTITY sequences.
     */
    public static void cleanReplaceFromSqlite(Path stagedSqlitePath, Connection h2Conn) throws SQLException {
        widenH2ColumnsToText(h2Conn);

        String sqliteUrl = "jdbc:sqlite:" + stagedSqlitePath.toAbsolutePath();
        try (Connection sqliteConn = DriverManager.getConnection(sqliteUrl)) {
            normalizeSqliteSchema(sqliteConn);

            boolean originalAutoCommit = h2Conn.getAutoCommit();
            h2Conn.setAutoCommit(false);

            try {
                // 1. Clear existing H2 data in foreign-key-safe child-first order
                try (Statement clearStmt = h2Conn.createStatement()) {
                    clearStmt.execute("UPDATE payment SET reversalOf = NULL");
                    clearStmt.execute("DELETE FROM payment");
                    clearStmt.execute("DELETE FROM session");
                    clearStmt.execute("DELETE FROM therapyPlan");
                    clearStmt.execute("DELETE FROM billItem");
                    clearStmt.execute("DELETE FROM bill");
                    clearStmt.execute("DELETE FROM paymentCheck");
                    clearStmt.execute("DELETE FROM worker");
                    clearStmt.execute("DELETE FROM sold");
                    clearStmt.execute("DELETE FROM patient");
                    clearStmt.execute("DELETE FROM Users");
                }

                // 2. Migrate all 9 core tables + payment table (if present in staging DB)
                OrphanReport report = new OrphanReport();
                migrateAllCoreTables(sqliteConn, h2Conn, report);
                migratePaymentsIfPresent(sqliteConn, h2Conn, report);

                // 3. Verify strict row-count parity on the 9 core tables before seeding fallback admin
                verifyParity(sqliteConn, h2Conn, report);

                // 4. Ensure at least one Admin user exists so login works after import
                ensureAdminUserExists(h2Conn);

                // 5. Backfill payment ledger from historical sessions and therapy plans
                backfillPaymentLedgerAndPatientTotals(h2Conn);

                // 6. Reset identity sequences for all 10 tables (including payment)
                resetAllSequences(h2Conn);

                h2Conn.commit();
                report.writeLogIfNeeded();
                AppConfig.setMigrationCompleted(true);
                System.out.println("✅ Clean-replace import into H2 completed and verified successfully.");
            } catch (Exception e) {
                h2Conn.rollback();
                throw new SQLException("Échec de l'importation dans la base H2 : " + e.getMessage(), e);
            } finally {
                h2Conn.setAutoCommit(originalAutoCommit);
            }
        }
    }

    /**
     * Legacy SQLite databases were used without foreign-key enforcement, so deleting a parent
     * (bill, worker, patient, therapy plan) could leave child rows pointing at a missing id.
     * H2 enforces these constraints, so such rows are migrated as if the H2 rules had always applied:
     * rows whose owning parent is gone are skipped (ON DELETE CASCADE semantics) and optional
     * references to missing rows are cleared (ON DELETE SET NULL semantics). Every skipped or
     * detached row is recorded so it can be written to a log file after the migration.
     */
    static final class OrphanReport {
        private final Map<String, Integer> skippedCounts = new LinkedHashMap<>();
        private final List<String> entries = new ArrayList<>();
        private Path logFile;

        void skip(String table, int id, String details) {
            skippedCounts.merge(table, 1, Integer::sum);
            entries.add("SKIPPED  " + table + " #" + id + " — " + details);
        }

        void detach(String table, int id, String details) {
            entries.add("DETACHED " + table + " #" + id + " — " + details);
        }

        int skipped(String table) {
            return skippedCounts.getOrDefault(table, 0);
        }

        int totalEntries() {
            return entries.size();
        }

        Path getLogFile() {
            return logFile;
        }

        void writeLogIfNeeded() {
            if (entries.isEmpty()) {
                return;
            }
            System.out.println("⚠️ Migration ignored/detached " + entries.size()
                    + " orphan row(s) from the legacy database: " + skippedCounts);
            try {
                Path logDir = Paths.get(AppConfig.getClinicDir(), "logs");
                Files.createDirectories(logDir);
                String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
                logFile = logDir.resolve("migration-orphans_" + timestamp + ".log");
                List<String> lines = new ArrayList<>();
                lines.add("Legacy SQLite -> H2 migration — orphan rows (" + LocalDateTime.now() + ")");
                lines.add("These rows referenced records that no longer existed in the legacy database.");
                lines.add("SKIPPED = row not imported; DETACHED = row imported with the broken link cleared.");
                lines.add("The original data remains in clinic.db and in backups/clinic_pre_migration_*.db.");
                lines.add("");
                lines.addAll(entries);
                Files.write(logFile, lines, StandardCharsets.UTF_8);
                System.out.println("   Orphan report written to " + logFile);
            } catch (Exception e) {
                System.err.println("⚠️ Could not write orphan migration report: " + e.getMessage());
            }
        }
    }

    private static void migrateAllCoreTables(Connection sqliteConn, Connection h2Conn, OrphanReport report) throws SQLException {
        migrateWorkers(sqliteConn, h2Conn);
        migratePaymentChecks(sqliteConn, h2Conn, report);
        migrateBills(sqliteConn, h2Conn);
        migrateBillItems(sqliteConn, h2Conn, report);
        migrateUsers(sqliteConn, h2Conn);
        migratePatients(sqliteConn, h2Conn);
        migrateTherapyPlans(sqliteConn, h2Conn, report);
        migrateSessions(sqliteConn, h2Conn, report);
        migrateSolds(sqliteConn, h2Conn);
    }

    /** Loads the ids already inserted into an H2 table (used to validate foreign keys before insert). */
    private static Set<Integer> loadIds(Connection h2Conn, String table) throws SQLException {
        Set<Integer> ids = new HashSet<>();
        try (Statement s = h2Conn.createStatement();
             ResultSet rs = s.executeQuery("SELECT id FROM " + table)) {
            while (rs.next()) {
                ids.add(rs.getInt(1));
            }
        }
        return ids;
    }

    /** Reads a nullable integer column; returns null for SQL NULL. */
    private static Integer getNullableInt(ResultSet rs, String column) throws SQLException {
        int value = rs.getInt(column);
        return rs.wasNull() ? null : value;
    }

    private static void widenH2ColumnsToText(Connection h2Conn) {
        try (Statement alterStmt = h2Conn.createStatement()) {
            alterStmt.execute("ALTER TABLE worker ALTER COLUMN name SET DATA TYPE TEXT");
            alterStmt.execute("ALTER TABLE billItem ALTER COLUMN itemName SET DATA TYPE TEXT");
            alterStmt.execute("ALTER TABLE patient ALTER COLUMN name SET DATA TYPE TEXT");
            alterStmt.execute("ALTER TABLE patient ALTER COLUMN gender SET DATA TYPE TEXT");
            alterStmt.execute("ALTER TABLE patient ALTER COLUMN phone SET DATA TYPE TEXT");
            alterStmt.execute("ALTER TABLE session ALTER COLUMN paied SET DATA TYPE TEXT");
            alterStmt.execute("ALTER TABLE sold ALTER COLUMN itemName SET DATA TYPE TEXT");
        } catch (SQLException ignored) {
        }
    }

    /**
     * Ensures that a staging SQLite database has all required tables and modern columns
     * (such as Users.userType, session.therapyPlanId, therapyPlan, and payment).
     */
    public static void normalizeSqliteSchema(Connection sqliteConn) throws SQLException {
        try (Statement stmt = sqliteConn.createStatement()) {
            // Rename legacy therapy_plan table if present
            if (tableExists(sqliteConn, "therapy_plan") && !tableExists(sqliteConn, "therapyPlan")) {
                stmt.execute("ALTER TABLE therapy_plan RENAME TO therapyPlan;");
            }

            createBaselineSqliteTables(stmt, true, true);

            // Add columns that might be missing in older schema versions
            if (!columnExists(sqliteConn, "Users", "userType")) {
                stmt.execute("ALTER TABLE Users ADD COLUMN userType TEXT DEFAULT 'Admin';");
            }
            stmt.execute("UPDATE Users SET userType = 'Admin' WHERE userType IS NULL OR TRIM(userType) = '';");

            if (!columnExists(sqliteConn, "session", "therapyPlanId")) {
                stmt.execute("ALTER TABLE session ADD COLUMN therapyPlanId INTEGER;");
            }
        }
    }

    private static void createBaselineSqliteTables(Statement stmt, boolean includeTherapyPlanIdInSession, boolean includeUserTypeInUsers) throws SQLException {
        stmt.execute("""
                CREATE TABLE IF NOT EXISTS worker (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    name TEXT NOT NULL,
                    birthDate TEXT,
                    birthPlace TEXT,
                    phoneNumber TEXT,
                    identityCardNumber TEXT NOT NULL,
                    function TEXT NOT NULL,
                    famillySituation TEXT
                );
                """);
        stmt.execute("""
                CREATE TABLE IF NOT EXISTS paymentCheck (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    workerId INTEGER NOT NULL,
                    paymentDate TEXT,
                    paidAmount REAL,
                    note TEXT
                );
                """);
        stmt.execute("""
                CREATE TABLE IF NOT EXISTS bill (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    billDate TEXT,
                    totalCost REAL
                );
                """);
        stmt.execute("""
                CREATE TABLE IF NOT EXISTS billItem (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    billId INTEGER NOT NULL,
                    itemName TEXT,
                    quantity INTEGER,
                    unitPrice REAL
                );
                """);
        if (includeUserTypeInUsers) {
            stmt.execute("""
                    CREATE TABLE IF NOT EXISTS Users (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        fullName TEXT NOT NULL,
                        userName TEXT NOT NULL,
                        passWord TEXT NOT NULL,
                        userType TEXT
                    );
                    """);
        } else {
            stmt.execute("""
                    CREATE TABLE IF NOT EXISTS Users (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        fullName TEXT NOT NULL,
                        userName TEXT NOT NULL,
                        passWord TEXT NOT NULL
                    );
                    """);
        }
        stmt.execute("""
                CREATE TABLE IF NOT EXISTS patient (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    name TEXT NOT NULL,
                    gender TEXT,
                    phone TEXT,
                    totalCost REAL,
                    totalPaid REAL
                );
                """);
        stmt.execute("""
                CREATE TABLE IF NOT EXISTS therapyPlan (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    patientId INTEGER,
                    date TEXT,
                    cost REAL
                );
                """);
        if (includeTherapyPlanIdInSession) {
            stmt.execute("""
                    CREATE TABLE IF NOT EXISTS session (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        patientId INTEGER,
                        therapyPlanId INTEGER,
                        sessionDate TEXT,
                        treatment TEXT,
                        paied TEXT,
                        cost REAL,
                        paidAmount REAL
                    );
                    """);
        } else {
            stmt.execute("""
                    CREATE TABLE IF NOT EXISTS session (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        patientId INTEGER,
                        sessionDate TEXT,
                        treatment TEXT,
                        paied TEXT,
                        cost REAL,
                        paidAmount REAL
                    );
                    """);
        }
        stmt.execute("""
                CREATE TABLE IF NOT EXISTS sold (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    itemName TEXT,
                    soldDate TEXT,
                    soldPrice REAL,
                    quantity REAL
                );
                """);
        stmt.execute("""
                CREATE TABLE IF NOT EXISTS payment (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    patientId INTEGER NOT NULL,
                    sessionId INTEGER,
                    paymentDate TEXT,
                    createdAt TEXT,
                    amount REAL NOT NULL,
                    paymentType TEXT NOT NULL,
                    reversalOf INTEGER,
                    reason TEXT,
                    createdBy TEXT NOT NULL
                );
                """);
    }

    private static boolean hasAnyRecognizedClinicTable(Connection sqliteConn) throws SQLException {
        for (String table : TABLES) {
            if (tableExists(sqliteConn, table)) {
                return true;
            }
        }
        return tableExists(sqliteConn, "therapy_plan");
    }

    private static boolean tableExists(Connection conn, String tableName) throws SQLException {
        String sql = "SELECT name FROM sqlite_master WHERE type='table' AND LOWER(name) = LOWER(?)";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, tableName);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    private static boolean columnExists(Connection conn, String tableName, String columnName) throws SQLException {
        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("PRAGMA table_info(" + tableName + ")")) {
            while (rs.next()) {
                if (columnName.equalsIgnoreCase(rs.getString("name"))) {
                    return true;
                }
            }
        }
        return false;
    }

    private static void migrateWorkers(Connection src, Connection dest) throws SQLException {
        String select = "SELECT id, name, birthDate, birthPlace, phoneNumber, identityCardNumber, function, famillySituation FROM worker";
        String insert = "INSERT INTO worker (id, name, birthDate, birthPlace, phoneNumber, identityCardNumber, function, famillySituation) VALUES (?, ?, ?, ?, ?, ?, ?, ?)";
        try (Statement s = src.createStatement();
             ResultSet rs = s.executeQuery(select);
             PreparedStatement ps = dest.prepareStatement(insert)) {
            while (rs.next()) {
                ps.setInt(1, rs.getInt("id"));
                ps.setString(2, rs.getString("name") != null ? rs.getString("name") : "");
                ps.setString(3, rs.getString("birthDate"));
                ps.setString(4, rs.getString("birthPlace"));
                ps.setString(5, rs.getString("phoneNumber"));
                ps.setString(6, rs.getString("identityCardNumber") != null ? rs.getString("identityCardNumber") : "");
                ps.setString(7, rs.getString("function") != null ? rs.getString("function") : "");
                ps.setString(8, rs.getString("famillySituation"));
                ps.addBatch();
            }
            ps.executeBatch();
        }
    }

    private static void migratePaymentChecks(Connection src, Connection dest, OrphanReport report) throws SQLException {
        Set<Integer> workerIds = loadIds(dest, "worker");
        String select = "SELECT id, workerId, paymentDate, paidAmount, note FROM paymentCheck";
        String insert = "INSERT INTO paymentCheck (id, workerId, paymentDate, paidAmount, note) VALUES (?, ?, ?, ?, ?)";
        try (Statement s = src.createStatement();
             ResultSet rs = s.executeQuery(select);
             PreparedStatement ps = dest.prepareStatement(insert)) {
            while (rs.next()) {
                int id = rs.getInt("id");
                Integer workerId = getNullableInt(rs, "workerId");
                if (workerId == null || !workerIds.contains(workerId)) {
                    report.skip("paymentCheck", id, "employé introuvable (workerId=" + workerId + "), date="
                            + rs.getString("paymentDate") + ", montant=" + rs.getDouble("paidAmount"));
                    continue;
                }
                ps.setInt(1, id);
                ps.setInt(2, workerId);
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

    private static void migrateBillItems(Connection src, Connection dest, OrphanReport report) throws SQLException {
        Set<Integer> billIds = loadIds(dest, "bill");
        String select = "SELECT id, billId, itemName, quantity, unitPrice FROM billItem";
        String insert = "INSERT INTO billItem (id, billId, itemName, quantity, unitPrice) VALUES (?, ?, ?, ?, ?)";
        try (Statement s = src.createStatement();
             ResultSet rs = s.executeQuery(select);
             PreparedStatement ps = dest.prepareStatement(insert)) {
            while (rs.next()) {
                int id = rs.getInt("id");
                Integer billId = getNullableInt(rs, "billId");
                if (billId == null || !billIds.contains(billId)) {
                    report.skip("billItem", id, "facture introuvable (billId=" + billId + "), article="
                            + rs.getString("itemName") + ", qté=" + rs.getInt("quantity")
                            + ", prix=" + rs.getDouble("unitPrice"));
                    continue;
                }
                ps.setInt(1, id);
                ps.setInt(2, billId);
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
                ps.setString(2, rs.getString("fullName") != null ? rs.getString("fullName") : "Administrateur");
                ps.setString(3, rs.getString("userName") != null ? rs.getString("userName") : "admin");
                ps.setString(4, rs.getString("passWord") != null ? rs.getString("passWord") : "admin123");
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
                ps.setString(2, rs.getString("name") != null ? rs.getString("name") : "Patient");
                ps.setString(3, rs.getString("gender"));
                ps.setString(4, rs.getString("phone"));
                ps.setDouble(5, rs.getDouble("totalCost"));
                ps.setDouble(6, rs.getDouble("totalPaid"));
                ps.addBatch();
            }
            ps.executeBatch();
        }
    }

    private static void migrateTherapyPlans(Connection src, Connection dest, OrphanReport report) throws SQLException {
        Set<Integer> patientIds = loadIds(dest, "patient");
        String select = "SELECT id, patientId, date, cost FROM therapyPlan";
        String insert = "INSERT INTO therapyPlan (id, patientId, date, cost) VALUES (?, ?, ?, ?)";
        try (Statement s = src.createStatement();
             ResultSet rs = s.executeQuery(select);
             PreparedStatement ps = dest.prepareStatement(insert)) {
            while (rs.next()) {
                int id = rs.getInt("id");
                Integer patientId = getNullableInt(rs, "patientId");
                if (patientId != null && !patientIds.contains(patientId)) {
                    report.skip("therapyPlan", id, "patient introuvable (patientId=" + patientId + "), date="
                            + rs.getString("date") + ", coût=" + rs.getDouble("cost"));
                    continue;
                }
                ps.setInt(1, id);
                if (patientId == null) {
                    ps.setNull(2, Types.INTEGER);
                } else {
                    ps.setInt(2, patientId);
                }
                ps.setString(3, rs.getString("date"));
                ps.setDouble(4, rs.getDouble("cost"));
                ps.addBatch();
            }
            ps.executeBatch();
        }
    }

    private static void migrateSessions(Connection src, Connection dest, OrphanReport report) throws SQLException {
        Set<Integer> patientIds = loadIds(dest, "patient");
        Set<Integer> planIds = loadIds(dest, "therapyPlan");
        String select = "SELECT id, patientId, therapyPlanId, sessionDate, treatment, paied, cost, paidAmount FROM session";
        String insert = "INSERT INTO session (id, patientId, therapyPlanId, sessionDate, treatment, paied, cost, paidAmount) VALUES (?, ?, ?, ?, ?, ?, ?, ?)";
        try (Statement s = src.createStatement();
             ResultSet rs = s.executeQuery(select);
             PreparedStatement ps = dest.prepareStatement(insert)) {
            while (rs.next()) {
                int id = rs.getInt("id");
                Integer patientId = getNullableInt(rs, "patientId");
                Integer planId = getNullableInt(rs, "therapyPlanId");
                if (planId != null && planId <= 0) {
                    planId = null;
                }
                String details = "date=" + rs.getString("sessionDate") + ", coût=" + rs.getDouble("cost")
                        + ", payé=" + rs.getDouble("paidAmount");
                if (patientId != null && !patientIds.contains(patientId)) {
                    report.skip("session", id, "patient introuvable (patientId=" + patientId + "), " + details);
                    continue;
                }
                if (planId != null && !planIds.contains(planId)) {
                    report.skip("session", id, "forfait introuvable (therapyPlanId=" + planId + "), patientId="
                            + patientId + ", " + details);
                    continue;
                }
                ps.setInt(1, id);
                if (patientId == null) {
                    ps.setNull(2, Types.INTEGER);
                } else {
                    ps.setInt(2, patientId);
                }
                if (planId == null) {
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

    private static void migratePaymentsIfPresent(Connection src, Connection dest, OrphanReport report) throws SQLException {
        if (!tableExists(src, "payment")) {
            return;
        }
        Set<Integer> patientIds = loadIds(dest, "patient");
        Set<Integer> sessionIds = loadIds(dest, "session");
        Set<Integer> insertedPaymentIds = new HashSet<>();
        // Ordered by id so that a reversal's original payment is always inserted first
        String select = "SELECT id, patientId, sessionId, paymentDate, createdAt, amount, paymentType, reversalOf, reason, createdBy FROM payment ORDER BY id";
        String insert = "INSERT INTO payment (id, patientId, sessionId, paymentDate, createdAt, amount, paymentType, reversalOf, reason, createdBy) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        try (Statement s = src.createStatement();
             ResultSet rs = s.executeQuery(select);
             PreparedStatement ps = dest.prepareStatement(insert)) {
            while (rs.next()) {
                double amount = rs.getDouble("amount");
                if (Math.abs(amount) < 0.001) continue;
                int id = rs.getInt("id");
                Integer patientId = getNullableInt(rs, "patientId");
                if (patientId == null || !patientIds.contains(patientId)) {
                    report.skip("payment", id, "patient introuvable (patientId=" + patientId + "), date="
                            + rs.getString("paymentDate") + ", montant=" + amount);
                    continue;
                }
                ps.setInt(1, id);
                ps.setInt(2, patientId);
                int sessionId = rs.getInt("sessionId");
                if (rs.wasNull() || sessionId <= 0) {
                    ps.setNull(3, Types.INTEGER);
                } else if (!sessionIds.contains(sessionId)) {
                    report.detach("payment", id, "séance introuvable (sessionId=" + sessionId + ") — lien supprimé");
                    ps.setNull(3, Types.INTEGER);
                } else {
                    ps.setInt(3, sessionId);
                }
                String payDate = rs.getString("paymentDate");
                String cleanDate = (payDate != null && payDate.length() >= 10) ? payDate.substring(0, 10) : java.time.LocalDate.now().toString();
                ps.setDate(4, java.sql.Date.valueOf(cleanDate));
                String createdAt = rs.getString("createdAt");
                if (createdAt != null && createdAt.length() >= 19) {
                    ps.setTimestamp(5, Timestamp.valueOf(createdAt.substring(0, 19).replace('T', ' ')));
                } else {
                    ps.setTimestamp(5, Timestamp.valueOf(cleanDate + " 12:00:00"));
                }
                ps.setBigDecimal(6, java.math.BigDecimal.valueOf(amount));
                ps.setString(7, rs.getString("paymentType") != null ? rs.getString("paymentType") : "SESSION");
                int revOf = rs.getInt("reversalOf");
                if (rs.wasNull() || revOf <= 0) {
                    ps.setNull(8, Types.INTEGER);
                } else if (!insertedPaymentIds.contains(revOf)) {
                    report.detach("payment", id, "paiement d'origine introuvable (reversalOf=" + revOf + ") — lien supprimé");
                    ps.setNull(8, Types.INTEGER);
                } else {
                    ps.setInt(8, revOf);
                }
                ps.setString(9, rs.getString("reason"));
                ps.setString(10, rs.getString("createdBy") != null ? rs.getString("createdBy") : "MIGRATION");
                ps.addBatch();
                insertedPaymentIds.add(id);
            }
            ps.executeBatch();
        }
    }

    private static void ensureAdminUserExists(Connection h2Conn) throws SQLException {
        try (Statement stmt = h2Conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM Users")) {
            if (rs.next() && rs.getInt(1) == 0) {
                stmt.execute("INSERT INTO Users (fullName, userName, passWord, userType) VALUES ('Administrateur', 'admin', 'admin123', 'Admin')");
            } else {
                stmt.execute("UPDATE Users SET userType = 'Admin' WHERE userType IS NULL OR TRIM(userType) = ''");
            }
        }
    }

    /**
     * Backfills historical session and therapyPlan payments into the payment ledger
     * and recalculates patient totalCost and totalPaid.
     */
    public static void backfillPaymentLedgerAndPatientTotals(Connection h2Conn) throws SQLException {
        String selectSessions = """
                SELECT s.id, s.patientId, s.sessionDate, s.paidAmount
                FROM session s
                WHERE s.paidAmount > 0
                  AND s.patientId IS NOT NULL
                  AND EXISTS (SELECT 1 FROM patient p WHERE p.id = s.patientId)
                  AND s.therapyPlanId IS NULL
                  AND NOT EXISTS (SELECT 1 FROM payment p WHERE p.sessionId = s.id)
                """;
        String insertPayment = """
                INSERT INTO payment (patientId, sessionId, paymentDate, createdAt, amount, paymentType, reason, createdBy)
                VALUES (?, ?, ?, ?, ?, ?, ?, 'MIGRATION')
                """;

        try (Statement s = h2Conn.createStatement();
             ResultSet rs = s.executeQuery(selectSessions);
             PreparedStatement ps = h2Conn.prepareStatement(insertPayment)) {
            while (rs.next()) {
                int sessionId = rs.getInt("id");
                int patientId = rs.getInt("patientId");
                String rawDate = rs.getString("sessionDate");
                double paidAmount = rs.getDouble("paidAmount");
                java.sql.Date sqlDate = parseSafeSqlDate(rawDate);
                ps.setInt(1, patientId);
                ps.setInt(2, sessionId);
                ps.setDate(3, sqlDate);
                ps.setTimestamp(4, Timestamp.valueOf(sqlDate.toLocalDate().atTime(12, 0)));
                ps.setBigDecimal(5, java.math.BigDecimal.valueOf(paidAmount));
                ps.setString(6, "SESSION");
                ps.setString(7, "Paiement initial séance #" + sessionId);
                ps.addBatch();
            }
            ps.executeBatch();
        }

        String selectPlans = """
                SELECT tp.id, tp.patientId, tp.date, tp.cost
                FROM therapyPlan tp
                WHERE tp.cost > 0
                  AND tp.patientId IS NOT NULL
                  AND EXISTS (SELECT 1 FROM patient p WHERE p.id = tp.patientId)
                  AND NOT EXISTS (SELECT 1 FROM payment p WHERE p.paymentType = 'THERAPY_PLAN' AND p.reason = 'Forfait thérapie #' || tp.id)
                """;
        try (Statement s = h2Conn.createStatement();
             ResultSet rs = s.executeQuery(selectPlans);
             PreparedStatement ps = h2Conn.prepareStatement(insertPayment)) {
            while (rs.next()) {
                int planId = rs.getInt("id");
                int patientId = rs.getInt("patientId");
                String rawDate = rs.getString("date");
                double cost = rs.getDouble("cost");
                java.sql.Date sqlDate = parseSafeSqlDate(rawDate);
                ps.setInt(1, patientId);
                ps.setNull(2, Types.INTEGER);
                ps.setDate(3, sqlDate);
                ps.setTimestamp(4, Timestamp.valueOf(sqlDate.toLocalDate().atTime(12, 0)));
                ps.setBigDecimal(5, java.math.BigDecimal.valueOf(cost));
                ps.setString(6, "THERAPY_PLAN");
                ps.setString(7, "Forfait thérapie #" + planId);
                ps.addBatch();
            }
            ps.executeBatch();
        }

        try (Statement stmt = h2Conn.createStatement()) {
            // Recalculate patient totalCost and totalPaid from sessions, therapy plans, and ledger
            stmt.execute("""
                UPDATE patient p
                SET totalCost = COALESCE((SELECT SUM(cost) FROM session s WHERE s.patientId = p.id AND s.therapyPlanId IS NULL), 0) +
                                COALESCE((SELECT SUM(cost) FROM therapyPlan tp WHERE tp.patientId = p.id), 0),
                    totalPaid = COALESCE((SELECT SUM(amount) FROM payment pay WHERE pay.patientId = p.id), 0);
            """);
        }
    }

    private static java.sql.Date parseSafeSqlDate(String rawDate) {
        if (rawDate != null && rawDate.trim().length() >= 10) {
            String sub = rawDate.trim().substring(0, 10);
            try {
                return java.sql.Date.valueOf(sub);
            } catch (IllegalArgumentException ignored) {
            }
        }
        return java.sql.Date.valueOf(java.time.LocalDate.now());
    }

    /**
     * Resets identity sequences on the 9 legacy tables to MAX(id) + 1.
     */
    public static void resetSequences(Connection conn) throws SQLException {
        resetSequencesForTables(conn, TABLES);
    }

    /**
     * Resets identity sequences on all 10 tables (including payment) to MAX(id) + 1.
     */
    public static void resetAllSequences(Connection conn) throws SQLException {
        resetSequencesForTables(conn, ALL_TABLES_WITH_PAYMENT);
    }

    private static void resetSequencesForTables(Connection conn, String[] tableList) throws SQLException {
        try (Statement stmt = conn.createStatement()) {
            for (String table : tableList) {
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
        verifyParity(sqliteConn, h2Conn, new OrphanReport());
    }

    /**
     * Verifies that H2 row counts equal the SQLite row counts minus the orphan rows
     * that were deliberately skipped (and recorded) during migration.
     */
    static void verifyParity(Connection sqliteConn, Connection h2Conn, OrphanReport report) throws SQLException {
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

                int skipped = report.skipped(table);
                if (c1 - skipped != c2) {
                    throw new IllegalStateException("Parity check failed for table [" + table +
                            "]: SQLite has " + c1 + " records (" + skipped + " orphan(s) skipped), but H2 has "
                            + c2 + " records.");
                }
                System.out.println("   ✔ Parity confirmed for " + table + ": " + c2 + " rows"
                        + (skipped > 0 ? " (" + skipped + " orphan row(s) skipped)" : ""));
            }
        }
    }

    /**
     * Splits a SQL script into individual statements while respecting single-quoted strings,
     * double-quoted identifiers, backtick identifiers, line comments (--), and block comments.
     */
    public static List<String> splitSqlStatements(String script) {
        List<String> statements = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        int len = script.length();
        boolean inSingleQuote = false;
        boolean inDoubleQuote = false;
        boolean inBacktick = false;

        for (int i = 0; i < len; i++) {
            char c = script.charAt(i);
            char next = (i + 1 < len) ? script.charAt(i + 1) : '\0';

            if (!inSingleQuote && !inDoubleQuote && !inBacktick) {
                // Line comment --
                if (c == '-' && next == '-') {
                    i += 2;
                    while (i < len && script.charAt(i) != '\n') {
                        i++;
                    }
                    continue;
                }
                // Block comment /* ... */
                if (c == '/' && next == '*') {
                    i += 2;
                    while (i + 1 < len && !(script.charAt(i) == '*' && script.charAt(i + 1) == '/')) {
                        i++;
                    }
                    i++; // skip '/'
                    continue;
                }
                if (c == ';') {
                    String stmt = current.toString().trim();
                    if (!stmt.isEmpty()) {
                        statements.add(stmt);
                    }
                    current.setLength(0);
                    continue;
                }
            }

            if (c == '\'' && !inDoubleQuote && !inBacktick) {
                if (inSingleQuote && next == '\'') {
                    current.append("''");
                    i++;
                    continue;
                } else if (inSingleQuote && i > 0 && script.charAt(i - 1) == '\\') {
                    // MySQL escaped quote \' -> convert to standard SQL ''
                    current.setCharAt(current.length() - 1, '\'');
                    current.append('\'');
                    continue;
                }
                inSingleQuote = !inSingleQuote;
            } else if (c == '"' && !inSingleQuote && !inBacktick) {
                inDoubleQuote = !inDoubleQuote;
            } else if (c == '`' && !inSingleQuote && !inDoubleQuote) {
                inBacktick = !inBacktick;
            }

            current.append(c);
        }

        String tail = current.toString().trim();
        if (!tail.isEmpty()) {
            statements.add(tail);
        }
        return statements;
    }

    private static String normalizeSqlStatementForSqlite(String raw) {
        String s = raw.trim();
        if (s.isEmpty()) return null;

        String upper = s.toUpperCase(Locale.ROOT);

        // Ignore transaction control, engine pragmas, and internal sequence tables
        if (upper.startsWith("BEGIN")
                || upper.startsWith("START TRANSACTION")
                || upper.startsWith("COMMIT")
                || upper.startsWith("END")
                || upper.startsWith("ROLLBACK")
                || upper.startsWith("PRAGMA")
                || upper.startsWith("SET ")
                || upper.startsWith("LOCK TABLES")
                || upper.startsWith("UNLOCK TABLES")
                || upper.startsWith("CREATE USER")
                || upper.startsWith("CREATE SEQUENCE")
                || upper.startsWith("CREATE INDEX")
                || upper.startsWith("CREATE UNIQUE INDEX")
                || upper.startsWith("ALTER TABLE")
                || upper.contains("SQLITE_SEQUENCE")) {
            return null;
        }

        // Normalize H2 or schema prefixes and legacy typos
        s = s.replaceAll("(?i)\"PUBLIC\"\\.", "")
                .replaceAll("(?i)\\bPUBLIC\\.", "")
                .replaceAll("(?i)\\bCREATE\\s+(?:CACHED|MEMORY)\\s+TABLE\\b", "CREATE TABLE")
                .replaceAll("(?i)\\bON\\s+DELETE\\s+CASADE\\b", "ON DELETE CASCADE")
                .replaceAll("(?i)\\btherapy_plan\\b", "therapyPlan")
                .replaceAll("(?i)\\bGENERATED\\s+BY\\s+DEFAULT\\s+AS\\s+IDENTITY\\b", "");

        if (upper.startsWith("CREATE TABLE")) {
            // Strip MySQL trailing table options after closing parenthesis, e.g., ENGINE=InnoDB ...
            int lastParen = s.lastIndexOf(')');
            if (lastParen > 0 && lastParen < s.length() - 1) {
                s = s.substring(0, lastParen + 1);
            }
            s = s.replaceAll("(?i)\\bAUTO_INCREMENT\\b", "AUTOINCREMENT");
        }

        return s;
    }

    private static String extractFirstTupleContent(String valuesPart) {
        int depth = 0;
        boolean inQuote = false;
        int start = -1;
        for (int i = 0; i < valuesPart.length(); i++) {
            char c = valuesPart.charAt(i);
            if (c == '\'') {
                inQuote = !inQuote;
            } else if (!inQuote) {
                if (c == '(') {
                    if (depth == 0) start = i + 1;
                    depth++;
                } else if (c == ')') {
                    depth--;
                    if (depth == 0 && start >= 0) {
                        return valuesPart.substring(start, i);
                    }
                }
            }
        }
        return valuesPart;
    }

    private static int countTupleValues(String tupleContent) {
        if (tupleContent == null || tupleContent.trim().isEmpty()) return 0;
        int count = 1;
        boolean inSingleQuote = false;
        boolean inDoubleQuote = false;
        int parenDepth = 0;
        for (int i = 0; i < tupleContent.length(); i++) {
            char c = tupleContent.charAt(i);
            if (c == '\'' && !inDoubleQuote) {
                inSingleQuote = !inSingleQuote;
            } else if (c == '"' && !inSingleQuote) {
                inDoubleQuote = !inDoubleQuote;
            } else if (!inSingleQuote && !inDoubleQuote) {
                if (c == '(') parenDepth++;
                else if (c == ')') parenDepth--;
                else if (c == ',' && parenDepth == 0) count++;
            }
        }
        return count;
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
