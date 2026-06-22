package org.example.client.util;

import javax.sound.sampled.AudioFileFormat;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.TargetDataLine;
import javax.sound.sampled.AudioInputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

public class VoiceRecorder {
    private static final AudioFormat FORMAT = new AudioFormat(16000.0f, 16, 1, true, false);

    private final Object lock = new Object();
    private volatile boolean recording;
    private TargetDataLine line;
    private ByteArrayOutputStream rawAudio;
    private Thread captureThread;

    public boolean isRecording() {
        return recording;
    }

    public boolean start() throws LineUnavailableException {
        synchronized (lock) {
            if (recording) {
                return false;
            }

            rawAudio = new ByteArrayOutputStream();
            DataLine.Info info = new DataLine.Info(TargetDataLine.class, FORMAT);
            line = (TargetDataLine) AudioSystem.getLine(info);
            line.open(FORMAT);
            line.start();
            recording = true;

            TargetDataLine currentLine = line;
            ByteArrayOutputStream currentAudio = rawAudio;
            captureThread = new Thread(() -> captureLoop(currentLine, currentAudio), "voice-recorder");
            captureThread.setDaemon(true);
            captureThread.start();
            return true;
        }
    }

    public byte[] stop() throws IOException {
        TargetDataLine currentLine;
        ByteArrayOutputStream output;
        Thread thread;

        synchronized (lock) {
            if (!recording) {
                return new byte[0];
            }
            recording = false;
            currentLine = line;
            output = rawAudio;
            thread = captureThread;
            line = null;
            rawAudio = null;
            captureThread = null;
        }

        if (currentLine != null) {
            currentLine.stop();
            currentLine.close();
        }

        if (thread != null) {
            try {
                thread.join(500);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        byte[] pcmBytes = output != null ? output.toByteArray() : new byte[0];
        if (pcmBytes.length == 0) {
            return new byte[0];
        }

        long frameLength = pcmBytes.length / FORMAT.getFrameSize();
        try (AudioInputStream audioInputStream = new AudioInputStream(new ByteArrayInputStream(pcmBytes), FORMAT, frameLength);
             ByteArrayOutputStream wavOut = new ByteArrayOutputStream()) {
            AudioSystem.write(audioInputStream, AudioFileFormat.Type.WAVE, wavOut);
            return wavOut.toByteArray();
        }
    }

    private void captureLoop(TargetDataLine currentLine, ByteArrayOutputStream output) {
        byte[] buffer = new byte[4096];
        try {
            while (recording) {
                int read = currentLine.read(buffer, 0, buffer.length);
                if (read > 0) {
                    output.write(buffer, 0, read);
                }
            }
        } catch (Exception ex) {
            // bỏ qua lỗi ghi khi đang tắt recorder
        }
    }
}


