package org.example.client.call;

import javax.sound.sampled.*;
import java.io.BufferedInputStream;
import java.io.InputStream;

// phát nhạc chuông lặp lại khi có cuộc gọi đến
public class RingtonePlayer {

    private static final RingtonePlayer INSTANCE = new RingtonePlayer();
    private Clip clip;
    private volatile boolean playing;

    private RingtonePlayer() {}

    public static RingtonePlayer getInstance() {
        return INSTANCE;
    }

    // phát nhạc chuông nếu chưa phát
    public synchronized void start() {
        if (playing) return;
        try {
            InputStream is = getClass().getResourceAsStream("/audio/ringtone.wav");
            if (is == null) {
                // phát tiếng beep nếu không có file
                System.out.println("[RingtonePlayer] ringtone.wav not found, using system beep");
                playing = true;
                new Thread(() -> {
                    while (playing) {
                        java.awt.Toolkit.getDefaultToolkit().beep();
                        try { Thread.sleep(2000); } catch (InterruptedException e) { break; }
                    }
                }, "ringtone-beep").start();
                return;
            }

            AudioInputStream audioStream = AudioSystem.getAudioInputStream(new BufferedInputStream(is));
            clip = AudioSystem.getClip();
            clip.open(audioStream);
            clip.loop(Clip.LOOP_CONTINUOUSLY);
            clip.start();
            playing = true;
        } catch (Exception e) {
            System.err.println("[RingtonePlayer] Error: " + e.getMessage());
        }
    }

    // dừng nhạc chuông
    public synchronized void stop() {
        playing = false;
        if (clip != null) {
            clip.stop();
            clip.close();
            clip = null;
        }
    }

    public boolean isPlaying() {
        return playing;
    }
}
