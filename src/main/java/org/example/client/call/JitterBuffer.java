package org.example.client.call;

import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

// Jitter buffer cho audio streaming.
// Hấp thụ biến thiên thời gian giữa các UDP packet.
// Prebuffer 5 frame (100ms) trước khi bắt đầu phát.
// Overflow: drop oldest. Underrun: PLC (repeat last frame).
public class JitterBuffer {

    private static final int CAPACITY = 20;
    private static final int MIN_PREBUFFER = 5;

    private final LinkedBlockingQueue<byte[]> queue = new LinkedBlockingQueue<>(CAPACITY);
    private volatile boolean prebuffering = true;
    private byte[] lastFrame;

    // Producer: nhận frame từ UDP, đưa vào buffer
    public void put(byte[] frame) {
        if (!queue.offer(frame)) {
            queue.poll(); // drop oldest nếu đầy
            queue.offer(frame);
        }
        if (prebuffering && queue.size() >= MIN_PREBUFFER) {
            prebuffering = false;
        }
    }

    // Consumer: lấy frame ra để phát
    public byte[] take() {
        if (prebuffering) {
            return null;
        }
        byte[] frame = queue.poll();
        if (frame == null) {
            prebuffering = true;
            return lastFrame; // PLC
        }
        lastFrame = frame;
        return frame;
    }

    // Consumer blocking: lấy frame với timeout
    public byte[] take(long timeoutMs) {
        if (prebuffering) {
            try { Thread.sleep(timeoutMs); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
            return null;
        }
        try {
            byte[] frame = queue.poll(timeoutMs, TimeUnit.MILLISECONDS);
            if (frame == null) {
                prebuffering = true;
                return lastFrame;
            }
            lastFrame = frame;
            return frame;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
        }
    }

    public void clear() {
        queue.clear();
        prebuffering = true;
        lastFrame = null;
    }

    public int size() {
        return queue.size();
    }

    public boolean isPrebuffering() {
        return prebuffering;
    }
}
