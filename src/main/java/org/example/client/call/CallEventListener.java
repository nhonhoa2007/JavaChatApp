package org.example.client.call;

public interface CallEventListener {
    default void onIncomingCall(CallSession session) {}
    default void onOutgoingCallStarted(CallSession session) {}
    default void onCallConnecting(CallSession session) {}
    default void onCallActive(CallSession session) {}
    default void onCallEnded(CallSession session, String reason) {}
}
