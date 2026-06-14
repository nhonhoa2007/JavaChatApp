package org.example.server.network;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketTimeoutException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

// UDP Relay server — forward audio giữa 2 client qua internet.
// Client gửi packet format: [8 bytes callId ASCII][payload audio]
// Server dùng callId tra cứu cặp client, forward payload cho bên còn lại.
public class UdpRelayService {

    private static final int RELAY_PORT = 8889;
    private static final int CALL_ID_LENGTH = 8;
    private static final int MAX_PACKET_SIZE = 2048;

    private final DatagramSocket relaySocket;
    private final Thread relayThread;
    private volatile boolean running = true;

    // callId → EndpointPair (2 địa chỉ UDP)
    private final Map<String, EndpointPair> activeSessions = new ConcurrentHashMap<>();

    public UdpRelayService() throws Exception {
        relaySocket = new DatagramSocket(RELAY_PORT);
        relaySocket.setSoTimeout(500);
        relayThread = new Thread(this::relayLoop, "udp-relay");
        relayThread.setDaemon(true);
    }

    public void start() {
        relayThread.start();
        System.out.println("[UdpRelay] Started on port " + RELAY_PORT);
    }

    public void stop() {
        running = false;
        if (relaySocket != null && !relaySocket.isClosed()) {
            relaySocket.close();
        }
    }

    // Đăng ký cuộc gọi ACTIVE để relay biết cần forward
    public void registerCall(String callId, String callerUsername, String calleeUsername) {
        activeSessions.put(callId, new EndpointPair(callerUsername, calleeUsername));
        System.out.println("[UdpRelay] Registered callId=" + callId + " (" + callerUsername + " ↔ " + calleeUsername + ")");
    }

    // Xóa mapping khi cuộc gọi kết thúc
    public void unregisterCall(String callId) {
        activeSessions.remove(callId);
        System.out.println("[UdpRelay] Unregistered callId=" + callId);
    }

    public int getRelayPort() {
        return RELAY_PORT;
    }

    private void relayLoop() {
        byte[] buffer = new byte[MAX_PACKET_SIZE];
        DatagramPacket packet = new DatagramPacket(buffer, buffer.length);

        while (running) {
            try {
                relaySocket.receive(packet);

                int length = packet.getLength();
                if (length <= CALL_ID_LENGTH) continue;

                // Extract callId (8 bytes đầu)
                String callId = new String(buffer, 0, CALL_ID_LENGTH);

                EndpointPair pair = activeSessions.get(callId);
                if (pair == null) continue;

                InetAddress senderAddr = packet.getAddress();
                int senderPort = packet.getPort();

                // Ghi nhận address (lazy binding khi nhận packet đầu tiên)
                Endpoint sender = new Endpoint(senderAddr, senderPort);
                boolean isCaller = pair.trySetCaller(sender);
                boolean isCallee = !isCaller && pair.trySetCallee(sender);

                if (!isCaller && !isCallee) continue;

                // Forward payload (skip callId header) cho bên kia
                Endpoint target = isCaller ? pair.getCalleeEndpoint() : pair.getCallerEndpoint();
                if (target == null) continue;

                int payloadOffset = CALL_ID_LENGTH;
                int payloadLength = length - CALL_ID_LENGTH;
                DatagramPacket forward = new DatagramPacket(
                        buffer, payloadOffset, payloadLength,
                        target.address, target.port
                );
                relaySocket.send(forward);

            } catch (SocketTimeoutException ste) {
                // normal
            } catch (Exception e) {
                if (running) {
                    System.err.println("[UdpRelay] Error: " + e.getMessage());
                }
            }
        }
        System.out.println("[UdpRelay] Stopped");
    }

    private static class Endpoint {
        final InetAddress address;
        final int port;

        Endpoint(InetAddress address, int port) {
            this.address = address;
            this.port = port;
        }

        boolean matches(InetAddress addr, int p) {
            return this.port == p && this.address.equals(addr);
        }
    }

    // Lưu 2 endpoint của 1 cuộc gọi. Address set khi nhận packet đầu tiên.
    private static class EndpointPair {
        final String callerUsername;
        final String calleeUsername;
        volatile Endpoint callerEndpoint;
        volatile Endpoint calleeEndpoint;

        EndpointPair(String caller, String callee) {
            this.callerUsername = caller;
            this.calleeUsername = callee;
        }

        synchronized boolean trySetCaller(Endpoint sender) {
            if (callerEndpoint == null) {
                callerEndpoint = sender;
                return true;
            }
            return callerEndpoint.matches(sender.address, sender.port);
        }

        synchronized boolean trySetCallee(Endpoint sender) {
            if (calleeEndpoint == null) {
                calleeEndpoint = sender;
                return true;
            }
            return calleeEndpoint.matches(sender.address, sender.port);
        }

        Endpoint getCallerEndpoint() { return callerEndpoint; }
        Endpoint getCalleeEndpoint() { return calleeEndpoint; }
    }
}
