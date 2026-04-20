package org.example.client.controller;

import com.google.gson.JsonObject;
import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.stage.Stage;
import org.example.client.ClientApplication;
import org.example.common.network.Packet;

import java.io.IOException;

public class RegisterController {

    @FXML
    private TextField txtUsername;

    @FXML
    private TextField txtFullName;

    @FXML
    private PasswordField txtPassword;

    @FXML
    private Label lblStatus;

    public void initialize() {
        ClientApplication.getChatClient().setOnPacketReceived(this::handleServerResponse);
    }

    private void handleServerResponse(Packet packet) {
        Platform.runLater(() -> {
            switch (packet.getType()) {
                case "REGISTER_SUCCESS":
                    lblStatus.setStyle("-fx-text-fill: green;");
                    lblStatus.setText(packet.getPayload() + " (Đang quay lại Đăng nhập...)");
                    new Thread(() -> {
                        try {
                            Thread.sleep(1500);
                            Platform.runLater(() -> goBackToLogin(null));
                        } catch (InterruptedException e) {}
                    }).start();
                    break;
                case "REGISTER_ERROR":
                    lblStatus.setStyle("-fx-text-fill: red;");
                    lblStatus.setText(packet.getPayload());
                    break;
            }
        });
    }

    @FXML
    public void handleRegister(ActionEvent event) {
        String username = txtUsername.getText().trim();
        String password = txtPassword.getText();
        String fullName = txtFullName.getText().trim();

        if (username.isEmpty() || password.isEmpty() || fullName.isEmpty()) {
            lblStatus.setText("Vui lòng điền đủ thông tin!");
            return;
        }

        JsonObject payload = new JsonObject();
        payload.addProperty("username", username);
        payload.addProperty("password", password);
        payload.addProperty("fullName", fullName);

        Packet registerPacket = new Packet("REGISTER_REQUEST", payload.toString());
        ClientApplication.getChatClient().sendPacket(registerPacket);
    }

    @FXML
    public void goBackToLogin(ActionEvent event) {
        try {
            Parent loginRoot = FXMLLoader.load(getClass().getResource("/fxml/Login.fxml"));
            Stage stage = (Stage) txtUsername.getScene().getWindow();
            stage.setScene(new Scene(loginRoot, 400, 400));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
