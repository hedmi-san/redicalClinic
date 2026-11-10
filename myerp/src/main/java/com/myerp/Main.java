package com.myerp;

import util.StartupErrorReporter;

/**
 * Bootstrap class to launch the JavaFX application.
 * This class does NOT extend javafx.application.Application.
 * This is a common workaround for JavaFX packaging to avoid "Main class extends
 * Application" issues.
 */
public class Main {
    public static void main(String[] args) {
        // Persist uncaught exceptions from any thread: the packaged app has no console.
        Thread.setDefaultUncaughtExceptionHandler((thread, error) -> {
            System.err.println("Exception in thread \"" + thread.getName() + "\"");
            error.printStackTrace();
            StartupErrorReporter.writeLog(error);
        });

        try {
            Myerp.main(args);
        } catch (Throwable error) {
            // Without this, the jpackage launcher only reports "Failed to launch JVM".
            StartupErrorReporter.reportFatal(error);
            System.exit(1);
        }
    }
}
