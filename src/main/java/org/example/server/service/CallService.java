package org.example.server.service;

import org.example.common.model.CallLog;
import org.example.common.model.User;
import org.example.common.network.CallPacketTypes;
import org.example.common.network.CallPayloads;
import org.example.common.network.Packet;
import org.example.server.dao.CallLogDAO;
import org.example.server.dao.FriendshipDAO;
import org.example.server.dao.UserDAO;
import org.example.server.network.ClientHandler;
import org.example.server.network.ServerManager;
import org.example.server.network.UdpRelayService;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Map;
import java.util.concurrent.*;

// quản lý signaling cho cuộc gọi thoại và video
// dùng chung một instance trong servermanager
public class CallService {

    private final ServerManager serverManager;
    private final FriendshipDAO friendshipDAO = new FriendshipDAO();
    private final UserDAO userDAO = new UserDAO();
    private final CallLogDAO callLogDAO = new CallLogDAO();

    // trạng thái cuộc gọi dùng chung giữa các kết nối
    private final Map<String, CallSession> activeCalls = new ConcurrentHashMap<>();
    private final Map<String, String> userToCallId = new ConcurrentHashMap<>();

    // bộ hẹn giờ timeout cho trạng thái ringing
    private final ScheduledExecutorService timeoutExecutor = Executors.newScheduledThreadPool(1);
    private final Map<String, ScheduledFuture<?>> ringingTimeouts = new ConcurrentHashMap<>();

    public CallService(ServerManager serverManager) {
        this.serverManager = serverManager;
    }

    // lưu phiên cuộc gọi phía server

    private static class CallSession {
        String callId;
        String callerUsername;
        String calleeUsername;
        String type;

        enum State { RINGING, ACCEPTED, ACTIVE, ENDED }
        State state;

        Instant startedAt;
        Instant connectedAt;
    }

    // xử lý yêu cầu mời gọi

    public void handleInvite(String payload, ClientHandler callerClient) {
        var req = CallPayloads.fromJson(payload, CallPayloads.InviteRequest.class);
        if (req == null || req.to() == null || req.callId() == null) return;

        String caller = callerClient.getCurrentUsername();
        String callee = req.to();
        String callId = req.callId();

        // kiểm tra dữ liệu gọi
        if (caller.equals(callee)) {
            sendFailed(callerClient, callId, CallPacketTypes.REASON_SELF_CALL);
            return;
        }
        if (userToCallId.containsKey(caller)) {
            sendFailed(callerClient, callId, "ALREADY_IN_CALL");
            return;
        }

        ClientHandler calleeHandler = serverManager.getClientHandler(callee);
        if (calleeHandler == null) {
            sendFailed(callerClient, callId, CallPacketTypes.REASON_OFFLINE);
            return;
        }
        if (userToCallId.containsKey(callee)) {
            var busyPayload = new CallPayloads.CallIdPayload(callId);
            callerClient.sendPacket(new Packet(CallPacketTypes.BUSY, CallPayloads.toJson(busyPayload)));
            return;
        }

        // kiểm tra trạng thái chặn
        User callerUser = userDAO.findByUsername(caller);
        User calleeUser = userDAO.findByUsername(callee);
        if (callerUser == null || calleeUser == null) {
            sendFailed(callerClient, callId, CallPacketTypes.REASON_INVALID_TARGET);
            return;
        }
        if (friendshipDAO.isBlocked(callerUser, calleeUser)) {
            sendFailed(callerClient, callId, CallPacketTypes.REASON_BLOCKED);
            return;
        }

        // tạo phiên cuộc gọi
        CallSession session = new CallSession();
        session.callId = callId;
        session.callerUsername = caller;
        session.calleeUsername = callee;
        session.type = req.type();
        session.state = CallSession.State.RINGING;
        session.startedAt = Instant.now();

        activeCalls.put(callId, session);
        userToCallId.put(caller, callId);
        userToCallId.put(callee, callId);

        // chuyển lời mời đến người nhận
        var incoming = new CallPayloads.IncomingInvite(caller, callee, callId, req.type());
        calleeHandler.sendPacket(new Packet(CallPacketTypes.INVITE, CallPayloads.toJson(incoming)));

        // đặt timeout 30 giây cho lời mời
        ScheduledFuture<?> timeout = timeoutExecutor.schedule(() -> {
            CallSession s = activeCalls.get(callId);
            if (s != null && s.state == CallSession.State.RINGING) {
                var rejectPayload = new CallPayloads.RejectPayload(callId, CallPacketTypes.REASON_MISSED);
                serverManager.sendToClient(caller, new Packet(CallPacketTypes.REJECT, CallPayloads.toJson(rejectPayload)));
                // thông báo người nhận đóng popup
                serverManager.sendToClient(callee, new Packet(CallPacketTypes.CANCEL, CallPayloads.toJson(new CallPayloads.CallIdPayload(callId))));
                cleanup(callId, "MISSED");
            }
        }, 30, TimeUnit.SECONDS);
        ringingTimeouts.put(callId, timeout);

        log(callId, "INVITE " + caller + " → " + callee);
    }

