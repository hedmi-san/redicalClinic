package util;

import java.awt.Dimension;
import java.awt.GraphicsEnvironment;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import javax.swing.JOptionPane;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;

/**
 * Records fatal startup errors to {@code ~/clinic/logs/startup-error.log} and shows them to the user.
 * <p>
 * The packaged (jpackage) launcher has no console, so an uncaught exception during startup only
 * surfaces as a generic "Failed to launch JVM" dialog. This class makes the real cause visible.
 */
public final class StartupErrorReporter {

    public static final String LOG_FILE_NAME = "startup-error.log";

    private StartupErrorReporter() {
    }

    /**
     * Appends the error (with environment details) to the startup log.
     *
     * @return the log file written, or {@code null} if no location was writable
     */
    public static Path writeLog(Throwable error) {
        String entry = buildEntry(error);
        Path[] candidates = {
                Paths.get(System.getProperty("user.home"), "clinic", "logs"),
                Paths.get(System.getProperty("java.io.tmpdir"), "RedicalClinic")
        };
        for (Path dir : candidates) {
            try {
                Files.createDirectories(dir);
                Path logFile = dir.resolve(LOG_FILE_NAME);
                Files.writeString(logFile, entry, StandardCharsets.UTF_8,
                        StandardOpenOption.CREATE, StandardOpenOption.APPEND);
                return logFile;
            } catch (Exception ignored) {
                // try next location
            }
        }
        return null;
    }

    /**
     * Logs the error, prints it to stderr and shows a blocking dialog describing it.
     * Never throws.
     */
    public static void reportFatal(Throwable error) {
        try {
            error.printStackTrace();
            Path logFile = writeLog(error);
            if (GraphicsEnvironment.isHeadless()) {
                return;
            }
            Throwable root = rootCause(error);
            String message = "L'application n'a pas pu démarrer.\n\n"
                    + root.getClass().getName() + ": " + root.getMessage() + "\n\n"
                    + (logFile != null
                            ? "Détails enregistrés dans :\n" + logFile.toAbsolutePath()
                            : "Impossible d'écrire le fichier journal.");

            JTextArea details = new JTextArea(message + "\n\n" + stackTrace(error));
            details.setEditable(false);
            details.setCaretPosition(0);
            JScrollPane scroll = new JScrollPane(details);
            scroll.setPreferredSize(new Dimension(700, 400));
            JOptionPane.showMessageDialog(null, scroll,
                    "RedicalClinic — Erreur de démarrage", JOptionPane.ERROR_MESSAGE);
        } catch (Throwable ignored) {
            // Reporting must never mask the original failure
        }
    }

    static String buildEntry(Throwable error) {
        return "==== " + LocalDateTime.now() + " ====\n"
                + "Java: " + System.getProperty("java.version") + " (" + System.getProperty("java.vendor") + ")\n"
                + "OS: " + System.getProperty("os.name") + " " + System.getProperty("os.version")
                + " " + System.getProperty("os.arch") + "\n"
                + "User home: " + System.getProperty("user.home") + "\n"
                + "Working dir: " + System.getProperty("user.dir") + "\n"
                + stackTrace(error) + "\n";
    }

    private static Throwable rootCause(Throwable error) {
        Throwable current = error;
        while (current.getCause() != null && current.getCause() != current) {
            current = current.getCause();
        }
        return current;
    }

    private static String stackTrace(Throwable error) {
        StringWriter sw = new StringWriter();
        error.printStackTrace(new PrintWriter(sw));
        return sw.toString();
    }
}

