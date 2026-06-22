package org.example.server.network;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketTimeoutException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

// chuyển tiếp dữ liệu udp giữa hai client
// client gửi gói gồm call id và payload
// server dùng call id để tìm người nhận còn lại
public class UdpRelayService {

    private final int port;
    private static final int CALL_ID_LENGTH = 8;
    private final int maxPacketSize;

    private final DatagramSocket relaySocket;
    private final Thread relayThread;
    private volatile boolean running = true;

    // ánh xạ call id với hai endpoint udp
    private final Map<String, EndpointPair> activeSessions = new ConcurrentHashMap<>();

    public UdpRelayService(int port, int maxPacketSize) throws Exception {
        this.port = port;
        this.maxPacketSize = maxPacketSize;
        relaySocket = new DatagramSocket(port);
        relaySocket.setSoTimeout(500);
        relayThread = new Thread(this::relayLoop, "udp-relay-" + port);
        relayThread.setDaemon(true);
    }

    public void start() {
        relayThread.start();
        System.out.println("[UdpRelay] Started on port " + port);
    }

    public void stop() {
        running = false;
        if (relaySocket != null && !relaySocket.isClosed()) {
            relaySocket.close();
        }
    }

    // đăng ký cuộc gọi đang hoạt động để relay dữ liệu
    public void registerCall(String callId, String callerUsername, String calleeUsername) {
        activeSessions.put(callId, new EndpointPair(callerUsername, calleeUsername));
        System.out.println("[UdpRelay-" + port + "] Registered callId=" + callId + " (" + callerUsername + " ↔ " + calleeUsername + ")");
    }

    // xóa mapping khi cuộc gọi kết thúc
    public void unregisterCall(String callId) {
        activeSessions.remove(callId);
        System.out.println("[UdpRelay-" + port + "] Unregistered callId=" + callId);
    }

    public int getRelayPort() {
        return port;
    }

    private void relayLoop() {
        byte[] buffer = new byte[maxPacketSize];
        DatagramPacket packet = new DatagramPacket(buffer, buffer.length);


        while (running) {
            try {
                relaySocket.receive(packet);

                int length = packet.getLength();
                if (length <= CALL_ID_LENGTH) continue;

                // tách call id từ 8 byte đầu
                String callId = new String(buffer, 0, CALL_ID_LENGTH);

                EndpointPair pair = activeSessions.get(callId);
                if (pair == null) continue;

                InetAddress senderAddr = packet.getAddress();
                int senderPort = packet.getPort();

                // ghi nhận địa chỉ khi nhận gói đầu tiên
                Endpoint sender = new Endpoint(senderAddr, senderPort);
                boolean isCaller = pair.trySetCaller(sender);
                boolean isCallee = !isCaller && pair.trySetCallee(sender);

                if (!isCaller && !isCallee) continue;

                // chuyển tiếp payload cho bên còn lại
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
                // timeout bình thường
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

    // lưu hai endpoint của một cuộc gọi
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
