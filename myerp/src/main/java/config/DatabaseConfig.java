package config;

import org.h2.jdbcx.JdbcConnectionPool;
import org.h2.tools.Server;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Manages database connections and the embedded H2 TCP Server lifecycle.
 * In HOST mode, launches an embedded H2 TCP server with AES encryption and remote LAN access.
 * In CLIENT mode, connects dynamically to the discovered host IP when online, or routes to the
 * local encrypted replica (client_replica.mv.db) when in offline mode.
 * Uses a persistent JdbcConnectionPool to eliminate TCP connection setup overhead across LAN.
 */
public class DatabaseConfig {

    private static final String DB_DIR = System.getProperty("user.home") + "/clinic";
    private static final String DB_NAME = "clinic";
    private static final String REPLICA_DB_NAME = "client_replica";

    private static final String DB_USER = "clinic_admin";
    private static final String FILE_PASSWORD = "NourElIslamClinicSecuredKey2026";
    private static final String USER_PASSWORD = "ClinicPasswordSecure9988";
    private static final String FULL_PASSWORD = FILE_PASSWORD + " " + USER_PASSWORD;

    private static Server tcpServer;
    private static volatile JdbcConnectionPool connectionPool;
    private static volatile String poolJdbcUrl;
    private static volatile boolean offlineMode = false;
    private static volatile boolean replicaSchemaInitialized = false;

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
     * Returns true if this client node is currently operating against the local offline replica.
     */
    public static boolean isOfflineMode() {
        return AppConfig.isClient() && offlineMode;
    }

    /**
     * Switches the client node between online mode (Host TCP server) and offline mode (local replica).
     * Automatically resets the active connection pool when the mode changes.
     */
    public static synchronized void setOfflineMode(boolean offline) {
        if (offlineMode != offline) {
            offlineMode = offline;
            resetPool();
            if (offline) {
                ensureReplicaInitialized();
            }
        }
    }

