package org.example.common.network;

import com.google.gson.Gson;

// định nghĩa payload cho các packet call
public final class CallPayloads {

    private static final Gson GSON = new Gson();

    // payload người gọi gửi lên server
    public record InviteRequest(String to, String callId, String type) {}

    // payload server gửi đến người nhận
    public record IncomingInvite(String from, String to, String callId, String type) {}

    // payload người nhận chấp nhận cuộc gọi
    public record AcceptPayload(String callId, String ip, int port) {}

    // payload xác nhận kết nối từ người gọi
    public record AckPayload(String callId, String ip, int port) {}

    // payload từ chối cuộc gọi
    public record RejectPayload(String callId, String reason) {}

    // payload báo lỗi cuộc gọi
    public record FailedPayload(String callId, String reason) {}

    // payload dùng chung cho hủy, kết thúc và bận
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
