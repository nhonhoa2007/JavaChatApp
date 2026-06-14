package org.example.client.controller;

import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.stage.Stage;
import javafx.util.Duration;
import org.example.client.call.CallEventListener;
import org.example.client.call.CallManager;
import org.example.client.call.CallSession;

// Controller cho màn hình cuộc gọi đang diễn ra (timer, mute, end)
public class CallViewController implements CallEventListener {

    @FXML private Label lblStatus;
    @FXML private Label lblPeerName;
    @FXML private Label lblTimer;
    @FXML private Label lblCallType;
    @FXML private Button btnMute;
    @FXML private Button btnEnd;

    private CallSession session;
    private Timeline timer;
    private int elapsedSeconds;
    private boolean muted;

    public void initData(CallSession session) {
        this.session = session;
        lblPeerName.setText(session.peerUsername);
        lblCallType.setText("VOICE".equals(session.type) ? "Cuộc gọi thoại" : "Cuộc gọi video");

        if (session.state == CallSession.State.ACTIVE) {
            lblStatus.setText("Đã kết nối");
            startTimer();
        } else {
            lblStatus.setText("Đang kết nối...");
        }

        CallManager.getInstance().addListener(this);
    }

    @Override
    public void onCallActive(CallSession s) {
        Platform.runLater(() -> {
            lblStatus.setText("Đã kết nối");
            startTimer();
        });
    }

    @Override
    public void onCallEnded(CallSession s, String reason) {
        Platform.runLater(() -> {
            stopTimer();
            CallManager.getInstance().removeListener(this);
            closeWindow();
        });
    }

    @FXML
    public void handleMute(ActionEvent event) {
        muted = !muted;
        CallManager.getInstance().setMuted(muted);
        btnMute.setText(muted ? "🔇" : "🎤");
        btnMute.setStyle(muted
                ? "-fx-background-color: #ef4444; -fx-text-fill: white; -fx-font-size: 20px; -fx-min-width: 56; -fx-min-height: 56; -fx-background-radius: 28; -fx-cursor: hand;"
                : "-fx-background-color: #334155; -fx-text-fill: white; -fx-font-size: 20px; -fx-min-width: 56; -fx-min-height: 56; -fx-background-radius: 28; -fx-cursor: hand;");
    }

    @FXML
    public void handleEnd(ActionEvent event) {
        stopTimer();
        CallManager.getInstance().removeListener(this);
        CallManager.getInstance().endCall();
        closeWindow();
    }

    // Gọi từ bên ngoài khi peer kết thúc cuộc gọi
    public void forceClose() {
        Platform.runLater(() -> {
            stopTimer();
            CallManager.getInstance().removeListener(this);
            closeWindow();
        });
    }

    private void startTimer() {
        if (timer != null) return;
        elapsedSeconds = 0;
        timer = new Timeline(new KeyFrame(Duration.seconds(1), e -> {
            elapsedSeconds++;
            int min = elapsedSeconds / 60;
            int sec = elapsedSeconds % 60;
            lblTimer.setText(String.format("%02d:%02d", min, sec));
        }));
        timer.setCycleCount(Timeline.INDEFINITE);
        timer.play();
    }

    private void stopTimer() {
        if (timer != null) {
            timer.stop();
            timer = null;
        }
    }

    private void closeWindow() {
        Stage stage = (Stage) lblPeerName.getScene().getWindow();
        if (stage != null) stage.close();
    }
}
