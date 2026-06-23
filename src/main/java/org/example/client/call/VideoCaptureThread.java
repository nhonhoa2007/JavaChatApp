package org.example.client.call;

import com.github.sarxos.webcam.Webcam;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;

// thu hình webcam, nén jpeg và gửi qua udp
public class VideoCaptureThread extends Thread {

    public interface FrameListener {
        void onFrameCaptured(javafx.scene.image.Image image);
    }

    private static final int WIDTH = 640;
    private static final int HEIGHT = 480;
    private static final int FPS = 15;
    private static final long FRAME_DELAY = 1000 / FPS; // độ trễ giữa hai frame

    private final DatagramSocket socket;
    private final InetAddress peerAddress;
    private final int peerPort;
    private final String myUsername;

    private volatile boolean running = true;
    private volatile boolean videoMuted = false;

    private byte[] callIdPrefix = null;
    private int sequenceNumber = 0;
    private FrameListener frameListener;

    public VideoCaptureThread(DatagramSocket socket, InetAddress peerAddress, int peerPort, String myUsername) {
        super("video-capture");
        setDaemon(true);
        this.socket = socket;
        this.peerAddress = peerAddress;
        this.peerPort = peerPort;
        this.myUsername = myUsername;
    }

    public void setRelayCallId(String callId) {
        if (callId != null && callId.length() >= 8) {
            this.callIdPrefix = callId.substring(0, 8).getBytes();
        }
    }

    public void setFrameListener(FrameListener listener) {
        this.frameListener = listener;
    }

    public void setVideoMuted(boolean videoMuted) {
        this.videoMuted = videoMuted;
    }

    public boolean isVideoMuted() {
        return videoMuted;
    }

    @Override
    public void run() {
        Webcam webcam = null;
        try {
            java.util.List<Webcam> webcams = Webcam.getWebcams();
            if (webcams != null && !webcams.isEmpty()) {
                System.out.println("[VideoCapture] Found " + webcams.size() + " webcams. Trying to open one...");
                for (Webcam w : webcams) {
                    // cấu hình kích cỡ nếu webcam hỗ trợ
                    java.awt.Dimension size = new java.awt.Dimension(WIDTH, HEIGHT);
                    java.awt.Dimension[] supported = w.getViewSizes();
                    boolean supported640 = false;
                    for (var s : supported) {
                        if (s.width == WIDTH && s.height == HEIGHT) {
                            supported640 = true;
                            break;
                        }
                    }
                    if (supported640) {
                        w.setViewSize(size);
                    }

                    try {
                        w.open();
                        if (w.isOpen()) {
                            webcam = w;
                            System.out.println("[VideoCapture] Webcam opened successfully: " + w.getName());
                            break;
                        }
                    } catch (Throwable t) {
                        System.out.println("[VideoCapture] Webcam " + w.getName() + " is busy or failed to open: " + t.getMessage());
                    }
                }
            } else {
                System.out.println("[VideoCapture] No webcams found on system.");
            }
        } catch (Throwable t) {
            System.err.println("[VideoCapture] Failed to check/initialize webcams: " + t.getMessage() + ". Using simulated frames.");
            webcam = null;
        }


        int prefixSize = (callIdPrefix != null) ? callIdPrefix.length : 0;
        int headerSize = 4; // kích thước số thứ tự

        System.out.println("[VideoCapture] Started video streaming — target=" + peerAddress.getHostAddress() + ":" + peerPort);

        try {
            while (running && !socket.isClosed()) {
                long startTime = System.currentTimeMillis();
                BufferedImage frame = null;

                if (videoMuted) {
                    frame = generateMutedFrame(WIDTH, HEIGHT);
                } else if (webcam != null && webcam.isOpen()) {
                    frame = webcam.getImage();
                }

                // tạo frame giả khi không có webcam hoặc capture lỗi
                if (frame == null) {
                    frame = generateMockFrame(WIDTH, HEIGHT, sequenceNumber);
                }

                // nén ảnh thành jpeg
                byte[] jpegBytes = compressToJpeg(frame, 0.65f);
                if (jpegBytes != null && jpegBytes.length < 60000) {
                    int totalSize = prefixSize + headerSize + jpegBytes.length;
                    byte[] sendBuffer = new byte[totalSize];

                    int offset = 0;
                    if (callIdPrefix != null) {
                        System.arraycopy(callIdPrefix, 0, sendBuffer, 0, callIdPrefix.length);
                        offset += callIdPrefix.length;
                    }

                    // ghi số thứ tự frame
                    sendBuffer[offset]     = (byte) ((sequenceNumber >> 24) & 0xFF);
                    sendBuffer[offset + 1] = (byte) ((sequenceNumber >> 16) & 0xFF);
                    sendBuffer[offset + 2] = (byte) ((sequenceNumber >> 8) & 0xFF);
                    sendBuffer[offset + 3] = (byte) (sequenceNumber & 0xFF);
                    offset += 4;

                    // ghi dữ liệu jpeg
                    System.arraycopy(jpegBytes, 0, sendBuffer, offset, jpegBytes.length);

                    DatagramPacket packet = new DatagramPacket(sendBuffer, totalSize, peerAddress, peerPort);
                    socket.send(packet);

                    // trả preview cục bộ cho giao diện
                    if (frameListener != null) {
                        javafx.scene.image.Image fxImage = new javafx.scene.image.Image(new ByteArrayInputStream(jpegBytes));
                        frameListener.onFrameCaptured(fxImage);
                    }

                    sequenceNumber++;
                }

                long elapsed = System.currentTimeMillis() - startTime;
                long sleepTime = FRAME_DELAY - elapsed;
                if (sleepTime > 0) {
                    Thread.sleep(sleepTime);
                }
            }
        } catch (InterruptedException ie) {
            // dừng luồng
        } catch (Exception e) {
            if (running) {
                System.err.println("[VideoCapture] Error in loop: " + e.getMessage());
            }
        } finally {
            if (webcam != null && webcam.isOpen()) {
                try {
                    webcam.close();
                    System.out.println("[VideoCapture] Webcam closed");
                } catch (Exception e) {
                    System.err.println("[VideoCapture] Error closing webcam: " + e.getMessage());
                }
            }
        }
    }

