package network;

import config.AppConfig;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.nio.charset.StandardCharsets;

/**
 * Background UDP listener on the Host machine that responds to LAN discovery probes
 * from client machines with host connection parameters.
 */
public class LanDiscoveryHost {

    private static Thread listenerThread;
    private static volatile boolean running = false;
    private static DatagramSocket socket;

    public static synchronized void start() {
        if (running) return;
        running = true;

        listenerThread = new Thread(() -> {
            try {
                socket = new DatagramSocket(LanDiscoveryConstants.DISCOVERY_PORT);
                byte[] buffer = new byte[1024];
                System.out.println("📡 LAN Discovery Host beacon listening on UDP port " + LanDiscoveryConstants.DISCOVERY_PORT);

                while (running) {
                    DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                    socket.receive(packet);

                    String message = new String(packet.getData(), 0, packet.getLength(), StandardCharsets.UTF_8).trim();
                    if (LanDiscoveryConstants.PROBE_MESSAGE.equals(message)) {
                        String response = LanDiscoveryConstants.RESPONSE_PREFIX + AppConfig.getDbPort() + ":" + AppConfig.APP_VERSION;
                        byte[] responseBytes = response.getBytes(StandardCharsets.UTF_8);

                        DatagramPacket reply = new DatagramPacket(
                                responseBytes,
                                responseBytes.length,
                                packet.getAddress(),
                                packet.getPort()
                        );
                        socket.send(reply);
                        System.out.println("   Replied to discovery probe from " + packet.getAddress().getHostAddress());
                    }
                }
            } catch (Exception e) {
                if (running) {
                    System.err.println("⚠️ LAN Discovery Host error: " + e.getMessage());
                }
            } finally {
                stop();
            }
        }, "LanDiscoveryHost-Thread");

        listenerThread.setDaemon(true);
        listenerThread.start();
    }

    public static synchronized void stop() {
        running = false;
        if (socket != null && !socket.isClosed()) {
            socket.close();
            socket = null;
        }
        if (listenerThread != null) {
            listenerThread.interrupt();
            listenerThread = null;
        }
    }
}
