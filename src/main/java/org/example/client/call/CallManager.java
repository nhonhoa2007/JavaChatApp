package org.example.client.call;

import org.example.client.network.ChatClient;
import org.example.common.network.CallPacketTypes;
import org.example.common.network.CallPayloads;
import org.example.common.network.Packet;

import java.net.DatagramSocket;
import java.net.InetAddress;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class CallManager {
    private static CallManager instance;

    public static void init(ChatClient client) {
        if (instance == null) {
            instance = new CallManager(client);
        }
    }

    public static CallManager getInstance() {
        if (instance == null) {
            throw new IllegalStateException("CallManager has not been init");
        }
        return instance;
    }

    private final ChatClient chatClient;
    private String myUsername;
    private CallSession currentSession;
    private final List<CallEventListener> listeners = new ArrayList<>();

    private CallManager(ChatClient chatClient) {
        this.chatClient = chatClient;
        // đăng ký listener khi cần xử lý packet trực tiếp
    }

    public synchronized void setMyUsername(String myUsername) {
        this.myUsername = myUsername;
    }

    public synchronized CallSession getCurrentSession() {
        return currentSession;
    }

    public synchronized boolean isInCall() {
        return currentSession != null;
    }

    public synchronized void addListener(CallEventListener listener) {
        if (!listeners.contains(listener)) listeners.add(listener);
    }

    public synchronized void removeListener(CallEventListener listener) {
        listeners.remove(listener);
    }


    public synchronized void startCall(String peerUsername, String type) {
        if (currentSession != null) {
            System.out.println("Đang trong cuộc gọi, không thể bắt đầu cuộc gọi mới");
            return;
        }
        if (peerUsername == null || peerUsername.isBlank()) {
            System.out.println("Peer username không hợp lệ");
            return;
        }
        // xử lý api của người gọi
        String callId = UUID.randomUUID().toString().substring(0, 8);

        currentSession = new CallSession();
        currentSession.callId = callId;
        currentSession.peerUsername = peerUsername;
        currentSession.type = type;
        currentSession.role = CallSession.Role.CALLER;
        currentSession.state = CallSession.State.RINGING_OUTGOING;
        currentSession.startedAt = Instant.now();

        var payload = new CallPayloads.InviteRequest(peerUsername, callId, type);
        chatClient.sendPacket(new Packet("CALL_INVITE", CallPayloads.toJson(payload)));

        System.out.println("Gọi " + peerUsername + " (" + type + ")..." + " callId : " + callId);
        fireOutgoingCallStarted(currentSession);
    }

    public synchronized void cancelCall() {
        if (currentSession == null) return;
        if (currentSession.state != CallSession.State.RINGING_OUTGOING) {
            System.out.println("Không thể cancel - trạng thái : " + currentSession.state);
            return;
        }

        var payload = new CallPayloads.CallIdPayload(currentSession.callId);
        chatClient.sendPacket(new Packet(CallPacketTypes.CANCEL, CallPayloads.toJson(payload)));

        System.out.println("cancelCall  callId : " + currentSession.callId);
        cleanup("Đã hủy");

    }

    // xử lý api của người nhận
    public synchronized void acceptCall() {
        if (currentSession == null) return;
        if (currentSession.state != CallSession.State.RINGING_INCOMING) {
            System.out.println("Ko thể accept - trạng thái : " + currentSession.state);
            return;
        }
        DatagramSocket Socket;
        String localIp;
        int localPort;
        try {
            Socket = new DatagramSocket(0);
            localPort = Socket.getLocalPort();
            localIp = InetAddress.getLocalHost().getHostAddress();
            currentSession.udpSocket = Socket;
            currentSession.localUdpPort = localPort;

        } catch (Exception e) {
            rejectCall("UDP_OPEN_FAILED");
            return;
        }
        currentSession.state = CallSession.State.CONNECTING;

        var payload = new CallPayloads.AcceptPayload(currentSession.callId, localIp, localPort);
        chatClient.sendPacket(new Packet(CallPacketTypes.ACCEPT, CallPayloads.toJson(payload)));

        System.out.println("acceptCall  callId : " + currentSession.callId + " port : " + localPort);
        fireCallConnecting(currentSession);
    }

    public synchronized void rejectCall(String reason) {
        if (currentSession == null) return;
        if (currentSession.state != CallSession.State.RINGING_INCOMING) {
            System.out.println("Ko thể reject - trạng thái : " + currentSession.state);
            return;

        }
        var payload = new CallPayloads.RejectPayload(currentSession.callId, reason);
        chatClient.sendPacket(new Packet(CallPacketTypes.REJECT, CallPayloads.toJson(payload)));
        cleanup(reason);
    }

    // xử lý api chung của cuộc gọi
    public synchronized void endCall() {
        if (currentSession == null) return;
        if (currentSession.state == CallSession.State.ENDED) return;
        var payload = new CallPayloads.CallIdPayload(currentSession.callId);
        chatClient.sendPacket(new Packet(CallPacketTypes.END, CallPayloads.toJson(payload)));
        cleanup("Đã kết thúc");
    }

    public synchronized void setMuted(boolean muted) {
        if (currentSession != null && currentSession.captureThread != null) {
            currentSession.captureThread.setMuted(muted);
        }
        System.out.println("[CallManager] setMuted=" + muted);
    }

    public synchronized void setVideoMuted(boolean videoMuted) {
        if (currentSession != null && currentSession.videoCaptureThread != null) {
            currentSession.videoCaptureThread.setVideoMuted(videoMuted);
        }
        System.out.println("[CallManager] setVideoMuted=" + videoMuted);
    }


    public void handlePacket(Packet packet) {
        if (packet == null || packet.getType() == null) return;

        String type = packet.getType();

        if (!type.startsWith("CALL_")) return;
        switch (type) {
            case CallPacketTypes.INVITE -> onInviteReceived(packet.getPayload());
            case CallPacketTypes.ACCEPT -> onAcceptReceived(packet.getPayload());
            case CallPacketTypes.ACCEPT_ACK -> onAckReceived(packet.getPayload());
            case CallPacketTypes.REJECT -> onRejectReceived(packet.getPayload());
            case CallPacketTypes.CANCEL -> onCancelReceived(packet.getPayload());
            case CallPacketTypes.END -> onEndReceived(packet.getPayload());
            case CallPacketTypes.BUSY -> onBusyReceived(packet.getPayload());
            case CallPacketTypes.FAILED -> onFailedReceived(packet.getPayload());
        }
    }
    public synchronized void onInviteReceived(String json) {
        var payload = CallPayloads.fromJson(json, CallPayloads.IncomingInvite.class);
        if(payload==null||payload.callId()==null||payload.from()==null) return;
        if(currentSession!=null){
            var busy = new CallPayloads.CallIdPayload(payload.callId());
            chatClient.sendPacket(new Packet(CallPacketTypes.BUSY, CallPayloads.toJson(busy)));
            return;
        }
        currentSession=new CallSession();
        currentSession.callId = payload.callId();
        currentSession.peerUsername=payload.from();
        currentSession.type = payload.type();
        currentSession.role = CallSession.Role.CALLEE;
        currentSession.state = CallSession.State.RINGING_INCOMING;
        currentSession.startedAt = Instant.now();

        fireIncomingCall(currentSession);
    }
    public synchronized void onAcceptReceived(String json) {
        var payload = CallPayloads.fromJson(json, CallPayloads.AcceptPayload.class);
        if(payload==null||currentSession==null) return;
        if(!payload.callId().equals(currentSession.callId)) return;
        if(currentSession.state!=CallSession.State.RINGING_OUTGOING) return;

        currentSession.peerIp=payload.ip();
        currentSession.peerUdpPort=payload.port();
        currentSession.state = CallSession.State.CONNECTING;

        fireCallConnecting(currentSession);

        try{
            DatagramSocket sock= new DatagramSocket(0);
            currentSession.udpSocket = sock;
            currentSession.localUdpPort = sock.getLocalPort();
            String localIp = InetAddress.getLocalHost().getHostAddress();

            var ack = new CallPayloads.AckPayload(
                    currentSession.callId, localIp, currentSession.localUdpPort
            );
            chatClient.sendPacket(new Packet(CallPacketTypes.ACCEPT_ACK, CallPayloads.toJson(ack)));
        } catch (Exception e) {
            e.printStackTrace();
            return;
        }
        currentSession.state = CallSession.State.ACTIVE;
        currentSession.connectedAt = Instant.now();
        System.out.println("call ACTIVE (caller) peer=" + payload.ip() + ":" + payload.port());

        startAudioThreads();
        startVideoThreads();
        fireCallActive(currentSession);
    }
    public synchronized void onAckReceived(String json) {
        var payload = CallPayloads.fromJson(json, CallPayloads.AckPayload.class);
        if(payload==null||currentSession==null) return;
        if(!payload.callId().equals(currentSession.callId)) return;
        if(currentSession.state!=CallSession.State.CONNECTING) return;

        currentSession.peerIp=payload.ip();
        currentSession.peerUdpPort=payload.port();
        currentSession.state = CallSession.State.ACTIVE;
        currentSession.connectedAt = Instant.now();
        System.out.println("call ACTIVE (callee) peer=" + payload.ip() + ":" + payload.port());

        startAudioThreads();
        startVideoThreads();
        fireCallActive(currentSession);
    }
    public synchronized void onRejectReceived(String json) {
        var payload = CallPayloads.fromJson(json, CallPayloads.RejectPayload.class);
        String reason = (payload != null && payload.reason() != null)
                ? payload.reason()
                : CallPacketTypes.REASON_USER_REJECT;

        System.out.println("Call rejected: " + reason);
        cleanup(reason);
    }

    public synchronized void onCancelReceived(String json) {
        System.out.println("Call canceled by peer");
        cleanup("CANCELED");
    }

    public synchronized void onEndReceived(String json) {
        System.out.println("Call ended by peer");
        cleanup("ENDED");
    }
    public synchronized void onBusyReceived(String json) {
        System.out.println("Call busy by peer");
        cleanup(CallPacketTypes.BUSY);
    }
    public synchronized void onFailedReceived(String json) {
        var payload = CallPayloads.fromJson(json, CallPayloads.FailedPayload.class);
        String reason = (payload != null && payload.reason() != null)
                ? payload.reason()
                : "UNKNOWN";
        System.out.println("Call failed: " + reason);
        cleanup(reason);
    }

    // quản lý âm thanh cuộc gọi

    // cổng relay âm thanh trên server
    private static final int RELAY_PORT = 8889;

    private void startAudioThreads() {
        if (currentSession == null || currentSession.udpSocket == null) {
            System.err.println("[CallManager] Cannot start audio — no session or socket");
            return;
        }
        try {
            // gửi âm thanh đến relay server thay vì peer trực tiếp
            // lấy ip server từ kết nối tcp hiện tại
            InetAddress serverAddr = InetAddress.getByName(
                    chatClient.getServerHost() != null ? chatClient.getServerHost() : "localhost"
            );

            currentSession.captureThread = new AudioCaptureThread(
                    currentSession.udpSocket, serverAddr, RELAY_PORT
            );
            // gắn call id để server biết cần chuyển tiếp cho ai
            currentSession.captureThread.setRelayCallId(currentSession.callId);

            currentSession.playbackThread = new AudioPlaybackThread(currentSession.udpSocket);

            currentSession.captureThread.start();
            currentSession.playbackThread.start();

            // bắt đầu theo dõi heartbeat
            startHeartbeatMonitor();

            System.out.println("[CallManager] Audio threads started (relay mode → server:" + RELAY_PORT + ")");
        } catch (Exception e) {
            System.err.println("[CallManager] Failed to start audio: " + e.getMessage());
        }
    }

    private static final int RELAY_VIDEO_PORT = 8890;

    private void startVideoThreads() {
        if (currentSession == null) return;
        if (!org.example.common.network.CallPacketTypes.TYPE_VIDEO.equals(currentSession.type)) return;

        try {
            java.net.DatagramSocket videoSocket = new java.net.DatagramSocket(0);
            currentSession.videoUdpSocket = videoSocket;
            currentSession.localVideoUdpPort = videoSocket.getLocalPort();

            InetAddress serverAddr = InetAddress.getByName(
                    chatClient.getServerHost() != null ? chatClient.getServerHost() : "localhost"
            );

            currentSession.videoCaptureThread = new VideoCaptureThread(
                    currentSession.videoUdpSocket, serverAddr, RELAY_VIDEO_PORT, myUsername
            );
            currentSession.videoCaptureThread.setRelayCallId(currentSession.callId);

            currentSession.videoPlaybackThread = new VideoPlaybackThread(currentSession.videoUdpSocket);

            currentSession.videoCaptureThread.start();
            currentSession.videoPlaybackThread.start();

            System.out.println("[CallManager] Video threads started (relay mode → server:" + RELAY_VIDEO_PORT + ")");
        } catch (Exception e) {
            System.err.println("[CallManager] Failed to start video: " + e.getMessage());
        }
    }


    private Thread heartbeatThread;

    private void startHeartbeatMonitor() {
        heartbeatThread = new Thread(() -> {
            long lastCheck = 0;
            try {
                // đợi cuộc gọi ổn định trước khi theo dõi
                Thread.sleep(3000);

                while (currentSession != null && currentSession.state == CallSession.State.ACTIVE) {
                    Thread.sleep(1000);

                    if (currentSession == null || currentSession.playbackThread == null) break;

                    long received = currentSession.playbackThread.getPacketsReceived();
                    if (received == lastCheck) {
                        // kiểm tra thêm khi không nhận gói mới
                        Thread.sleep(4000);
                        if (currentSession == null || currentSession.playbackThread == null) break;
                        long receivedAfterWait = currentSession.playbackThread.getPacketsReceived();
                        if (receivedAfterWait == received) {
                            // tự kết thúc khi mất gói quá lâu
                            System.out.println("[CallManager] Heartbeat timeout — peer lost connection");
                            endCall();
                            break;
                        }
                    }
                    lastCheck = currentSession != null && currentSession.playbackThread != null
                            ? currentSession.playbackThread.getPacketsReceived()
                            : 0;
                }
            } catch (InterruptedException e) {
                // dừng theo dõi
            }
        }, "call-heartbeat");
        heartbeatThread.setDaemon(true);
        heartbeatThread.start();
    }

    private void cleanup(String reason) {
        CallSession ended = currentSession;
        if(ended==null) return;

        ended.state = CallSession.State.ENDED;

        // dừng heartbeat monitor
        if (heartbeatThread != null) {
            heartbeatThread.interrupt();
            heartbeatThread = null;
        }

        // dừng luồng âm thanh
        if (ended.captureThread != null) {
            ended.captureThread.shutdown();
            ended.captureThread = null;
        }
        if (ended.playbackThread != null) {
            ended.playbackThread.shutdown();
            ended.playbackThread = null;
        }

        // đóng socket udp
        if(ended.udpSocket!=null&&!ended.udpSocket.isClosed()) ended.udpSocket.close();

        // dừng luồng video
        if (ended.videoCaptureThread != null) {
            ended.videoCaptureThread.shutdown();
            ended.videoCaptureThread = null;
        }
        if (ended.videoPlaybackThread != null) {
            ended.videoPlaybackThread.shutdown();
            ended.videoPlaybackThread = null;
        }

        // đóng socket udp video
        if (ended.videoUdpSocket != null && !ended.videoUdpSocket.isClosed()) {
            ended.videoUdpSocket.close();
        }

        currentSession = null;

        fireCallEnded(ended,reason);
    }
    private List<CallEventListener> snapshotListeners() {
        synchronized (this) {
            return new ArrayList<>(listeners);
        }
    }


    private void fireCallEnded(CallSession ended, String reason) {
        for(var p : snapshotListeners()) p.onCallEnded(ended, reason);
    }

    private void fireIncomingCall(CallSession s) {
        for (var l : snapshotListeners()) l.onIncomingCall(s);
    }

    private void fireOutgoingCallStarted(CallSession s) {
        for (var l : snapshotListeners()) l.onOutgoingCallStarted(s);
    }

    private void fireCallConnecting(CallSession s) {
        for (var l : snapshotListeners()) l.onCallConnecting(s);
    }

    private void fireCallActive(CallSession s) {
        for (var l : snapshotListeners()) l.onCallActive(s);
    }
}

