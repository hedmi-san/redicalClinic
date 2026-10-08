package network;

import config.AppConfig;
import config.DatabaseConfig;
import javafx.application.Platform;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * Periodically monitors connection health to the host database on client nodes
 * and notifies UI listeners of connection drops and restorations.
 */
public class ConnectionMonitor {

    private static ScheduledExecutorService scheduler;
    private static volatile boolean running = false;
    private static volatile boolean lastKnownStatus = true;

    /**
     * Starts the periodic heartbeat monitor on client nodes.
     * Host nodes do not require network polling.
     */
    public static synchronized void start(Consumer<Boolean> onStatusChanged) {
        if (running || !AppConfig.isClient()) {
            return;
        }
        running = true;

        scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "ConnectionMonitor-Heartbeat");
            t.setDaemon(true);
            return t;
        });

        // Run heartbeat every 4 seconds
        scheduler.scheduleWithFixedDelay(() -> {
            boolean currentStatus = DatabaseConfig.testConnection();
            if (currentStatus != lastKnownStatus) {
                lastKnownStatus = currentStatus;
                if (onStatusChanged != null) {
                    Platform.runLater(() -> onStatusChanged.accept(currentStatus));
                }
            }
        }, 3, 4, TimeUnit.SECONDS);
    }

    public static synchronized void stop() {
        running = false;
        if (scheduler != null && !scheduler.isShutdown()) {
            scheduler.shutdownNow();
            scheduler = null;
        }
    }

    public static boolean isConnected() {
        return lastKnownStatus;
    }
}
