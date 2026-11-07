package config;

import java.io.*;
import java.nio.file.*;
import java.security.MessageDigest;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Properties;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

/**
 * Manages automated compressed database backups, standalone .cni export/restore packages,
 * and legacy .sql / .db clean-replace imports with SHA-256 verification and pre-restore safety snapshots.
 */
public class BackupManager {

    private static final String BACKUP_DIR = System.getProperty("user.home") + "/clinic/backups";
    private static final String MANIFEST_ENTRY_NAME = "backup_manifest.properties";
    private static final String DB_ENTRY_NAME = "clinic.mv.db";
    private static final DateTimeFormatter DISPLAY_DATE_FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss");
    private static final DateTimeFormatter FILE_TIMESTAMP_FMT = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");

    /**
     * Structured metadata returned when inspecting a .cni, .sql, or .db file before restoration/import.
     */
    public record BackupInspection(
            String format,
            String clinicName,
            String appVersion,
            String timestamp,
            String sha256,
            int patientsCount,
            int sessionsCount,
            int billsCount,
            int usersCount,
            File sourceFile
    ) {
    }

    /**
     * Represents an existing backup file in ~/clinic/backups/ for the local backup history table.
     */
    public record LocalBackupItem(
            String fileName,
            String formattedDate,
            String formattedSize,
            Path filePath,
            long lastModifiedMillis
    ) {
    }

    /**
     * Creates an online compressed zip backup of the H2 database.
     */
    public static boolean createBackup() {
        if (!AppConfig.isHost()) {
            return false;
        }

        try {
            Path backupDirPath = Paths.get(BACKUP_DIR);
            if (!Files.exists(backupDirPath)) {
                Files.createDirectories(backupDirPath);
            }

            String timestamp = LocalDateTime.now().format(FILE_TIMESTAMP_FMT);
            String backupZipPath = BACKUP_DIR + "/clinic_backup_" + timestamp + ".zip";
            String sanitizedPath = backupZipPath.replace('\\', '/');

            try (Connection conn = DatabaseConfig.getConnection();
                 Statement stmt = conn.createStatement()) {
                stmt.execute("BACKUP TO '" + sanitizedPath + "'");
                System.out.println("💾 Automated H2 database backup created: " + backupZipPath);
                return true;
            }
        } catch (Exception e) {
            System.err.println("⚠️ Warning: Automated database backup failed: " + e.getMessage());
            return false;
        }
    }

