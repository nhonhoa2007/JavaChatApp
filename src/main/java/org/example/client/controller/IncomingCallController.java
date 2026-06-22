package org.example.client.controller;

import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.stage.Stage;
import org.example.client.call.CallManager;
import org.example.client.call.CallSession;
import org.example.client.call.RingtonePlayer;
import org.example.common.network.CallPacketTypes;

// điều khiển popup nhận cuộc gọi đến
public class IncomingCallController {

    @FXML private Label lblCallerName;
    @FXML private Label lblCallType;

    private CallSession session;

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

    // đóng popup khi cuộc gọi bị hủy hoặc timeout
    public void forceClose() {
        RingtonePlayer.getInstance().stop();
        closeWindow();
    }

    private void closeWindow() {
        Stage stage = (Stage) lblCallerName.getScene().getWindow();
        if (stage != null) stage.close();
    }
}
