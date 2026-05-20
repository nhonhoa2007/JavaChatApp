package org.example.common.network;

import com.google.gson.Gson;

// DTO cho payload các packet CALL_*. Immutable records, serialize bằng Gson.
public final class CallPayloads {

    private static final Gson GSON = new Gson();

    // Caller → Server. Không có "from" — server lấy từ ClientHandler.
    public record InviteRequest(String to, String callId, String type) {}

    // Server → Callee. "from" do server gắn.
    public record IncomingInvite(String from, String to, String callId, String type) {}

    // Callee → Server → Caller. Kèm UDP address của callee.
    public record AcceptPayload(String callId, String ip, int port) {}

    // Caller → Server → Callee. Kèm UDP address của caller.
    public record AckPayload(String callId, String ip, int port) {}

    // Callee từ chối
    public record RejectPayload(String callId, String reason) {}

    // Server báo lỗi
    public record FailedPayload(String callId, String reason) {}

    // Dùng chung cho CANCEL, END, BUSY
    public record CallIdPayload(String callId) {}

    public static String toJson(Object payload) {
        return GSON.toJson(payload);
    }

    public static <T> T fromJson(String json, Class<T> clazz) {
        return GSON.fromJson(json, clazz);
    }

    private CallPayloads() {
        throw new AssertionError("CallPayloads is a utility class, not instantiable");
    }
}