    private static synchronized void ensureReplicaInitialized() {
        if (!replicaSchemaInitialized) {
            try (Connection conn = DriverManager.getConnection(getReplicaJdbcUrl(), DB_USER, FULL_PASSWORD)) {
                DatabaseInitializer.initializeReplicaSchema(conn);
                replicaSchemaInitialized = true;
            } catch (SQLException e) {
                System.err.println("⚠️ Failed to initialize local replica schema: " + e.getMessage());
            }
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
                if (e.getCause() instanceof java.net.BindException || (e.getMessage() != null && e.getMessage().contains("Address already in use"))) {
                    System.out.println("ℹ️ H2 TCP Server is already active on port " + AppConfig.getDbPort() + ".");
                } else {
                    System.err.println("⚠️ Error starting H2 TCP server: " + e.getMessage());
                }
            }
        }
    }

    /**
     * Returns the JDBC URL for the Host TCP server (used on Host or when Client is online).
     */
    public static String getHostJdbcUrl() {
        int port = AppConfig.getDbPort();
        if (AppConfig.isHost()) {
            startHostServer();
            return "jdbc:h2:tcp://localhost:" + port + "/~/clinic/" + DB_NAME + ";CIPHER=AES;DATABASE_TO_UPPER=FALSE";
        } else {
            String hostIp = AppConfig.getDbHost();
            return "jdbc:h2:tcp://" + hostIp + ":" + port + "/~/clinic/" + DB_NAME + ";CIPHER=AES;DATABASE_TO_UPPER=FALSE";
        }
    }

    /**
     * Returns the JDBC URL for the Client's local encrypted replica database.
     * Uses DB_CLOSE_DELAY=-1 so the AES-encrypted MVStore stays open in memory across queries.
     */
    public static String getReplicaJdbcUrl() {
        String dir = (System.getProperty("user.home") + "/clinic").replace('\\', '/');
        return "jdbc:h2:file:" + dir + "/" + REPLICA_DB_NAME + ";CIPHER=AES;DATABASE_TO_UPPER=FALSE;DB_CLOSE_DELAY=-1";
    }

    /**
     * Builds the appropriate JDBC URL depending on whether this node is HOST, online CLIENT, or offline CLIENT.
     */
    public static String getJdbcUrl() {
        if (AppConfig.isClient() && offlineMode) {
            if (!replicaSchemaInitialized) {
                ensureReplicaInitialized();
            }
            return getReplicaJdbcUrl();
        }
        return getHostJdbcUrl();
    }

    /**
     * Obtains or creates the JdbcConnectionPool for the current configuration.
     * Uses a fast volatile read when the pool is already initialized for the active URL.
     */
    private static JdbcConnectionPool getPool() {
        JdbcConnectionPool existingPool = connectionPool;
        if (existingPool != null && AppConfig.isClient() && offlineMode && replicaSchemaInitialized) {
            return existingPool;
        }
        synchronized (DatabaseConfig.class) {
            String currentUrl = getJdbcUrl();
            if (connectionPool == null || !currentUrl.equals(poolJdbcUrl)) {
                if (connectionPool != null) {
                    try {
                        connectionPool.dispose();
                    } catch (Exception ignored) {
                    }
                }
                JdbcConnectionPool newPool = JdbcConnectionPool.create(currentUrl, DB_USER, FULL_PASSWORD);
                newPool.setMaxConnections(20);
                newPool.setLoginTimeout(2);
                poolJdbcUrl = currentUrl;
                connectionPool = newPool;
            }
            return connectionPool;
        }
    }

    /**
     * Opens and returns a connection from the active connection pool.
     */
    public static Connection getConnection() throws SQLException {
        return getPool().getConnection();
    }

    /**
     * Opens a direct JDBC connection to the Host TCP server regardless of whether offlineMode is active.
     */
    public static Connection getHostConnection() throws SQLException {
        DriverManager.setLoginTimeout(4);
        return DriverManager.getConnection(getHostJdbcUrl(), DB_USER, FULL_PASSWORD);
    }

    /**
     * Opens a direct JDBC connection to the Client's local replica database and ensures its schema is initialized.
     */
    public static Connection getReplicaConnection() throws SQLException {
        ensureReplicaInitialized();
        return DriverManager.getConnection(getReplicaJdbcUrl(), DB_USER, FULL_PASSWORD);
    }

    /**
     * Resets and disposes the connection pool.
     */
    public static synchronized void resetPool() {
        if (connectionPool != null) {
            try {
                connectionPool.dispose();
            } catch (Exception ignored) {
            }
            connectionPool = null;
            poolJdbcUrl = null;
        }
    }

    /**
     * Verifies whether the currently routed database is reachable.
     */
    public static boolean testConnection() {
        try (Connection conn = getConnection()) {
            return conn != null && !conn.isClosed();
        } catch (Exception e) {
            resetPool();
            return false;
        }
    }

    /**
     * Verifies whether the Host TCP server is directly reachable without disturbing an active offline replica pool.
     */
    public static boolean testHostConnection() {
        try (Connection conn = getHostConnection()) {
            return conn != null && !conn.isClosed();
        } catch (Exception e) {
            if (!isOfflineMode()) {
                resetPool();
            }
            return false;
        }
    }

    /** Returns the absolute path to the host database file. */
    public static String getDatabasePath() {
        return System.getProperty("user.home") + "/clinic/" + DB_NAME + ".mv.db";
    }

    /** Returns the absolute path to the client local replica database file. */
    public static String getReplicaDatabasePath() {
        return System.getProperty("user.home") + "/clinic/" + REPLICA_DB_NAME + ".mv.db";
    }

    /**
     * Cleanly closes all pooled connections, flushes H2, and stops the embedded TCP server
     * so that the underlying clinic.mv.db file handle is released on Windows for hot-swap operations.
     */
    public static synchronized void stopServerAndResetPool() {
        try (Connection conn = connectionPool != null ? connectionPool.getConnection() : null) {
            if (conn != null && !conn.isClosed()) {
                try (Statement stmt = conn.createStatement()) {
                    stmt.execute("SHUTDOWN");
                } catch (SQLException ignored) {
                }
            }
        } catch (Exception ignored) {
        }
        resetPool();
        if (tcpServer != null && tcpServer.isRunning(false)) {
            System.out.println("🛑 Stopping embedded H2 TCP Server for maintenance...");
            tcpServer.stop();
            tcpServer = null;
        }
    }

    /** Cleanly shuts down database server and connections on exit. */
    public static synchronized void shutdown() {
        if (AppConfig.isHost()) {
            BackupManager.createBackup();
        }
        network.LanDiscoveryHost.stop();
        resetPool();
        if (tcpServer != null && tcpServer.isRunning(false)) {
            System.out.println("🛑 Stopping embedded H2 TCP Server...");
            tcpServer.stop();
            tcpServer = null;
        }
    }
}
