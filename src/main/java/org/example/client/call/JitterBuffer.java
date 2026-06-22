package org.example.client.call;

import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

// đệm jitter cho luồng âm thanh
// giảm dao động thời gian giữa các gói udp
// đệm trước 5 frame trước khi phát
// bỏ frame cũ khi đầy và lặp frame cuối khi thiếu dữ liệu
public class JitterBuffer {

    private static final int CAPACITY = 20;
    private static final int MIN_PREBUFFER = 5;

    private final LinkedBlockingQueue<byte[]> queue = new LinkedBlockingQueue<>(CAPACITY);
    private volatile boolean prebuffering = true;
    private byte[] lastFrame;

    // nhận frame từ udp và đưa vào buffer
    public void put(byte[] frame) {
        if (!queue.offer(frame)) {
            queue.poll(); // bỏ frame cũ nhất khi đầy
            queue.offer(frame);
        }
        if (prebuffering && queue.size() >= MIN_PREBUFFER) {
            prebuffering = false;
        }
    }

    // lấy frame ra để phát
    public byte[] take() {
        if (prebuffering) {
            return null;
        }
        byte[] frame = queue.poll();
        if (frame == null) {
            prebuffering = true;
            return lastFrame; // lặp frame cuối khi thiếu dữ liệu
        }
        lastFrame = frame;
        return frame;
    }

    // lấy frame với timeout chờ
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
