package com.myerp;

import java.nio.file.Files;
import java.nio.file.Path;
import util.StartupErrorReporter;

/**
 * Verifies that fatal startup errors are written to ~/clinic/logs/startup-error.log.
 * Uses a disposable temporary user.home; never touches the real clinic directory.
 */
public class StartupErrorReporterVerificationTest {

    public static void main(String[] args) throws Exception {
        String originalHome = System.getProperty("user.home");
        Path tempHome = Files.createTempDirectory("rc-startup-log-test");
        System.setProperty("user.home", tempHome.toString());
        try {
            RuntimeException error = new RuntimeException("Exception in Application start method",
                    new IllegalStateException("simulated startup failure"));

            Path logFile = StartupErrorReporter.writeLog(error);
            check(logFile != null, "log file path returned");
            check(logFile.equals(tempHome.resolve("clinic").resolve("logs")
                    .resolve(StartupErrorReporter.LOG_FILE_NAME)), "log written under user.home/clinic/logs");

            String content = Files.readString(logFile);
            check(content.contains("simulated startup failure"), "root cause message logged");
            check(content.contains("IllegalStateException"), "cause class logged");
            check(content.contains("Java: "), "environment details logged");

            StartupErrorReporter.writeLog(new RuntimeException("second failure"));
            String appended = Files.readString(logFile);
            check(appended.contains("simulated startup failure") && appended.contains("second failure"),
                    "subsequent errors appended, not overwritten");

            System.out.println("✅ StartupErrorReporterVerificationTest passed");
        } finally {
            System.setProperty("user.home", originalHome);
        }
    }

    private static void check(boolean condition, String description) {
        if (!condition) {
            throw new AssertionError("FAILED: " + description);
        }
        System.out.println("  ✔ " + description);
    }
}