    /**
     * Exports the active H2 database into a standalone .cni archive containing clinic.mv.db
     * and backup_manifest.properties with entity counts and SHA-256 integrity hash.
     */
    public static BackupInspection exportToCni(File destinationFile) throws Exception {
        if (!AppConfig.isHost()) {
            throw new IllegalStateException("L'exportation de la base de données n'est autorisée que sur le poste principal (Serveur).");
        }
        if (destinationFile == null) {
            throw new IllegalArgumentException("Aucun fichier de destination spécifié.");
        }

        Path tempDir = Files.createTempDirectory("cni_export_");
        Path tempH2Zip = tempDir.resolve("online_snapshot.zip");
        Path extractedMvDb = tempDir.resolve(DB_ENTRY_NAME);

        try {
            int patientsCount;
            int sessionsCount;
            int billsCount;
            int usersCount;

            // 1. Query live entity counts and trigger online H2 BACKUP TO temp zip
            try (Connection conn = DatabaseConfig.getConnection();
                 Statement stmt = conn.createStatement()) {
                patientsCount = queryTableCount(stmt, "patient");
                sessionsCount = queryTableCount(stmt, "session");
                billsCount = queryTableCount(stmt, "bill");
                usersCount = queryTableCount(stmt, "Users");

                String sanitizedZipPath = tempH2Zip.toAbsolutePath().toString().replace('\\', '/');
                stmt.execute("BACKUP TO '" + sanitizedZipPath + "'");
            }

            // 2. Extract clinic.mv.db from the H2 online backup zip
            extractMvDbFromZip(tempH2Zip, extractedMvDb);
            if (!Files.exists(extractedMvDb) || Files.size(extractedMvDb) == 0) {
                throw new IOException("Échec de l'extraction du cliché de base de données H2.");
            }

            // 3. Calculate SHA-256 digest of clinic.mv.db
            String sha256 = computeSha256(extractedMvDb);
            String isoTimestamp = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);

            // 4. Build backup_manifest.properties
            Properties manifest = new Properties();
            manifest.setProperty("clinic.name", "Cabinet Nour El Islam");
            manifest.setProperty("app.version", AppConfig.APP_VERSION);
            manifest.setProperty("backup.timestamp", isoTimestamp);
            manifest.setProperty("backup.sha256", sha256);
            manifest.setProperty("counts.patients", String.valueOf(patientsCount));
            manifest.setProperty("counts.sessions", String.valueOf(sessionsCount));
            manifest.setProperty("counts.bills", String.valueOf(billsCount));
            manifest.setProperty("counts.users", String.valueOf(usersCount));

            // 5. Package clinic.mv.db and backup_manifest.properties into destination .cni file
            if (destinationFile.getParentFile() != null) {
                Files.createDirectories(destinationFile.getParentFile().toPath());
            }

            try (ZipOutputStream zos = new ZipOutputStream(new BufferedOutputStream(new FileOutputStream(destinationFile)))) {
                // Write manifest entry
                ZipEntry manifestEntry = new ZipEntry(MANIFEST_ENTRY_NAME);
                zos.putNextEntry(manifestEntry);
                manifest.store(zos, "Cabinet Nour El Islam - Backup Manifest");
                zos.closeEntry();

                // Write clinic.mv.db entry
                ZipEntry dbEntry = new ZipEntry(DB_ENTRY_NAME);
                zos.putNextEntry(dbEntry);
                Files.copy(extractedMvDb, zos);
                zos.closeEntry();
            }

            System.out.println("✅ Export .cni créé avec succès : " + destinationFile.getAbsolutePath());
            return new BackupInspection(
                    "CNI",
                    "Cabinet Nour El Islam",
                    AppConfig.APP_VERSION,
                    isoTimestamp,
                    sha256,
                    patientsCount,
                    sessionsCount,
                    billsCount,
                    usersCount,
                    destinationFile
            );
        } finally {
            deleteDirectoryQuietly(tempDir);
        }
    }

    /**
     * Inspects and validates a .cni archive, .sql dump script, or .db SQLite file without modifying the active database.
     */
    public static BackupInspection inspectBackupFile(File file) throws Exception {
        if (file == null || !file.exists() || !file.isFile() || file.length() == 0) {
            throw new IllegalArgumentException("Le fichier sélectionné est introuvable ou vide.");
        }

        String nameLower = file.getName().toLowerCase(Locale.ROOT);
        if (nameLower.endsWith(".cni")) {
            return inspectCniArchive(file);
        } else if (nameLower.endsWith(".sql") || nameLower.endsWith(".db") || nameLower.endsWith(".sqlite") || nameLower.endsWith(".sqlite3")) {
            return inspectLegacySqlOrDb(file);
        } else {
            throw new IllegalArgumentException("Format non pris en charge. Veuillez sélectionner un fichier .cni, .sql ou .db.");
        }
    }

    private static BackupInspection inspectCniArchive(File cniFile) throws Exception {
        Path tempDir = Files.createTempDirectory("cni_inspect_");
        try {
            Path manifestPath = tempDir.resolve(MANIFEST_ENTRY_NAME);
            Path dbPath = tempDir.resolve(DB_ENTRY_NAME);
            extractCniContents(cniFile, manifestPath, dbPath);

            Properties props = new Properties();
            try (InputStream in = Files.newInputStream(manifestPath)) {
                props.load(in);
            }

            String expectedSha = props.getProperty("backup.sha256", "").trim();
            if (expectedSha.isEmpty()) {
                throw new IllegalArgumentException("Archive .cni invalide : empreinte SHA-256 absente du manifeste.");
            }

            String actualSha = computeSha256(dbPath);
            if (!expectedSha.equalsIgnoreCase(actualSha)) {
                throw new IllegalArgumentException("Archive .cni corrompue : la vérification d'intégrité SHA-256 a échoué.");
            }

            return new BackupInspection(
                    "CNI",
                    props.getProperty("clinic.name", "Cabinet Nour El Islam"),
                    props.getProperty("app.version", AppConfig.APP_VERSION),
                    props.getProperty("backup.timestamp", "Inconnu"),
                    actualSha,
                    parseIntSafe(props.getProperty("counts.patients"), 0),
                    parseIntSafe(props.getProperty("counts.sessions"), 0),
                    parseIntSafe(props.getProperty("counts.bills"), 0),
                    parseIntSafe(props.getProperty("counts.users"), 0),
                    cniFile
            );
        } finally {
            deleteDirectoryQuietly(tempDir);
        }
    }

    private static BackupInspection inspectLegacySqlOrDb(File file) throws Exception {
        Path stagedSqlite = DatabaseMigrator.stageLegacySqlOrDb(file);
        try {
            String sqliteUrl = "jdbc:sqlite:" + stagedSqlite.toAbsolutePath();
            try (Connection conn = DriverManager.getConnection(sqliteUrl);
                 Statement stmt = conn.createStatement()) {
                int patientsCount = queryTableCount(stmt, "patient");
                int sessionsCount = queryTableCount(stmt, "session");
                int billsCount = queryTableCount(stmt, "bill");
                int usersCount = queryTableCount(stmt, "Users");

                String format = file.getName().toLowerCase(Locale.ROOT).endsWith(".sql") ? "SQL" : "DB";
                String fileTime = LocalDateTime.ofInstant(
                        Instant.ofEpochMilli(file.lastModified()), ZoneId.systemDefault()
                ).format(DISPLAY_DATE_FMT);
                String sha256 = computeSha256(file.toPath());

                return new BackupInspection(
                        format,
                        "Base de données ancienne version (" + format + ")",
                        "Legacy -> " + AppConfig.APP_VERSION,
                        fileTime,
                        sha256,
                        patientsCount,
                        sessionsCount,
                        billsCount,
                        usersCount,
                        file
                );
            }
        } finally {
            try {
                Files.deleteIfExists(stagedSqlite);
            } catch (IOException ignored) {
            }
        }
    }

    /**
     * Creates a verified pre-restore safety snapshot of the active clinic.mv.db in ~/clinic/backups/
     * named clinic_pre_restore_<timestamp>.mv.db before any destructive restore or import operation.
     * Returns the Path to the snapshot, or null if no active database exists yet (e.g. clean PC).
     */
    public static Path createPreRestoreSafetySnapshot() throws Exception {
        Path activeDbPath = Paths.get(DatabaseConfig.getDatabasePath());
        if (!Files.exists(activeDbPath) || Files.size(activeDbPath) == 0) {
            return null;
        }

        Path backupDirPath = Paths.get(BACKUP_DIR);
        Files.createDirectories(backupDirPath);

        String timestamp = LocalDateTime.now().format(FILE_TIMESTAMP_FMT);
        Path snapshotPath = backupDirPath.resolve("clinic_pre_restore_" + timestamp + ".mv.db");

        // Try online snapshot via BACKUP TO first if the database engine is running
        Path tempZip = backupDirPath.resolve("temp_pre_restore_" + timestamp + ".zip");
        try {
            try (Connection conn = DatabaseConfig.getConnection();
                 Statement stmt = conn.createStatement()) {
                String sanitizedZip = tempZip.toAbsolutePath().toString().replace('\\', '/');
                stmt.execute("BACKUP TO '" + sanitizedZip + "'");
            }
            extractMvDbFromZip(tempZip, snapshotPath);
        } catch (Exception onlineEx) {
            // Fallback to raw file copy (e.g. if server is stopped or not yet started)
            Files.copy(activeDbPath, snapshotPath, StandardCopyOption.REPLACE_EXISTING);
        } finally {
            try {
                Files.deleteIfExists(tempZip);
            } catch (IOException ignored) {
            }
        }

        if (!Files.exists(snapshotPath) || Files.size(snapshotPath) == 0) {
            throw new IOException("Impossible de créer la copie de sécurité pré-restauration.");
        }

        System.out.println("🛡️ Pre-restore safety snapshot created: " + snapshotPath);
        return snapshotPath;
    }

    /**
     * Restores a .cni archive or imports a legacy .sql / .db file into the active clinic database.
     * Automatically creates a pre-restore safety snapshot and rolls back on failure.
     */
    public static BackupInspection restoreOrImportDatabase(File sourceFile) throws Exception {
        if (sourceFile == null || !sourceFile.exists()) {
            throw new IllegalArgumentException("Le fichier sélectionné est introuvable.");
        }
        String nameLower = sourceFile.getName().toLowerCase(Locale.ROOT);
        if (nameLower.endsWith(".cni")) {
            return restoreFromCni(sourceFile);
        } else if (nameLower.endsWith(".sql") || nameLower.endsWith(".db") || nameLower.endsWith(".sqlite") || nameLower.endsWith(".sqlite3")) {
            return importFromLegacySqlOrDb(sourceFile);
        } else {
            throw new IllegalArgumentException("Format non pris en charge : " + sourceFile.getName());
        }
    }

    /**
     * Atomically hot-swaps clinic.mv.db from a validated .cni archive with automatic rollback on failure.
     */
    public static BackupInspection restoreFromCni(File cniFile) throws Exception {
        Path tempDir = Files.createTempDirectory("cni_restore_");
        Path manifestPath = tempDir.resolve(MANIFEST_ENTRY_NAME);
        Path extractedDbPath = tempDir.resolve(DB_ENTRY_NAME);

        try {
            // 1. Validate .cni archive and verify SHA-256 before touching anything
            BackupInspection inspection = inspectCniArchive(cniFile);
            extractCniContents(cniFile, manifestPath, extractedDbPath);

            // 2. Create pre-restore safety snapshot (aborts immediately if snapshot fails)
            Path safetySnapshot = createPreRestoreSafetySnapshot();

            // 3. Stop H2 TCP server and dispose connection pool to release Windows file locks
            DatabaseConfig.stopServerAndResetPool();

            Path activeDbPath = Paths.get(DatabaseConfig.getDatabasePath());
            Files.createDirectories(activeDbPath.getParent());

            try {
                // 4. Atomic swap of clinic.mv.db
                Files.copy(extractedDbPath, activeDbPath, StandardCopyOption.REPLACE_EXISTING);

                // 5. Ensure node is configured as HOST and migration marked completed
                AppConfig.setNodeRole(AppConfig.ROLE_HOST);
                AppConfig.setMigrationCompleted(true);

                // 6. Restart server and initialize/verify schema
                DatabaseConfig.startHostServer();
                DatabaseInitializer.initializeDatabase();

                if (!DatabaseConfig.testConnection()) {
                    throw new IOException("La base de données restaurée n'a pas pu démarrer correctement.");
                }

                System.out.println("✅ Restauration .cni effectuée avec succès depuis : " + cniFile.getName());
                return inspection;
            } catch (Exception swapError) {
                System.err.println("❌ Erreur lors de la restauration .cni, annulation et retour à la copie de sécurité : " + swapError.getMessage());
                DatabaseConfig.stopServerAndResetPool();
                if (safetySnapshot != null && Files.exists(safetySnapshot)) {
                    Files.copy(safetySnapshot, activeDbPath, StandardCopyOption.REPLACE_EXISTING);
                }
                DatabaseConfig.startHostServer();
                throw new IOException("La restauration a échoué ; la base de données précédente a été rétablie automatiquement. Détail : " + swapError.getMessage(), swapError);
            }
        } finally {
            deleteDirectoryQuietly(tempDir);
        }
    }

    /**
     * Performs a clean-replace import from a legacy .sql dump or .db SQLite file into H2,
     * with pre-restore safety snapshot, payment ledger backfill, patient total recalculation,
     * and H2 identity sequence realignment.
     */
    public static BackupInspection importFromLegacySqlOrDb(File legacyFile) throws Exception {
        // 1. Validate and stage legacy .sql or .db into normalized temporary SQLite DB
        BackupInspection inspection = inspectLegacySqlOrDb(legacyFile);
        Path stagedSqlite = DatabaseMigrator.stageLegacySqlOrDb(legacyFile);

        try {
            // 2. Create pre-restore safety snapshot before modifying H2 database
            Path safetySnapshot = createPreRestoreSafetySnapshot();

            // 3. Ensure node is HOST and schema is initialized (without triggering legacy ~/clinic/clinic.db auto-migration)
            AppConfig.setNodeRole(AppConfig.ROLE_HOST);
            AppConfig.setMigrationCompleted(true);
            DatabaseInitializer.initializeDatabase();

            // 4. Perform transactional clean-replace migration into H2
            try (Connection h2Conn = DatabaseConfig.getConnection()) {
                DatabaseMigrator.cleanReplaceFromSqlite(stagedSqlite, h2Conn);
            } catch (Exception migrationError) {
                System.err.println("❌ Erreur lors de l'importation SQL/DB : " + migrationError.getMessage());
                if (safetySnapshot != null && Files.exists(safetySnapshot)) {
                    DatabaseConfig.stopServerAndResetPool();
                    Files.copy(safetySnapshot, Paths.get(DatabaseConfig.getDatabasePath()), StandardCopyOption.REPLACE_EXISTING);
                    DatabaseConfig.startHostServer();
                }
                throw new IOException("L'importation a échoué ; les données précédentes ont été préservées. Détail : " + migrationError.getMessage(), migrationError);
            }

            System.out.println("✅ Importation SQL/DB terminée avec succès depuis : " + legacyFile.getName());
            return inspection;
        } finally {
            try {
                Files.deleteIfExists(stagedSqlite);
            } catch (IOException ignored) {
            }
        }
    }

    /**
     * Lists all automated and pre-restore backups in ~/clinic/backups/ sorted newest first.
     */
    public static List<LocalBackupItem> listLocalBackups() {
        List<LocalBackupItem> items = new ArrayList<>();
        Path backupDir = Paths.get(BACKUP_DIR);
        if (!Files.exists(backupDir)) {
            return items;
        }

        try (Stream<Path> stream = Files.list(backupDir)) {
            stream.filter(Files::isRegularFile)
                    .filter(p -> {
                        String n = p.getFileName().toString().toLowerCase(Locale.ROOT);
                        return n.endsWith(".zip") || n.endsWith(".db") || n.endsWith(".cni");
                    })
                    .forEach(p -> {
                        try {
                            long lastMod = Files.getLastModifiedTime(p).toMillis();
                            long sizeBytes = Files.size(p);
                            String dateStr = LocalDateTime.ofInstant(
                                    Instant.ofEpochMilli(lastMod), ZoneId.systemDefault()
                            ).format(DISPLAY_DATE_FMT);
                            items.add(new LocalBackupItem(
                                    p.getFileName().toString(),
                                    dateStr,
                                    formatFileSize(sizeBytes),
                                    p,
                                    lastMod
                            ));
                        } catch (IOException ignored) {
                        }
                    });
        } catch (IOException e) {
            System.err.println("⚠️ Warning: Unable to list local backups: " + e.getMessage());
        }

        items.sort(Comparator.comparingLong(LocalBackupItem::lastModifiedMillis).reversed());
        return items;
    }

    /**
     * Copies a local backup file to an external destination (e.g. USB drive).
     */
    public static void copyBackupToExternal(Path sourceBackup, File destinationFile) throws IOException {
        if (sourceBackup == null || !Files.exists(sourceBackup)) {
            throw new FileNotFoundException("Le fichier de sauvegarde source est introuvable.");
        }
        if (destinationFile.getParentFile() != null) {
            Files.createDirectories(destinationFile.getParentFile().toPath());
        }
        Files.copy(sourceBackup, destinationFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
    }

    public static String getBackupDir() {
        return BACKUP_DIR;
    }

    private static void extractCniContents(File cniFile, Path manifestDest, Path dbDest) throws IOException {
        boolean foundManifest = false;
        boolean foundDb = false;

        try (ZipInputStream zis = new ZipInputStream(new BufferedInputStream(new FileInputStream(cniFile)))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                String entryName = Paths.get(entry.getName()).getFileName().toString();
                if (MANIFEST_ENTRY_NAME.equalsIgnoreCase(entryName)) {
                    Files.copy(zis, manifestDest, StandardCopyOption.REPLACE_EXISTING);
                    foundManifest = true;
                } else if (DB_ENTRY_NAME.equalsIgnoreCase(entryName)) {
                    Files.copy(zis, dbDest, StandardCopyOption.REPLACE_EXISTING);
                    foundDb = true;
                }
                zis.closeEntry();
            }
        } catch (Exception e) {
            throw new IllegalArgumentException("Le fichier sélectionné n'est pas une archive .cni valide.", e);
        }

        if (!foundManifest || !foundDb || !Files.exists(dbDest) || Files.size(dbDest) == 0) {
            throw new IllegalArgumentException("Archive .cni incomplète : manifeste ou base de données manquant.");
        }
    }

    private static void extractMvDbFromZip(Path zipPath, Path destMvDb) throws IOException {
        try (ZipInputStream zis = new ZipInputStream(new BufferedInputStream(Files.newInputStream(zipPath)))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                if (entry.getName().endsWith(".mv.db")) {
                    Files.copy(zis, destMvDb, StandardCopyOption.REPLACE_EXISTING);
                    zis.closeEntry();
                    return;
                }
                zis.closeEntry();
            }
        }
        throw new IOException("Aucun fichier .mv.db trouvé dans la sauvegarde temporaire.");
    }

    public static String computeSha256(Path filePath) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (InputStream in = new BufferedInputStream(Files.newInputStream(filePath))) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = in.read(buffer)) != -1) {
                digest.update(buffer, 0, read);
            }
        }
        byte[] hashBytes = digest.digest();
        StringBuilder sb = new StringBuilder(hashBytes.length * 2);
        for (byte b : hashBytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    private static int queryTableCount(Statement stmt, String tableName) {
        try (ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM " + tableName)) {
            if (rs.next()) {
                return rs.getInt(1);
            }
        } catch (Exception ignored) {
        }
        return 0;
    }

    private static int parseIntSafe(String val, int fallback) {
        if (val == null) return fallback;
        try {
            return Integer.parseInt(val.trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private static String formatFileSize(long bytes) {
        if (bytes < 1024) return bytes + " o";
        double kb = bytes / 1024.0;
        if (kb < 1024) return String.format(Locale.FRANCE, "%.1f Ko", kb);
        double mb = kb / 1024.0;
        return String.format(Locale.FRANCE, "%.2f Mo", mb);
    }

    private static void deleteDirectoryQuietly(Path dir) {
        if (dir == null || !Files.exists(dir)) return;
        try (Stream<Path> walk = Files.walk(dir)) {
            walk.sorted(Comparator.reverseOrder()).forEach(p -> {
                try {
                    Files.deleteIfExists(p);
                } catch (IOException ignored) {
                }
            });
        } catch (IOException ignored) {
        }
    }
}
