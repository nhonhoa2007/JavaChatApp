package org.example.client.call;

import javax.sound.sampled.*;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;

// Thread ghi mic và gửi audio frame qua UDP cho peer.
// Format: PCM 16kHz, 16-bit, mono, signed, little-endian.
// Frame size: 20ms = 640 bytes.
// Packet format: [callId prefix (8 bytes, relay only)][4 bytes seq][640 bytes PCM]
public class AudioCaptureThread extends Thread {

    private static final AudioFormat FORMAT = new AudioFormat(16000.0f, 16, 1, true, false);
    private static final int FRAME_SIZE = 640; // 20ms * 16000Hz * 2bytes

    private final DatagramSocket socket;
    private final InetAddress peerAddress;
    private final int peerPort;

    private volatile boolean running = true;
    private volatile boolean muted = false;

    private int sequenceNumber = 0;
    private byte[] callIdPrefix = null; // null = P2P, non-null = relay mode

    public AudioCaptureThread(DatagramSocket socket, InetAddress peerAddress, int peerPort) {
        super("audio-capture");
        setDaemon(true);
        this.socket = socket;
        this.peerAddress = peerAddress;
        this.peerPort = peerPort;
    }

    // Enable relay mode — prepend 8 bytes callId trước mỗi packet
    public void setRelayCallId(String callId) {
        if (callId != null && callId.length() >= 8) {
            this.callIdPrefix = callId.substring(0, 8).getBytes();
        }
    }

    @Override
    public void run() {
        TargetDataLine micLine = null;
        try {
            DataLine.Info info = new DataLine.Info(TargetDataLine.class, FORMAT);
            if (!AudioSystem.isLineSupported(info)) {
                System.err.println("[AudioCapture] Mic line not supported");
                return;
            }

            micLine = (TargetDataLine) AudioSystem.getLine(info);
            micLine.open(FORMAT);
            micLine.start();

            byte[] audioBuffer = new byte[FRAME_SIZE];
            int headerSize = 4;
            int prefixSize = (callIdPrefix != null) ? callIdPrefix.length : 0;
            int totalPacketSize = prefixSize + headerSize + FRAME_SIZE;
            byte[] sendBuffer = new byte[totalPacketSize];

            System.out.println("[AudioCapture] Started — target=" + peerAddress.getHostAddress() + ":" + peerPort
                    + (callIdPrefix != null ? " (relay mode)" : " (P2P)"));

            while (running && !socket.isClosed()) {
                int bytesRead = micLine.read(audioBuffer, 0, FRAME_SIZE);
                if (bytesRead == FRAME_SIZE && !muted) {
                    int offset = 0;

                    // Relay prefix (8 bytes callId)
                    if (callIdPrefix != null) {
                        System.arraycopy(callIdPrefix, 0, sendBuffer, 0, callIdPrefix.length);
                        offset = callIdPrefix.length;
                    }

                    // Sequence number (4 bytes big-endian)
                    sendBuffer[offset]     = (byte) ((sequenceNumber >> 24) & 0xFF);
                    sendBuffer[offset + 1] = (byte) ((sequenceNumber >> 16) & 0xFF);
                    sendBuffer[offset + 2] = (byte) ((sequenceNumber >> 8) & 0xFF);
                    sendBuffer[offset + 3] = (byte) (sequenceNumber & 0xFF);
                    offset += 4;

                    // Audio data
                    System.arraycopy(audioBuffer, 0, sendBuffer, offset, FRAME_SIZE);

                    DatagramPacket packet = new DatagramPacket(sendBuffer, totalPacketSize, peerAddress, peerPort);
                    socket.send(packet);

                    sequenceNumber++;
                }
            }
        } catch (Exception e) {
            if (running) {
                System.err.println("[AudioCapture] Error: " + e.getMessage());
            }
        } finally {
            if (micLine != null) {
                micLine.stop();
                micLine.close();
            }
            System.out.println("[AudioCapture] Stopped — sent " + sequenceNumber + " frames");
        }
    }

    public void shutdown() {
        running = false;
        this.interrupt();
    }

    public void setMuted(boolean muted) {
        this.muted = muted;
    }

    public boolean isMuted() {
        return muted;
    }

    public int getSequenceNumber() {
        return sequenceNumber;
    }
}
