package network;

import config.AppConfig;
import config.DatabaseConfig;

import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;

/**
 * Discovers the Host workstation IP address across the offline local WiFi LAN
 * by sending directed UDP discovery probes across all active network interfaces.
 */
public class LanDiscoveryClient {

    public static record DiscoveryResult(boolean success, String hostIp, int port, String version, String message) {}

    /**
     * Attempts fast connection using cached host IP first; if unreachable, runs multi-interface LAN discovery.
     */
    public static DiscoveryResult discoverHost() {
        // 1. Fast path: check cached IP if available
        String cachedIp = AppConfig.getDbHost();
        if (cachedIp != null && !cachedIp.trim().isEmpty() && !"localhost".equalsIgnoreCase(cachedIp)) {
            System.out.println("🔍 Testing cached host IP: " + cachedIp + "...");
            if (DatabaseConfig.testConnection()) {
                System.out.println("⚡ Reconnected instantly using cached host IP: " + cachedIp);
                return new DiscoveryResult(true, cachedIp, AppConfig.getDbPort(), AppConfig.APP_VERSION, "Connecté (Cache)");
            }
        }

        // 2. Slow path: broadcast UDP discovery probes
        return broadcastDiscovery();
    }

    /**
     * Broadcasts discovery probes across all local network interfaces.
     */
    public static DiscoveryResult broadcastDiscovery() {
        System.out.println("📡 Broadcasting LAN discovery probes on UDP port " + LanDiscoveryConstants.DISCOVERY_PORT + "...");
        byte[] probeData = LanDiscoveryConstants.PROBE_MESSAGE.getBytes(StandardCharsets.UTF_8);

        try (DatagramSocket socket = new DatagramSocket()) {
            socket.setBroadcast(true);
            socket.setSoTimeout(1800); // 1.8 second response timeout

            List<InetAddress> broadcastTargets = getBroadcastAddresses();
            for (InetAddress target : broadcastTargets) {
                try {
                    DatagramPacket packet = new DatagramPacket(
                            probeData,
                            probeData.length,
                            target,
                            LanDiscoveryConstants.DISCOVERY_PORT
                    );
                    socket.send(packet);
                } catch (Exception ignored) {
                }
            }

            // Listen for host response
            byte[] recvBuf = new byte[1024];
            DatagramPacket recvPacket = new DatagramPacket(recvBuf, recvBuf.length);

            try {
                socket.receive(recvPacket);
                String reply = new String(recvPacket.getData(), 0, recvPacket.getLength(), StandardCharsets.UTF_8).trim();

                if (reply.startsWith(LanDiscoveryConstants.RESPONSE_PREFIX)) {
                    String hostIp = recvPacket.getAddress().getHostAddress();
                    String details = reply.substring(LanDiscoveryConstants.RESPONSE_PREFIX.length());
                    String[] parts = details.split(":");
                    int port = parts.length > 0 ? Integer.parseInt(parts[0]) : 9092;
                    String version = parts.length > 1 ? parts[1] : AppConfig.APP_VERSION;

                    // Cache discovered parameters
                    AppConfig.setDbHost(hostIp);
                    AppConfig.setDbPort(port);

                    System.out.println("✅ Discovered Host at " + hostIp + ":" + port + " (version " + version + ")");
                    return new DiscoveryResult(true, hostIp, port, version, "Hôte découvert: " + hostIp);
                }
            } catch (SocketTimeoutException e) {
                System.out.println("⏳ Discovery probe timed out (no host response)");
            }

        } catch (Exception e) {
            System.err.println("⚠️ LAN Discovery Client error: " + e.getMessage());
            return new DiscoveryResult(false, null, 0, null, "Erreur de découverte réseau: " + e.getMessage());
        }

        return new DiscoveryResult(false, null, 0, null, "Aucun poste principal détecté sur le réseau WiFi");
    }

    /**
     * Gathers all valid broadcast addresses across non-loopback, non-virtual network interfaces.
     */
    private static List<InetAddress> getBroadcastAddresses() {
        List<InetAddress> list = new ArrayList<>();
        try {
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            while (interfaces != null && interfaces.hasMoreElements()) {
                NetworkInterface nif = interfaces.nextElement();
                if (!nif.isUp() || nif.isLoopback() || nif.isVirtual()) {
                    continue;
                }
                for (InterfaceAddress addr : nif.getInterfaceAddresses()) {
                    InetAddress bcast = addr.getBroadcast();
                    if (bcast != null) {
                        list.add(bcast);
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("⚠️ Error enumerating network interfaces: " + e.getMessage());
        }

        // Add standard 255.255.255.255 fallback
        try {
            list.add(InetAddress.getByName("255.255.255.255"));
        } catch (UnknownHostException ignored) {
        }

        return list;
    }
}