    // xử lý chấp nhận cuộc gọi

    public void handleAccept(String payload, ClientHandler calleeClient) {
        var req = CallPayloads.fromJson(payload, CallPayloads.AcceptPayload.class);
        if (req == null || req.callId() == null) return;

        CallSession session = activeCalls.get(req.callId());
        if (session == null) return;
        if (session.state != CallSession.State.RINGING) return;
        if (!session.calleeUsername.equals(calleeClient.getCurrentUsername())) return;

        session.state = CallSession.State.ACCEPTED;
        cancelTimeout(req.callId());

        // chuyển xác nhận đến người gọi
        serverManager.sendToClient(session.callerUsername,
                new Packet(CallPacketTypes.ACCEPT, CallPayloads.toJson(req)));

        log(req.callId(), "ACCEPT by " + session.calleeUsername);
    }

    // xử lý xác nhận kết nối cuộc gọi

    public void handleAcceptAck(String payload, ClientHandler callerClient) {
        var req = CallPayloads.fromJson(payload, CallPayloads.AckPayload.class);
        if (req == null || req.callId() == null) return;

        CallSession session = activeCalls.get(req.callId());
        if (session == null) return;
        if (session.state != CallSession.State.ACCEPTED) return;
        if (!session.callerUsername.equals(callerClient.getCurrentUsername())) return;

        session.state = CallSession.State.ACTIVE;
        session.connectedAt = Instant.now();

        // đăng ký relay udp cho audio và video
        var audioRelay = serverManager.getAudioRelayService();
        if (audioRelay != null) {
            audioRelay.registerCall(req.callId(), session.callerUsername, session.calleeUsername);
        }
        var videoRelay = serverManager.getVideoRelayService();
        if (videoRelay != null) {
            videoRelay.registerCall(req.callId(), session.callerUsername, session.calleeUsername);
        }

        // chuyển ack đến người nhận
        serverManager.sendToClient(session.calleeUsername,
                new Packet(CallPacketTypes.ACCEPT_ACK, CallPayloads.toJson(req)));

        log(req.callId(), "ACTIVE — connected");
    }


    // xử lý từ chối cuộc gọi

    public void handleReject(String payload, ClientHandler calleeClient) {
        var req = CallPayloads.fromJson(payload, CallPayloads.RejectPayload.class);
        if (req == null || req.callId() == null) return;

        CallSession session = activeCalls.get(req.callId());
        if (session == null) return;
        if (!session.calleeUsername.equals(calleeClient.getCurrentUsername())) return;

        // chuyển từ chối đến người gọi
        serverManager.sendToClient(session.callerUsername,
                new Packet(CallPacketTypes.REJECT, CallPayloads.toJson(req)));

        cleanup(req.callId(), "REJECTED");
        log(req.callId(), "REJECTED reason=" + req.reason());
    }

    // xử lý hủy cuộc gọi

