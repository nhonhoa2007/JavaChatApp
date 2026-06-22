package org.example.client.call;

import javax.sound.sampled.*;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.SocketTimeoutException;

// nhận âm thanh từ udp và phát ra loa
// tách luồng nhận gói tin và luồng phát từ buffer
// dùng định dạng pcm 16khz 16-bit mono
public class AudioPlaybackThread extends Thread {

    private static final AudioFormat FORMAT = new AudioFormat(16000.0f, 16, 1, true, false);
    private static final int FRAME_SIZE = 640;
    private static final byte[] SILENCE = new byte[FRAME_SIZE];

    private final DatagramSocket socket;
    private final JitterBuffer jitterBuffer = new JitterBuffer();
    private volatile boolean running = true;

    private Thread receiveThread;

    // thống kê nhận gói
    private long packetsReceived;
    private int lastSeq = -1;
    private long packetsLost;

    public AudioPlaybackThread(DatagramSocket socket) {
        super("audio-playback");
        setDaemon(true);
        this.socket = socket;
    }

    @Override
    public void run() {
        receiveThread = new Thread(this::receiveLoop, "audio-receive");
        receiveThread.setDaemon(true);
        receiveThread.start();

        SourceDataLine speakerLine = null;
        try {
            DataLine.Info info = new DataLine.Info(SourceDataLine.class, FORMAT);
            if (!AudioSystem.isLineSupported(info)) {
                System.err.println("[AudioPlayback] Speaker line not supported");
                return;
            }

            speakerLine = (SourceDataLine) AudioSystem.getLine(info);
            speakerLine.open(FORMAT, FRAME_SIZE * 4);
            speakerLine.start();

            System.out.println("[AudioPlayback] Started — port " + socket.getLocalPort());

            while (running) {
                byte[] frame = jitterBuffer.take(20);

                if (frame == null) {
                    speakerLine.write(SILENCE, 0, FRAME_SIZE);
                } else {
                    speakerLine.write(frame, 0, Math.min(frame.length, FRAME_SIZE));
                }
            }
        } catch (Exception e) {
            if (running) {
                System.err.println("[AudioPlayback] Error: " + e.getMessage());
            }
        } finally {
            if (speakerLine != null) {
                speakerLine.drain();
                speakerLine.stop();
                speakerLine.close();
            }
            System.out.println("[AudioPlayback] Stopped — received=" + packetsReceived + " lost=" + packetsLost);
        }
    }

    // vòng lặp nhận udp và đưa frame hợp lệ vào jitter buffer
    private void receiveLoop() {
        try {
            socket.setSoTimeout(200);
            byte[] buffer = new byte[FRAME_SIZE + 4];
            DatagramPacket packet = new DatagramPacket(buffer, buffer.length);

            while (running && !socket.isClosed()) {
                try {
                    socket.receive(packet);
                    int length = packet.getLength();

                    if (length >= 4 + FRAME_SIZE) {
                        // định dạng có số thứ tự và dữ liệu âm thanh
                        int seq = ((buffer[0] & 0xFF) << 24)
                                | ((buffer[1] & 0xFF) << 16)
                                | ((buffer[2] & 0xFF) << 8)
                                | (buffer[3] & 0xFF);

                        if (seq <= lastSeq) {
                            continue; // bỏ gói đến muộn
                        }

                        if (lastSeq >= 0 && seq > lastSeq + 1) {
                            packetsLost += (seq - lastSeq - 1);
                        }
                        lastSeq = seq;
                        packetsReceived++;

                        byte[] audioFrame = new byte[FRAME_SIZE];
                        System.arraycopy(buffer, 4, audioFrame, 0, FRAME_SIZE);
                        jitterBuffer.put(audioFrame);

                    } else if (length == FRAME_SIZE) {
                        // hỗ trợ định dạng cũ không có số thứ tự
                        byte[] audioFrame = new byte[FRAME_SIZE];
                        System.arraycopy(buffer, 0, audioFrame, 0, FRAME_SIZE);
                        jitterBuffer.put(audioFrame);
                        packetsReceived++;
                    }
                } catch (SocketTimeoutException ste) {
                    // timeout nhận gói bình thường
                }
            }
        } catch (Exception e) {
            if (running) {
                System.err.println("[AudioReceive] Error: " + e.getMessage());
            }
        }
    }

    public void shutdown() {
        running = false;
        if (receiveThread != null) receiveThread.interrupt();
        this.interrupt();
    }

    public long getPacketsReceived() {
        return packetsReceived;
    }

    public long getPacketsLost() {
        return packetsLost;
    }

    // tính tỉ lệ mất gói
    public double getLossPercent() {
        long total = packetsReceived + packetsLost;
        if (total == 0) return 0.0;
        return (packetsLost * 100.0) / total;
    }
}
