package network;

import config.AppConfig;
import config.DatabaseConfig;
import config.OfflineSyncManager;
import javafx.application.Platform;

import java.net.InetSocketAddress;
import java.net.Socket;
import java.sql.Connection;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

// Periodically monitors connection health to the host database on client nodes.
// Requires 3 consecutive failed probes (~12s) before switching to offline replica mode
// to tolerate brief Wi-Fi blips, and requires 2 consecutive successful probes while
// offline before flushing changes to the Host and restoring online mode.
public class ConnectionMonitor {

    public static final int OFFLINE_FAILURE_THRESHOLD = 3;
    public static final int ONLINE_RECOVERY_THRESHOLD = 2;
    private static final int PERIODIC_REPLICA_REFRESH_TICKS = 30;

    private static ScheduledExecutorService scheduler;
    private static volatile boolean running = false;
    private static volatile boolean lastKnownStatus = true;
    private static int consecutiveFailures = 0;
    private static int consecutiveSuccesses = 0;
    private static int onlineTicksSinceSync = 0;

    public static synchronized void start(Consumer<Boolean> onStatusChanged) {
        start(onStatusChanged, null);
    }

    public static synchronized void start(Consumer<Boolean> onStatusChanged,
                                          Consumer<OfflineSyncManager.SyncResult> onSyncCompleted) {
        if (running || !AppConfig.isClient()) {
            return;
        }
        running = true;
        lastKnownStatus = !DatabaseConfig.isOfflineMode();
        consecutiveFailures = DatabaseConfig.isOfflineMode() ? OFFLINE_FAILURE_THRESHOLD : 0;
        consecutiveSuccesses = 0;
        onlineTicksSinceSync = 0;

        if (onStatusChanged != null) {
            boolean initialConnected = lastKnownStatus;
            Platform.runLater(() -> onStatusChanged.accept(initialConnected));
        }

        scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "ConnectionMonitor-Heartbeat");
            t.setDaemon(true);
            return t;
        });

        scheduler.scheduleWithFixedDelay(() -> {
            boolean hostReachable = probeHost();
            processProbeResult(hostReachable, status -> {
                if (onStatusChanged != null) {
                    Platform.runLater(() -> onStatusChanged.accept(status));
                }
            }, syncResult -> {
                if (onSyncCompleted != null) {
                    Platform.runLater(() -> onSyncCompleted.accept(syncResult));
                }
            });
        }, 3, 4, TimeUnit.SECONDS);
    }

    private static boolean probeHost() {
        String hostIp = AppConfig.getDbHost();
        int port = AppConfig.getDbPort();

        if (isTcpPortOpen(hostIp, port, 400) && DatabaseConfig.testHostConnection()) {
            return true;
        }

        LanDiscoveryClient.DiscoveryResult discovery = LanDiscoveryClient.discoverHost();
        if (discovery.success()) {
            return isTcpPortOpen(AppConfig.getDbHost(), AppConfig.getDbPort(), 400)
                    && DatabaseConfig.testHostConnection();
        }
        return false;
    }

    private static boolean isTcpPortOpen(String host, int port, int timeoutMs) {
        if (host == null || host.isBlank()) {
            return false;
        }
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(host.trim(), port), timeoutMs);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public static synchronized boolean processProbeResult(
            boolean hostReachable,
            Consumer<Boolean> onStatusChanged,
            Consumer<OfflineSyncManager.SyncResult> onSyncCompleted) {

        boolean currentlyOnline = lastKnownStatus && !DatabaseConfig.isOfflineMode();

        if (currentlyOnline) {
            if (hostReachable) {
                consecutiveFailures = 0;
                consecutiveSuccesses++;
                onlineTicksSinceSync++;
                if (onlineTicksSinceSync >= PERIODIC_REPLICA_REFRESH_TICKS) {
                    onlineTicksSinceSync = 0;
                    refreshReplicaQuietlyIfClean();
                }
                return true;
            } else {
                consecutiveFailures++;
                consecutiveSuccesses = 0;
                if (consecutiveFailures >= OFFLINE_FAILURE_THRESHOLD) {
                    lastKnownStatus = false;
                    if (OfflineSyncManager.hasInitializedReplica()) {
                        DatabaseConfig.setOfflineMode(true);
                    }
                    if (onStatusChanged != null) {
                        onStatusChanged.accept(false);
                    }
                    return false;
                }
                return true;
            }
        } else {
            if (!hostReachable) {
                consecutiveSuccesses = 0;
                consecutiveFailures++;
                return false;
            } else {
                consecutiveSuccesses++;
                consecutiveFailures = 0;
                if (consecutiveSuccesses >= ONLINE_RECOVERY_THRESHOLD) {
                    OfflineSyncManager.SyncResult syncResult = OfflineSyncManager.synchronizeWithHost();
                    if (syncResult.success()) {
                        DatabaseConfig.setOfflineMode(false);
                        lastKnownStatus = true;
                        consecutiveSuccesses = 0;
                        onlineTicksSinceSync = 0;
                        if (onStatusChanged != null) {
                            onStatusChanged.accept(true);
                        }
                        if (onSyncCompleted != null) {
                            onSyncCompleted.accept(syncResult);
                        }
                        return true;
                    }
                }
                return false;
            }
        }
    }

    private static void refreshReplicaQuietlyIfClean() {
        try (Connection hostConn = DatabaseConfig.getHostConnection();
             Connection replicaConn = DatabaseConfig.getReplicaConnection()) {
            if (OfflineSyncManager.countPendingChanges(replicaConn) == 0) {
                OfflineSyncManager.refreshReplicaFromHost(hostConn, replicaConn);
            }
        } catch (Exception ignored) {
        }
    }

    public static synchronized void resetStateForTest() {
        lastKnownStatus = !DatabaseConfig.isOfflineMode();
        consecutiveFailures = DatabaseConfig.isOfflineMode() ? OFFLINE_FAILURE_THRESHOLD : 0;
        consecutiveSuccesses = 0;
        onlineTicksSinceSync = 0;
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

    public static synchronized int getConsecutiveFailures() {
        return consecutiveFailures;
    }
}
