package org.example.client.controller;

import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.Label;
import javafx.scene.image.ImageView;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;
import javafx.util.Duration;
import org.example.client.call.CallEventListener;
import org.example.client.call.CallManager;
import org.example.client.call.CallSession;
import org.example.client.util.IconUtil;

// Điều khiển màn hình cuộc gọi đang diễn ra
public class CallViewController implements CallEventListener {

    @FXML private StackPane rootPane;
    @FXML private Label lblStatus;
    @FXML private Label lblPeerName;
    @FXML private Label lblTimer;
    @FXML private Label lblCallType;
    @FXML private Label lblAvatar;
    @FXML private Button btnMute;
    @FXML private Button btnCamera;
    @FXML private Button btnEnd;

    @FXML private ImageView imgRemoteVideo;
    @FXML private ImageView imgLocalVideo;

    private CallSession session;
    private Timeline timer;
    private int elapsedSeconds;
    private boolean muted;
    private boolean cameraMuted;

    public void initialize() {
        lblAvatar.setText(null);
        lblAvatar.setGraphic(IconUtil.createIcon("/icon/user.svg", "call-avatar-icon", 2.5));
        setButtonIcon(btnMute, "/icon/mic.svg", "call-control-icon", 1.15);
        setButtonIcon(btnCamera, "/icon/video.svg", "call-control-icon", 1.15);
        setButtonIcon(btnEnd, "/icon/phone-off.svg", "call-control-icon", 1.25);
    }

    public void initData(CallSession session) {
        this.session = session;
        lblPeerName.setText(session.peerUsername);

        boolean isVideo = "VIDEO".equals(session.type);
        lblCallType.setText(isVideo ? "Cuộc gọi video" : "Cuộc gọi thoại");

        if (isVideo) {
            btnCamera.setVisible(true);
            btnCamera.setManaged(true);

            // Cấu hình kích cỡ khung video.
            imgRemoteVideo.setFitWidth(640);
            imgRemoteVideo.setFitHeight(480);

            // Chỉnh kích thước của cửa sổ cho cuộc gọi video.
            Platform.runLater(() -> {
                Stage stage = (Stage) rootPane.getScene().getWindow();
                if (stage != null) {
                    stage.setWidth(656);
                    stage.setHeight(520);
                    stage.setResizable(false);
                    stage.centerOnScreen();
                }
            });
        }

        if (session.state == CallSession.State.ACTIVE) {
            lblStatus.setText("Đã kết nối");
            startTimer();
            registerVideoListeners();
        } else {
            lblStatus.setText("Đang kết nối...");
        }

        CallManager.getInstance().addListener(this);
    }

    private void registerVideoListeners() {
        if (session == null) return;

        // Đăng ký nhận frame cục bộ từ camera.
        if (session.videoCaptureThread != null) {
            session.videoCaptureThread.setFrameListener(img -> {
                Platform.runLater(() -> {
                    imgLocalVideo.setImage(img);
                    if (!imgLocalVideo.isVisible()) {
                        imgLocalVideo.setVisible(true);
                    }
                });
            });
        }

        // Đăng ký nhận frame từ người gọi còn lại.
        if (session.videoPlaybackThread != null) {
            session.videoPlaybackThread.setFrameListener(img -> {
                Platform.runLater(() -> {
                    imgRemoteVideo.setImage(img);
                    if (!imgRemoteVideo.isVisible()) {
                        imgRemoteVideo.setVisible(true);
                    }
                    if (lblAvatar.isVisible()) {
                        lblAvatar.setVisible(false);
                    }
                });
            });
        }
    }

    @Override
    public void onCallActive(CallSession s) {
        Platform.runLater(() -> {
            lblStatus.setText("Đã kết nối");
            startTimer();
            registerVideoListeners();
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
        setButtonIcon(btnMute, muted ? "/icon/volume-off.svg" : "/icon/mic.svg", "call-control-icon", 1.15);
        btnMute.setStyle(muted
                ? "-fx-background-color: #ef4444; -fx-text-fill: white; -fx-font-size: 20px; -fx-min-width: 56; -fx-min-height: 56; -fx-background-radius: 28; -fx-cursor: hand;"
                : "-fx-background-color: #334155; -fx-text-fill: white; -fx-font-size: 20px; -fx-min-width: 56; -fx-min-height: 56; -fx-background-radius: 28; -fx-cursor: hand;");
    }

    @FXML
    public void handleCameraToggle(ActionEvent event) {
        cameraMuted = !cameraMuted;
        CallManager.getInstance().setVideoMuted(cameraMuted);
        setButtonIcon(btnCamera, cameraMuted ? "/icon/camera-off.svg" : "/icon/video.svg", "call-control-icon", 1.15);
        btnCamera.setStyle(cameraMuted
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

    public void forceClose() {
        Platform.runLater(() -> {
            stopTimer();
            CallManager.getInstance().removeListener(this);
            closeWindow();
        });
    }

    private void setButtonIcon(Button button, String resourcePath, String styleClass, double scale) {
        if (button == null) return;
        button.setText(null);
        button.setGraphic(IconUtil.createIcon(resourcePath, styleClass, scale));
        button.setContentDisplay(ContentDisplay.GRAPHIC_ONLY);
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