    public void handleCancel(String payload, ClientHandler callerClient) {
        var req = CallPayloads.fromJson(payload, CallPayloads.CallIdPayload.class);
        if (req == null || req.callId() == null) return;

        CallSession session = activeCalls.get(req.callId());
        if (session == null) return;
        if (!session.callerUsername.equals(callerClient.getCurrentUsername())) return;

        // chuyển hủy đến người nhận
        serverManager.sendToClient(session.calleeUsername,
                new Packet(CallPacketTypes.CANCEL, CallPayloads.toJson(req)));

        cleanup(req.callId(), "CANCELED");
        log(req.callId(), "CANCELED by caller");
    }

    // xử lý kết thúc cuộc gọi

    public void handleEnd(String payload, ClientHandler client) {
        var req = CallPayloads.fromJson(payload, CallPayloads.CallIdPayload.class);
        if (req == null || req.callId() == null) return;

        CallSession session = activeCalls.get(req.callId());
        if (session == null) return;

        String username = client.getCurrentUsername();
        String otherUser;
        if (username.equals(session.callerUsername)) {
            otherUser = session.calleeUsername;
        } else if (username.equals(session.calleeUsername)) {
            otherUser = session.callerUsername;
        } else {
            return;
        }

        // chuyển kết thúc đến bên còn lại
        serverManager.sendToClient(otherUser,
                new Packet(CallPacketTypes.END, CallPayloads.toJson(req)));

        cleanup(req.callId(), "COMPLETED");
        log(req.callId(), "ENDED by " + username);
    }

    // xử lý client mất kết nối

    public void handleClientDisconnect(String username) {
        if (username == null) return;
        String callId = userToCallId.get(username);
        if (callId == null) return;

        CallSession session = activeCalls.get(callId);
        if (session == null) return;

        String otherUser = username.equals(session.callerUsername)
                ? session.calleeUsername
                : session.callerUsername;

        var endPayload = new CallPayloads.CallIdPayload(callId);
        serverManager.sendToClient(otherUser,
                new Packet(CallPacketTypes.END, CallPayloads.toJson(endPayload)));

        cleanup(callId, session.state == CallSession.State.ACTIVE ? "COMPLETED" : "MISSED");
        log(callId, "DISCONNECT — " + username + " dropped");
    }

    // hàm hỗ trợ vòng đời cuộc gọi

    private void cleanup(String callId, String status) {
        CallSession session = activeCalls.remove(callId);
        if (session != null) {
            userToCallId.remove(session.callerUsername);
            userToCallId.remove(session.calleeUsername);

            // hủy đăng ký khỏi relay udp
            var audioRelay = serverManager.getAudioRelayService();
            if (audioRelay != null) {
                audioRelay.unregisterCall(callId);
            }
            var videoRelay = serverManager.getVideoRelayService();
            if (videoRelay != null) {
                videoRelay.unregisterCall(callId);
            }

            saveCallLog(session, status);
        }
        cancelTimeout(callId);
    }


    private void cancelTimeout(String callId) {
        ScheduledFuture<?> future = ringingTimeouts.remove(callId);
        if (future != null) future.cancel(false);
    }

    private void sendFailed(ClientHandler client, String callId, String reason) {
        var payload = new CallPayloads.FailedPayload(callId, reason);
        client.sendPacket(new Packet(CallPacketTypes.FAILED, CallPayloads.toJson(payload)));
    }

    private void log(String callId, String msg) {
        System.out.println("[Call] callId=" + callId + " " + msg);
    }

    private void saveCallLog(CallSession session, String status) {
        try {
            User caller = userDAO.findByUsername(session.callerUsername);
            User callee = userDAO.findByUsername(session.calleeUsername);
            if (caller == null || callee == null) return;

            LocalDateTime startedAt = LocalDateTime.ofInstant(session.startedAt, ZoneId.systemDefault());
            CallLog callLog = new CallLog(caller, callee, session.type, status, startedAt);

            if (session.connectedAt != null) {
                Instant endedAt = Instant.now();
                callLog.setEndedAt(LocalDateTime.ofInstant(endedAt, ZoneId.systemDefault()));
                callLog.setDurationSec((int) Duration.between(session.connectedAt, endedAt).getSeconds());
            }

            callLogDAO.save(callLog);
        } catch (Exception e) {
            System.err.println("[Call] Failed to save CallLog: " + e.getMessage());
        }
    }
}
