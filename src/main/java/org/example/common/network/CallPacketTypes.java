package org.example.common.network;

public final class CallPacketTypes {
    public static final String INVITE      = "CALL_INVITE";
    public static final String ACCEPT      = "CALL_ACCEPT";
    public static final String ACCEPT_ACK  = "CALL_ACCEPT_ACK";
    public static final String REJECT      = "CALL_REJECT";
    public static final String CANCEL      = "CALL_CANCEL";
    public static final String END         = "CALL_END";
    public static final String BUSY        = "CALL_BUSY";
    public static final String FAILED      = "CALL_FAILED";
    // lý do từ chối cuộc gọi
    public static final String REASON_USER_REJECT    = "USER_REJECT";
    public static final String REASON_MISSED         = "MISSED";
    public static final String REASON_TIMEOUT        = "TIMEOUT";
    public static final String REASON_OFFLINE        = "OFFLINE";
    public static final String REASON_BLOCKED        = "BLOCKED";
    public static final String REASON_INVALID_TARGET = "INVALID_TARGET";
    public static final String REASON_SELF_CALL      = "SELF_CALL";

    public static final String TYPE_VOICE = "VOICE";
    public static final String TYPE_VIDEO = "VIDEO";

    private CallPacketTypes() {
        throw new AssertionError("CallPacketTypes is a constants class");
    }
}
