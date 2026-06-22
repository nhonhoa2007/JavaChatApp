package org.example.client.call;

import java.io.ByteArrayInputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.SocketTimeoutException;

// nhận video qua udp và giải mã jpeg cho giao diện
public class VideoPlaybackThread extends Thread {

    public interface FrameListener {
        void onFrameReceived(javafx.scene.image.Image image);
    }

    private final DatagramSocket socket;
    private volatile boolean running = true;
    private FrameListener frameListener;

    // thống kê gói tin
    private long packetsReceived = 0;
    private int lastSeq = -1;
    private long packetsLost = 0;

    public VideoPlaybackThread(DatagramSocket socket) {
        super("video-playback");
        setDaemon(true);
        this.socket = socket;
    }

    public void setFrameListener(FrameListener listener) {
        this.frameListener = listener;
    }

    public long getPacketsReceived() {
        return packetsReceived;
    }

    public long getPacketsLost() {
        return packetsLost;
    }

    @Override
    public void run() {
        System.out.println("[VideoPlayback] Started listening on local port " + socket.getLocalPort());
        try {
            socket.setSoTimeout(200);
            byte[] buffer = new byte[65535];
            DatagramPacket packet = new DatagramPacket(buffer, buffer.length);

            while (running && !socket.isClosed()) {
                try {
                    socket.receive(packet);
                    int length = packet.getLength();

                    if (length >= 4) {
                        // trích xuất số thứ tự gói
                        int seq = ((buffer[0] & 0xFF) << 24)
                                | ((buffer[1] & 0xFF) << 16)
                                | ((buffer[2] & 0xFF) << 8)
                                | (buffer[3] & 0xFF);

                        if (seq <= lastSeq && lastSeq >= 0) {
                            // bỏ qua gói tin đến muộn
                            continue;
                        }

                        if (lastSeq >= 0 && seq > lastSeq + 1) {
                            packetsLost += (seq - lastSeq - 1);
                        }
                        lastSeq = seq;
                        packetsReceived++;

                        // trích xuất dữ liệu ảnh jpeg
                        int jpegLength = length - 4;
                        byte[] jpegBytes = new byte[jpegLength];
                        System.arraycopy(buffer, 4, jpegBytes, 0, jpegLength);

                        // giải mã jpeg sang ảnh javafx
                        javafx.scene.image.Image fxImage = new javafx.scene.image.Image(new ByteArrayInputStream(jpegBytes));

                        if (frameListener != null) {
                            frameListener.onFrameReceived(fxImage);
                        }
                    }
                } catch (SocketTimeoutException ste) {
                    // timeout bình thường khi chưa có gói tin
                } catch (Exception e) {
                    if (running) {
                        System.err.println("[VideoPlayback] Receive error: " + e.getMessage());
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("[VideoPlayback] Thread error: " + e.getMessage());
        } finally {
            System.out.println("[VideoPlayback] Stopped — received=" + packetsReceived + ", lost=" + packetsLost);
        }
    }

    public void shutdown() {
        running = false;
    }
}
