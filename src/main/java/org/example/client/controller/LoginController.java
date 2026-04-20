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

public class LoginController {

    @FXML
    private TextField txtUsername;

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
                case "LOGIN_SUCCESS":
                    lblStatus.setStyle("-fx-text-fill: green;");
                    lblStatus.setText(packet.getPayload());
                    
                    try {
                        FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/Chat.fxml"));
                        Parent chatRoot = loader.load();
                        ChatController chatController = loader.getController();
                        chatController.initData(txtUsername.getText().trim());

                        Stage stage = (Stage) txtUsername.getScene().getWindow();
                        stage.setScene(new Scene(chatRoot, 700, 500));
                        stage.centerOnScreen();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                    break;
                case "LOGIN_ERROR":
                    lblStatus.setStyle("-fx-text-fill: red;");
                    lblStatus.setText(packet.getPayload());
                    break;
            }
        });
    }

    @FXML
    public void handleLogin(ActionEvent event) {
        String username = txtUsername.getText().trim();
        String password = txtPassword.getText();

        if (username.isEmpty() || password.isEmpty()) {
            lblStatus.setText("Vui lòng nhập đầy đủ thông tin!");
            return;
        }

        JsonObject payload = new JsonObject();
        payload.addProperty("username", username);
        payload.addProperty("password", password);

        Packet loginPacket = new Packet("LOGIN_REQUEST", payload.toString());
        ClientApplication.getChatClient().sendPacket(loginPacket);
    }

    @FXML
    public void handleRegister(ActionEvent event) {
        try {
            Parent registerRoot = FXMLLoader.load(getClass().getResource("/fxml/Register.fxml"));
            Stage stage = (Stage) txtUsername.getScene().getWindow();
            stage.setScene(new Scene(registerRoot, 400, 400));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