    public void shutdown() {
        running = false;
        this.interrupt();
    }

    private byte[] compressToJpeg(BufferedImage img, float quality) {
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            ImageWriter writer = ImageIO.getImageWritersByFormatName("jpg").next();
            ImageWriteParam param = writer.getDefaultWriteParam();
            param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
            param.setCompressionQuality(quality);

            try (var ios = ImageIO.createImageOutputStream(baos)) {
                writer.setOutput(ios);
                writer.write(null, new IIOImage(img, null, null), param);
            }
            writer.dispose();
            return baos.toByteArray();
        } catch (Exception e) {
            System.err.println("[VideoCapture] Compression error: " + e.getMessage());
            return null;
        }
    }

    private BufferedImage generateMockFrame(int width, int height, int seq) {
        BufferedImage img = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        java.awt.Graphics2D g = img.createGraphics();

        // vẽ nền chuyển màu động
        java.awt.Color color1 = java.awt.Color.getHSBColor((seq % 360) / 360.0f, 0.6f, 0.4f);
        java.awt.Color color2 = java.awt.Color.darkGray;
        g.setPaint(new java.awt.GradientPaint(0, 0, color1, width, height, color2));
        g.fillRect(0, 0, width, height);

        // vẽ chữ thông tin
        g.setColor(java.awt.Color.WHITE);
        g.setFont(new java.awt.Font("Segoe UI", java.awt.Font.BOLD, 22));
        g.drawString("CAMERA GIẢ LẬP (Không có phần cứng)", 50, height / 2 - 20);
        g.setFont(new java.awt.Font("Segoe UI", java.awt.Font.PLAIN, 16));
        g.drawString("Tài khoản: " + myUsername, 50, height / 2 + 15);
        g.drawString("Frame: " + seq + " | FPS: " + FPS, 50, height / 2 + 40);

        // vẽ hình tròn chuyển động
        int size = 60;
        int x = (int) (width / 2 + Math.cos(seq * 0.1) * (width / 3.5) - size / 2);
        int y = (int) (height / 2 + Math.sin(seq * 0.1) * (height / 3.5) - size / 2);
        g.setColor(java.awt.Color.GREEN);
        g.fillOval(x, y, size, size);
        g.setColor(java.awt.Color.WHITE);
        g.setStroke(new java.awt.BasicStroke(2.0f));
        g.drawOval(x, y, size, size);

        g.dispose();
        return img;
    }

    private BufferedImage generateMutedFrame(int width, int height) {
        BufferedImage img = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        java.awt.Graphics2D g = img.createGraphics();

        g.setColor(java.awt.Color.BLACK);
        g.fillRect(0, 0, width, height);

        g.setColor(java.awt.Color.LIGHT_GRAY);
        g.setFont(new java.awt.Font("Segoe UI", java.awt.Font.BOLD, 24));
        g.drawString("CAMERA ĐÃ TẮT", width / 2 - 100, height / 2);

        g.dispose();
        return img;
    }
}
