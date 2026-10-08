package config;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Properties;

/**
 * Manages persistent application configuration for workstation roles,
 * network parameters, and migration flags.
 */
public class AppConfig {

    public static final String ROLE_HOST = "HOST";
    public static final String ROLE_CLIENT = "CLIENT";
    public static final String APP_VERSION = "1.6";

    private static final String CLINIC_DIR = System.getProperty("user.home") + "/clinic";
    private static final String CONFIG_FILE = CLINIC_DIR + "/config.properties";

    private static final Properties properties = new Properties();
    private static boolean loaded = false;

    static {
        load();
    }

    public static synchronized void load() {
        try {
            Path clinicPath = Paths.get(CLINIC_DIR);
            if (!Files.exists(clinicPath)) {
                Files.createDirectories(clinicPath);
            }
            File file = new File(CONFIG_FILE);
            if (file.exists()) {
                try (InputStream in = new FileInputStream(file)) {
                    properties.load(in);
                }
            }
            loaded = true;
        } catch (Exception e) {
            System.err.println("⚠️ Failed to load config.properties: " + e.getMessage());
        }
    }

    public static synchronized void save() {
        try {
            Path clinicPath = Paths.get(CLINIC_DIR);
            if (!Files.exists(clinicPath)) {
                Files.createDirectories(clinicPath);
            }
            File file = new File(CONFIG_FILE);
            try (OutputStream out = new FileOutputStream(file)) {
                properties.store(out, "RedicalClinic Configuration");
            }
        } catch (Exception e) {
            System.err.println("❌ Failed to save config.properties: " + e.getMessage());
        }
    }

    public static synchronized boolean isConfigured() {
        String role = properties.getProperty("app.node_role");
        return role != null && !role.trim().isEmpty();
    }

    public static synchronized String getNodeRole() {
        return properties.getProperty("app.node_role", "");
    }

    public static synchronized void setNodeRole(String role) {
        properties.setProperty("app.node_role", role.trim().toUpperCase());
        save();
    }

    public static synchronized boolean isHost() {
        return ROLE_HOST.equalsIgnoreCase(getNodeRole());
    }

    public static synchronized boolean isClient() {
        return ROLE_CLIENT.equalsIgnoreCase(getNodeRole());
    }

    public static synchronized String getDbHost() {
        return properties.getProperty("db.host", "localhost");
    }

    public static synchronized void setDbHost(String host) {
        if (host != null) {
            properties.setProperty("db.host", host.trim());
            save();
        }
    }

    public static synchronized int getDbPort() {
        try {
            return Integer.parseInt(properties.getProperty("db.port", "9092"));
        } catch (NumberFormatException e) {
            return 9092;
        }
    }

    public static synchronized void setDbPort(int port) {
        properties.setProperty("db.port", String.valueOf(port));
        save();
    }

    public static synchronized boolean isMigrationCompleted() {
        return "true".equalsIgnoreCase(properties.getProperty("migration.completed", "false"));
    }

    public static synchronized void setMigrationCompleted(boolean completed) {
        properties.setProperty("migration.completed", String.valueOf(completed));
        save();
    }

    public static String getClinicDir() {
        return CLINIC_DIR;
    }
}
