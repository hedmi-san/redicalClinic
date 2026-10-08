package config;

import org.h2.tools.Server;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

/**
 * Manages database connections and the embedded H2 TCP Server lifecycle.
 * In HOST mode, launches an embedded H2 TCP server with AES encryption and remote LAN access.
 * In CLIENT mode, connects dynamically to the discovered host IP.
 */
public class DatabaseConfig {

    private static final String DB_DIR = System.getProperty("user.home") + "/clinic";
    private static final String DB_NAME = "clinic";

    private static final String DB_USER = "clinic_admin";
    private static final String FILE_PASSWORD = "NourElIslamClinicSecuredKey2026";
    private static final String USER_PASSWORD = "ClinicPasswordSecure9988";
    private static final String FULL_PASSWORD = FILE_PASSWORD + " " + USER_PASSWORD;

    private static Server tcpServer;

    static {
        try {
            Files.createDirectories(Paths.get(DB_DIR));
            // Register JVM shutdown hook
            Runtime.getRuntime().addShutdownHook(new Thread(DatabaseConfig::shutdown));
        } catch (Exception e) {
            throw new ExceptionInInitializerError("Could not create DB directory: " + e.getMessage());
        }
    }

    /**
     * Starts the embedded H2 TCP server on the Host if not already active.
     */
    public static synchronized void startHostServer() {
        if (tcpServer == null || !tcpServer.isRunning(false)) {
            try {
                int port = AppConfig.getDbPort();
                tcpServer = Server.createTcpServer(
                        "-tcp",
                        "-tcpAllowOthers",
                        "-tcpPort", String.valueOf(port),
                        "-ifNotExists"
                ).start();
                System.out.println("🚀 Embedded H2 TCP Server started on port " + port + " (remote access enabled)");
                network.LanDiscoveryHost.start();
            } catch (SQLException e) {
                System.err.println("⚠️ Error starting H2 TCP server: " + e.getMessage());
            }
        }
    }

    /**
     * Builds the appropriate JDBC URL depending on whether this node is HOST or CLIENT.
     */
    public static String getJdbcUrl() {
        int port = AppConfig.getDbPort();
        if (AppConfig.isHost()) {
            startHostServer();
            // Host connects to its own server via localhost
            return "jdbc:h2:tcp://localhost:" + port + "/~/clinic/" + DB_NAME + ";CIPHER=AES;DATABASE_TO_UPPER=FALSE";
        } else {
            // Client connects to the discovered host IP
            String hostIp = AppConfig.getDbHost();
            return "jdbc:h2:tcp://" + hostIp + ":" + port + "/~/clinic/" + DB_NAME + ";CIPHER=AES;DATABASE_TO_UPPER=FALSE";
        }
    }

    /**
     * Opens and returns a connection to the database.
     */
    public static Connection getConnection() throws SQLException {
        DriverManager.setLoginTimeout(4);
        String url = getJdbcUrl();
        return DriverManager.getConnection(url, DB_USER, FULL_PASSWORD);
    }

    /**
     * Verifies whether the database is currently reachable.
     */
    public static boolean testConnection() {
        try (Connection conn = getConnection()) {
            return conn != null && !conn.isClosed();
        } catch (Exception e) {
            return false;
        }
    }

    /** Returns the absolute path to the database file directory. */
    public static String getDatabasePath() {
        return DB_DIR + "/" + DB_NAME + ".mv.db";
    }

    /** Cleanly shuts down database server and connections on exit. */
    public static synchronized void shutdown() {
        if (AppConfig.isHost()) {
            BackupManager.createBackup();
        }
        network.LanDiscoveryHost.stop();
        if (tcpServer != null && tcpServer.isRunning(false)) {
            System.out.println("🛑 Stopping embedded H2 TCP Server...");
            tcpServer.stop();
            tcpServer = null;
        }
    }
}
