package org.example.client.controller;

import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.Label;
import javafx.stage.Stage;
import org.example.client.call.CallManager;
import org.example.client.call.CallSession;
import org.example.client.call.RingtonePlayer;
import org.example.client.util.IconUtil;
import org.example.common.network.CallPacketTypes;

// Điều khiển popup nhận cuộc gọi đến
public class IncomingCallController {

    @FXML private Label lblIncomingIcon;
    @FXML private Label lblCallerName;
    @FXML private Label lblCallType;
    @FXML private Button btnReject;
    @FXML private Button btnAccept;

    private CallSession session;

    public void initialize() {
        lblIncomingIcon.setText(null);
        lblIncomingIcon.setGraphic(IconUtil.createIcon("/icon/phone.svg", "incoming-call-icon", 2.3));
        setButtonGraphic(btnReject, "/icon/x.svg", "incoming-action-icon", "Từ chối");
        setButtonGraphic(btnAccept, "/icon/check.svg", "incoming-action-icon", "Chấp nhận");
    }

    public void initData(CallSession session) {
        this.session = session;
        lblCallerName.setText(session.peerUsername);
        lblCallType.setText("VOICE".equals(session.type) ? "Cuộc gọi thoại" : "Cuộc gọi video");
        RingtonePlayer.getInstance().start();
    }

    @FXML
    public void handleAccept(ActionEvent event) {
        RingtonePlayer.getInstance().stop();
        CallManager.getInstance().acceptCall();
        closeWindow();
    }

    @FXML
    public void handleReject(ActionEvent event) {
        RingtonePlayer.getInstance().stop();
        CallManager.getInstance().rejectCall(CallPacketTypes.REASON_USER_REJECT);
        closeWindow();
    }

    // Đóng popup khi cuộc gọi bị hủy hoặc timeout.
    public void forceClose() {
        RingtonePlayer.getInstance().stop();
        closeWindow();
    }

    private void setButtonGraphic(Button button, String resourcePath, String styleClass, String text) {
        button.setText(text);
        button.setGraphic(IconUtil.createIcon(resourcePath, styleClass, 0.85));
        button.setContentDisplay(ContentDisplay.LEFT);
        button.setGraphicTextGap(8);
    }

    private void closeWindow() {
        Stage stage = (Stage) lblCallerName.getScene().getWindow();
        if (stage != null) stage.close();
    }
}
