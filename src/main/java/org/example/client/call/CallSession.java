package org.example.client.call;

import java.net.DatagramSocket;
import java.time.Instant;

public class CallSession {
    public enum Role{
        CALLER, CALLEE;
    }
    public enum State {
        RINGING_OUTGOING,
        RINGING_INCOMING,
        CONNECTING,
        ACTIVE,
        ENDED
    }
    public String callId;
    public Role role;
    public String peerUsername;
    public String type;
    public volatile State state;

    public String peerIp;
    public int peerUdpPort;

    public DatagramSocket udpSocket;
    public int localUdpPort;

    public Instant startedAt;
    public Instant connectedAt;

    // luồng âm thanh của cuộc gọi
    public AudioCaptureThread captureThread;
    public AudioPlaybackThread playbackThread;

    // luồng video của cuộc gọi
    public DatagramSocket videoUdpSocket;
    public int localVideoUdpPort;
    public VideoCaptureThread videoCaptureThread;
    public VideoPlaybackThread videoPlaybackThread;
}
