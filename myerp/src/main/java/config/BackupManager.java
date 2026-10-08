package config;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Manages automated compressed database backups using H2 BACKUP TO.
 * Executes on the Host node at scheduled intervals and on application shutdown.
 */
public class BackupManager {

    private static final String BACKUP_DIR = System.getProperty("user.home") + "/clinic/backups";

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

            String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
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

    public static String getBackupDir() {
        return BACKUP_DIR;
    }
}
