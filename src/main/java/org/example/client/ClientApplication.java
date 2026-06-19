package org.example.client;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;
import org.example.client.call.CallManager;
import org.example.client.network.ChatClient;

public class ClientApplication extends Application {

    private static ChatClient chatClient;
    public static void main(String[] args) {
        launch(args);
    }

    @Override
    public void start(Stage primaryStage) throws Exception {

        chatClient = new ChatClient();
        boolean isConnected = chatClient.connect("localhost", 8888);

        if (!isConnected) {
            System.err.println("Không thể kết nối tới Server. Đang thoát...");
            System.exit(1);
        }
        CallManager.init(chatClient);

        //load giao diện
        Parent root = FXMLLoader.load(getClass().getResource("/fxml/Login.fxml"));
        
        primaryStage.setTitle("Java Chat - Login");
        primaryStage.setScene(new Scene(root, 400, 400));
        primaryStage.setResizable(false);
        

        primaryStage.setOnCloseRequest(event -> {
            chatClient.disconnect();
            Platform.exit();
            System.exit(0);
        });

        primaryStage.show();
    }

    public static ChatClient getChatClient() {
        return chatClient;
    }
}
