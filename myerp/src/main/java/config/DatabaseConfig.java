package config;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.nio.file.Files;
import java.nio.file.Paths;

public class DatabaseConfig {

    // Store the DB in the user's home directory under a "clinic" folder
    private static final String DB_DIR = System.getProperty("user.home") + "/clinic";
    private static final String DB_FILE = DB_DIR + "/clinic.db";
    private static final String URL = "jdbc:sqlite:" + DB_FILE;

    // Ensure the directory exists the very first time this class is loaded
    static {
        try {
            Files.createDirectories(Paths.get(DB_DIR));
        } catch (Exception e) {
            throw new ExceptionInInitializerError("Could not create DB directory: " + e.getMessage());
        }
    }

    /**
     * Opens and returns a new SQLite connection.
     * Callers are responsible for closing the connection (use try-with-resources).
     */
    public static Connection getConnection() throws SQLException {
        return DriverManager.getConnection(URL);
    }

    /** Returns the absolute path to the database file (useful for logging). */
    public static String getDatabasePath() {
        return DB_FILE;
    }
}
