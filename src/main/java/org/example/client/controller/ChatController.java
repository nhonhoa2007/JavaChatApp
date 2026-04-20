package org.example.client.controller;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.text.Text;
import javafx.scene.text.TextFlow;
import javafx.stage.FileChooser;
import javafx.util.Duration;
import org.controlsfx.control.Notifications;
import org.example.client.ClientApplication;
import org.example.common.network.Packet;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public class ChatController {

    @FXML
    private ListView<String> listUsers;

    @FXML
    private ListView<String> listRequests;

    @FXML
    private ListView<Object> listMessages;

    @FXML
    private TextArea txtMessage;

    @FXML
    private TextField txtAddFriend;

    @FXML
    private Label lblChatTitle;

    @FXML
    private Button btnBlock;

    @FXML
    private Button btnMute;

    private String currentUsername;
    private final Map<Long, Integer> messageIdToIndexMap = new HashMap<>();

    private final Map<String, Boolean> isBlockedByMeMap = new HashMap<>();
    private final Map<String, Boolean> isMutedByMeMap = new HashMap<>();

    private String extractUsername(String displayItem) {
        if (displayItem == null) return null;
        if (displayItem.endsWith(" [Online]")) {
            return displayItem.substring(0, displayItem.length() - 9);
        } else if (displayItem.endsWith(" [Offline]")) {
            return displayItem.substring(0, displayItem.length() - 10);
        }
        return displayItem;
    }

    public void initialize() {
        ClientApplication.getChatClient().setOnPacketReceived(this::handleServerResponse);
        
        listUsers.getSelectionModel().selectedItemProperty().addListener((observable, oldValue, newValue) -> {
            String selectedUser = extractUsername(newValue);
            if (selectedUser != null) {
                lblChatTitle.setText("Chat với: " + selectedUser);
                loadHistory(selectedUser);
                updateControlButtons(selectedUser);
            } else {
                lblChatTitle.setText("Chọn một người bạn để bắt đầu trò chuyện");
                listMessages.getItems().clear();
                messageIdToIndexMap.clear();
                if (btnBlock != null) btnBlock.setVisible(false);
                if (btnMute != null) btnMute.setVisible(false);
            }
        });

        setupMessageContextMenu();
        setupRequestContextMenu();
    }

    private void updateControlButtons(String targetUser) {
        if (btnBlock == null || btnMute == null) return;
        btnBlock.setVisible(true);
        btnMute.setVisible(true);

        boolean isBlocked = isBlockedByMeMap.getOrDefault(targetUser, false);
        if (isBlocked) {
            btnBlock.setText("Bỏ Chặn");
            btnBlock.setStyle("-fx-background-color: #4CAF50; -fx-text-fill: white; -fx-cursor: hand;");
        } else {
            btnBlock.setText("Chặn");
            btnBlock.setStyle("-fx-background-color: #f44336; -fx-text-fill: white; -fx-cursor: hand;");
        }

        boolean isMuted = isMutedByMeMap.getOrDefault(targetUser, false);
        if (isMuted) {
            btnMute.setText("Bật TB");
            btnMute.setStyle("-fx-background-color: #4CAF50; -fx-text-fill: white; -fx-cursor: hand;");
        } else {
            btnMute.setText("Tắt TB");
            btnMute.setStyle("-fx-background-color: #ff9800; -fx-text-fill: white; -fx-cursor: hand;");
        }
    }

    private void setupRequestContextMenu() {
        ContextMenu contextMenu = new ContextMenu();
        MenuItem acceptItem = new MenuItem("Chấp nhận kết bạn");
        acceptItem.setOnAction(e -> handleAcceptFriend());
        contextMenu.getItems().add(acceptItem);

        listRequests.setCellFactory(lv -> {
            ListCell<String> cell = new ListCell<>();
            cell.textProperty().bind(cell.itemProperty());
            cell.emptyProperty().addListener((obs, wasEmpty, isNowEmpty) -> {
                if (isNowEmpty) {
                    cell.setContextMenu(null);
                } else {
                    cell.setContextMenu(contextMenu);
                }
            });
            return cell;
        });
    }

    private void setupMessageContextMenu() {
        ContextMenu contextMenu = new ContextMenu();
        
        MenuItem recallItem = new MenuItem("Thu hồi tin nhắn");
        recallItem.setOnAction(e -> handleRecallMessage());

        MenuItem editItem = new MenuItem("Chỉnh sửa tin nhắn");
        editItem.setOnAction(e -> handleEditMessageUI());

        contextMenu.getItems().addAll(editItem, recallItem);

        listMessages.setCellFactory(lv -> {
            return new ListCell<Object>() {
                @Override
                protected void updateItem(Object item, boolean empty) {
                    super.updateItem(item, empty);
                    if (empty || item == null) {
                        setText(null);
                        setGraphic(null);
                        setContextMenu(null);
                        setStyle("-fx-background-color: transparent;");
                    } else if (item instanceof HBox) {
                        HBox container = (HBox) item;
                        setText(null);
                        setGraphic(container);
                        
                        // Check alignment to set context menu for "Bạn"
                        if (container.getAlignment() == Pos.CENTER_RIGHT) {
                            setContextMenu(contextMenu);
                        } else {
                            setContextMenu(null);
                        }
                        setStyle("-fx-background-color: transparent; -fx-padding: 5px;");
                    }
                }
            };
        });
    }

    private void loadHistory(String otherUser) {
        listMessages.getItems().clear();
        messageIdToIndexMap.clear();
        JsonObject payload = new JsonObject();
        payload.addProperty("otherUser", otherUser);
        payload.addProperty("limit", 50); // Lấy 50 tin nhắn gần nhất
        ClientApplication.getChatClient().sendPacket(new Packet("LOAD_HISTORY_REQUEST", payload.toString()));
    }

    public void initData(String username) {
        this.currentUsername = username;
    }

    private void handleServerResponse(Packet packet) {
        Platform.runLater(() -> {
            switch (packet.getType()) {
                case "STATUS_UPDATE":
                    handleStatusUpdate(packet.getPayload());
                    break;
                case "LOAD_FRIENDS_SUCCESS":
                    handleLoadFriendsSuccess(packet.getPayload());
                    break;
                case "FRIEND_REQUEST":
                    handleNewFriendRequest(packet.getPayload());
                    break;
                case "FRIEND_SUCCESS":
                    showAlert("Thông báo", packet.getPayload(), Alert.AlertType.INFORMATION);
                    txtAddFriend.clear();
                    break;
                case "FRIEND_ERROR":
                    showAlert("Lỗi Kết Bạn", packet.getPayload(), Alert.AlertType.ERROR);
                    break;
                case "RELOAD_FRIENDS":
                    ClientApplication.getChatClient().sendPacket(new Packet("LOAD_FRIENDS_REQUEST", ""));
                    break;
                case "BLOCK_SUCCESS":
                case "MUTE_SUCCESS":
                    showAlert("Thành công", packet.getPayload(), Alert.AlertType.INFORMATION);
                    break;
                case "CHAT_HISTORY":
                    handleChatHistory(packet.getPayload());
                    break;
                case "CHAT_ACK":
                    handleChatAck(packet.getPayload());
                    break;
                case "CHAT_MESSAGE":
                    handleIncomingMessage(packet.getPayload());
                    break;
                case "MESSAGE_RECALLED":
                    handleMessageRecalled(packet.getPayload());
                    break;
                case "MESSAGE_EDITED":
                    handleMessageEdited(packet.getPayload());
                    break;
                case "CHAT_ERROR":
                    showAlert("Lỗi Gửi Tin", packet.getPayload(), Alert.AlertType.ERROR);
                    break;
            }
        });
    }

    private void handleLoadFriendsSuccess(String payload) {
        JsonObject json = JsonParser.parseString(payload).getAsJsonObject();
        JsonArray friends = json.getAsJsonArray("friends");
        JsonArray requests = json.getAsJsonArray("requests");

        String selectedItem = listUsers.getSelectionModel().getSelectedItem();
        String actualSelected = extractUsername(selectedItem);

        listUsers.getItems().clear();
        isBlockedByMeMap.clear();
        isMutedByMeMap.clear();

        for (int i = 0; i < friends.size(); i++) {
            JsonObject f = friends.get(i).getAsJsonObject();
            String username = f.get("username").getAsString();
            String status = f.get("status").getAsString();
            
            if (f.has("isBlockedByMe")) {
                isBlockedByMeMap.put(username, f.get("isBlockedByMe").getAsBoolean());
            }
            if (f.has("isMutedByMe")) {
                isMutedByMeMap.put(username, f.get("isMutedByMe").getAsBoolean());
            }

            String displayText = username + (status.equals("ONLINE") ? " [Online]" : " [Offline]");
            listUsers.getItems().add(displayText);
        }

        // Logic check lại sau khi load xong danh sách mới
        if (actualSelected != null) {
            boolean stillExists = false;
            for(String item : listUsers.getItems()) {
                if(extractUsername(item).equals(actualSelected)) {
                    listUsers.getSelectionModel().select(item);
                    updateControlButtons(actualSelected);
                    stillExists = true;
                    break;
                }
            }
            
            if(!stillExists) {
                listMessages.getItems().clear();
                messageIdToIndexMap.clear();
                lblChatTitle.setText("Người dùng không còn tồn tại trong danh sách bạn bè");
                if (btnBlock != null) btnBlock.setVisible(false);
                if (btnMute != null) btnMute.setVisible(false);
            }
        }

        listRequests.getItems().clear();
        for (int i = 0; i < requests.size(); i++) {
            listRequests.getItems().add(requests.get(i).getAsString());
        }
    }

    private void handleNewFriendRequest(String payload) {
        JsonObject json = JsonParser.parseString(payload).getAsJsonObject();
        String from = json.get("from").getAsString();
        if (!listRequests.getItems().contains(from)) {
            listRequests.getItems().add(from);
        }
        showPushNotification("Hệ thống", from + " đã gửi lời mời kết bạn.");
    }

    private void handleStatusUpdate(String payload) {
        String[] parts = payload.split(":");
        if (parts.length == 2) {
            String user = parts[0];
            String status = parts[1];
            
            for (int i = 0; i < listUsers.getItems().size(); i++) {
                String item = listUsers.getItems().get(i);
                String actualUsername = extractUsername(item);
                if (actualUsername != null && actualUsername.equals(user)) {
                    String newDisplayText = actualUsername + (status.equals("ONLINE") ? " [Online]" : " [Offline]");
                    
                    boolean isSelected = listUsers.getSelectionModel().getSelectedIndex() == i;
                    
                    listUsers.getItems().set(i, newDisplayText);
                    
                    if (isSelected) {
                        listUsers.getSelectionModel().select(i);
                    }
                    break;
                }
            }
        }
    }

    @FXML
    public void handleAddFriend(ActionEvent event) {
        String friendUsername = txtAddFriend.getText().trim();
        if (friendUsername.isEmpty()) return;

        JsonObject payload = new JsonObject();
        payload.addProperty("friendUsername", friendUsername);
        ClientApplication.getChatClient().sendPacket(new Packet("ADD_FRIEND_REQUEST", payload.toString()));
    }

    private void handleAcceptFriend() {
        String selectedRequest = listRequests.getSelectionModel().getSelectedItem();
        if (selectedRequest != null) {
            JsonObject payload = new JsonObject();
            payload.addProperty("senderUsername", selectedRequest);
            ClientApplication.getChatClient().sendPacket(new Packet("ACCEPT_FRIEND_REQUEST", payload.toString()));
            listRequests.getItems().remove(selectedRequest);
        }
    }

    @FXML
    public void handleBlockUser(ActionEvent event) {
        String selectedUser = extractUsername(listUsers.getSelectionModel().getSelectedItem());
        if (selectedUser != null) {
            JsonObject payload = new JsonObject();
            payload.addProperty("targetUsername", selectedUser);
            ClientApplication.getChatClient().sendPacket(new Packet("BLOCK_USER_REQUEST", payload.toString()));
        } else {
            showAlert("Thông báo", "Vui lòng chọn một người bạn để chặn.", Alert.AlertType.WARNING);
        }
    }

    @FXML
    public void handleMuteUser(ActionEvent event) {
        String selectedUser = extractUsername(listUsers.getSelectionModel().getSelectedItem());
        if (selectedUser != null) {
            JsonObject payload = new JsonObject();
            payload.addProperty("targetUsername", selectedUser);
            ClientApplication.getChatClient().sendPacket(new Packet("MUTE_USER_REQUEST", payload.toString()));
        } else {
            showAlert("Thông báo", "Vui lòng chọn một người bạn để tắt thông báo.", Alert.AlertType.WARNING);
        }
    }

    private void handleChatHistory(String payload) {
        JsonObject json = JsonParser.parseString(payload).getAsJsonObject();
        String otherUser = json.get("otherUser").getAsString();
        
        String selectedUser = extractUsername(listUsers.getSelectionModel().getSelectedItem());
        if (selectedUser == null || !selectedUser.equals(otherUser)) {
            return;
        }

        JsonArray messages = json.getAsJsonArray("messages");
        for (int i = 0; i < messages.size(); i++) {
            JsonObject msg = messages.get(i).getAsJsonObject();
            long id = msg.get("id").getAsLong();
            String sender = msg.get("sender").getAsString();
            String content = msg.get("content").getAsString();
            String type = msg.get("type").getAsString();
            
            boolean isMe = sender.equals("Bạn");
            if (type.equals("IMAGE")) {
                listMessages.getItems().add(createImageMessageNode(sender, content, isMe));
            } else {
                listMessages.getItems().add(createTextMessageNode(sender, content, isMe));
            }
            messageIdToIndexMap.put(id, listMessages.getItems().size() - 1);
        }
    }

    private void handleChatAck(String payload) {
        JsonObject json = JsonParser.parseString(payload).getAsJsonObject();
        long messageId = json.get("id").getAsLong();
        String content = json.get("content").getAsString();
        String type = json.has("type") ? json.get("type").getAsString() : "TEXT";
        
        if (type.equals("IMAGE")) {
            listMessages.getItems().add(createImageMessageNode("Bạn", content, true));
        } else {
            listMessages.getItems().add(createTextMessageNode("Bạn", content, true));
        }
        
        messageIdToIndexMap.put(messageId, listMessages.getItems().size() - 1);
    }

    private void handleIncomingMessage(String payload) {
        JsonObject json = JsonParser.parseString(payload).getAsJsonObject();
        String sender = json.get("sender").getAsString();
        String content = json.get("content").getAsString();
        String type = json.has("type") ? json.get("type").getAsString() : "TEXT";
        
        String selectedUser = extractUsername(listUsers.getSelectionModel().getSelectedItem());
        if (selectedUser != null && selectedUser.equals(sender)) {
            if (type.equals("IMAGE")) {
                listMessages.getItems().add(createImageMessageNode(sender, content, false));
            } else {
                listMessages.getItems().add(createTextMessageNode(sender, content, false));
            }

            if (json.has("id")) {
                long messageId = json.get("id").getAsLong();
                messageIdToIndexMap.put(messageId, listMessages.getItems().size() - 1);
            }
        }

        boolean isMuted = json.has("isMuted") && json.get("isMuted").getAsBoolean();
        if (!isMuted) {
            String notiText = type.equals("IMAGE") ? "[Hình ảnh]" : content;
            showPushNotification(sender, notiText);
        }
    }

    // Helper method to create a modern text message UI node
    private HBox createTextMessageNode(String sender, String content, boolean isMe) {
        HBox container = new HBox();
        container.setSpacing(10);
        
        VBox messageBox = new VBox();
        messageBox.setSpacing(2);
        messageBox.setMaxWidth(400);

        Label senderLabel = new Label(sender);
        senderLabel.setStyle("-fx-font-size: 11px; -fx-text-fill: #888;");

        Text text = new Text(content);
        text.setStyle("-fx-font-size: 14px;");

        TextFlow textFlow = new TextFlow(text);
        textFlow.setPadding(new javafx.geometry.Insets(8, 12, 8, 12));
        
        if (isMe) {
            text.setFill(Color.WHITE);
            textFlow.setStyle("-fx-background-color: #2196F3; -fx-background-radius: 15px 15px 0px 15px;");
            messageBox.setAlignment(Pos.CENTER_RIGHT);
            container.setAlignment(Pos.CENTER_RIGHT);
            messageBox.getChildren().add(textFlow); // Don't show sender name for "Me"
        } else {
            text.setFill(Color.BLACK);
            textFlow.setStyle("-fx-background-color: #E0E0E0; -fx-background-radius: 15px 15px 15px 0px;");
            messageBox.setAlignment(Pos.CENTER_LEFT);
            container.setAlignment(Pos.CENTER_LEFT);
            messageBox.getChildren().addAll(senderLabel, textFlow);
        }

        // Store the original text as user data for context menu retrieval
        container.setUserData(content);
        
        container.getChildren().add(messageBox);
        return container;
    }

    // Helper method to create a modern image message UI node
    private HBox createImageMessageNode(String sender, String base64Content, boolean isMe) {
        HBox container = new HBox();
        container.setSpacing(10);
        
        VBox messageBox = new VBox();
        messageBox.setSpacing(2);
        
        Label senderLabel = new Label(sender);
        senderLabel.setStyle("-fx-font-size: 11px; -fx-text-fill: #888;");

        try {
            byte[] imageBytes = Base64.getDecoder().decode(base64Content);
            Image image = new Image(new ByteArrayInputStream(imageBytes));
            ImageView imageView = new ImageView(image);
            imageView.setFitWidth(200);
            imageView.setPreserveRatio(true);
            
            // Add a small border radius effect using CSS wrapper
            VBox imageWrapper = new VBox(imageView);
            imageWrapper.setPadding(new javafx.geometry.Insets(5));
            
            if (isMe) {
                imageWrapper.setStyle("-fx-background-color: #2196F3; -fx-background-radius: 10px 10px 0px 10px;");
                messageBox.setAlignment(Pos.CENTER_RIGHT);
                container.setAlignment(Pos.CENTER_RIGHT);
                messageBox.getChildren().add(imageWrapper);
            } else {
                imageWrapper.setStyle("-fx-background-color: #E0E0E0; -fx-background-radius: 10px 10px 10px 0px;");
                messageBox.setAlignment(Pos.CENTER_LEFT);
                container.setAlignment(Pos.CENTER_LEFT);
                messageBox.getChildren().addAll(senderLabel, imageWrapper);
            }
        } catch (Exception e) {
            Label errorLabel = new Label("[Lỗi hiển thị hình ảnh]");
            messageBox.getChildren().add(errorLabel);
        }

        container.setUserData(base64Content); // Store base64 just in case
        container.getChildren().add(messageBox);
        return container;
    }

    private void handleMessageRecalled(String payload) {
        JsonObject json = JsonParser.parseString(payload).getAsJsonObject();
        long messageId = json.get("messageId").getAsLong();

        if (messageIdToIndexMap.containsKey(messageId)) {
            int index = messageIdToIndexMap.get(messageId);
            if (index < listMessages.getItems().size()) {
                Object oldItem = listMessages.getItems().get(index);
                if (oldItem instanceof HBox) {
                    HBox oldContainer = (HBox) oldItem;
                    boolean isMe = oldContainer.getAlignment() == Pos.CENTER_RIGHT;
                    String sender = isMe ? "Bạn" : "Người dùng";
                    
                    HBox recalledNode = createTextMessageNode(sender, "🚫 Tin nhắn đã bị thu hồi", isMe);
                    listMessages.getItems().set(index, recalledNode);
                }
            }
        }
    }

    private void handleMessageEdited(String payload) {
        JsonObject json = JsonParser.parseString(payload).getAsJsonObject();
        long messageId = json.get("messageId").getAsLong();
        String newContent = json.get("newContent").getAsString();

        if (messageIdToIndexMap.containsKey(messageId)) {
            int index = messageIdToIndexMap.get(messageId);
            if (index < listMessages.getItems().size()) {
                Object oldItem = listMessages.getItems().get(index);
                if (oldItem instanceof HBox) {
                    HBox oldContainer = (HBox) oldItem;
                    boolean isMe = oldContainer.getAlignment() == Pos.CENTER_RIGHT;
                    String sender = isMe ? "Bạn" : "Người dùng"; // Simplified
                    
                    HBox editedNode = createTextMessageNode(sender, newContent + " (Đã chỉnh sửa)", isMe);
                    // Store original un-appended newContent for further edits
                    editedNode.setUserData(newContent); 
                    listMessages.getItems().set(index, editedNode);
                }
            }
        }
    }

    @FXML
    public void handleSendMessage(ActionEvent event) {
        String content = txtMessage.getText().trim();
        if (content.isEmpty()) return;

        JsonObject payload = new JsonObject();
        payload.addProperty("content", content);
        payload.addProperty("type", "TEXT");
        
        String selectedUser = extractUsername(listUsers.getSelectionModel().getSelectedItem());
        if (selectedUser != null) {
            payload.addProperty("receiver", selectedUser);
            Packet chatPacket = new Packet("PRIVATE_MESSAGE", payload.toString());
            ClientApplication.getChatClient().sendPacket(chatPacket);
        } else {
            showAlert("Thông báo", "Vui lòng chọn một người bạn để gửi tin nhắn.", Alert.AlertType.WARNING);
        }
        
        txtMessage.clear();
    }

    @FXML
    public void handleSendImage(ActionEvent event) {
        String selectedUser = extractUsername(listUsers.getSelectionModel().getSelectedItem());
        if (selectedUser == null) {
            showAlert("Thông báo", "Vui lòng chọn một người bạn để gửi ảnh.", Alert.AlertType.WARNING);
            return;
        }

        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Chọn hình ảnh để gửi");
        fileChooser.getExtensionFilters().add(
            new FileChooser.ExtensionFilter("Image Files", "*.png", "*.jpg", "*.jpeg", "*.gif")
        );
        
        File selectedFile = fileChooser.showOpenDialog(txtMessage.getScene().getWindow());
        if (selectedFile != null) {
            try {
                if (selectedFile.length() > 2 * 1024 * 1024) {
                    showAlert("Lỗi", "Vui lòng chọn ảnh có kích thước nhỏ hơn 2MB.", Alert.AlertType.ERROR);
                    return;
                }

                byte[] fileContent = Files.readAllBytes(selectedFile.toPath());
                String encodedString = Base64.getEncoder().encodeToString(fileContent);

                JsonObject payload = new JsonObject();
                payload.addProperty("content", encodedString);
                payload.addProperty("type", "IMAGE");
                payload.addProperty("receiver", selectedUser);

                Packet chatPacket = new Packet("PRIVATE_MESSAGE", payload.toString());
                ClientApplication.getChatClient().sendPacket(chatPacket);
                
            } catch (IOException e) {
                showAlert("Lỗi", "Không thể đọc file hình ảnh.", Alert.AlertType.ERROR);
                e.printStackTrace();
            }
        }
    }

    @FXML
    public void handleSendVoice(ActionEvent event) {
        showAlert("Thông báo", "Tính năng gửi Voice chat sẽ được cập nhật trong phiên bản sau.", Alert.AlertType.INFORMATION);
    }

    private void handleRecallMessage() {
        int selectedIndex = listMessages.getSelectionModel().getSelectedIndex();
        if (selectedIndex == -1) return;

        Long messageIdToRecall = findMessageIdByIndex(selectedIndex);
        if (messageIdToRecall != null) {
            JsonObject payload = new JsonObject();
            payload.addProperty("messageId", messageIdToRecall);
            ClientApplication.getChatClient().sendPacket(new Packet("RECALL_MESSAGE", payload.toString()));
            
            // Update local UI immediately
            HBox recalledNode = createTextMessageNode("Bạn", "🚫 Tin nhắn đã bị thu hồi", true);
            listMessages.getItems().set(selectedIndex, recalledNode);
        } else {
            showAlert("Không thể thu hồi", "Tin nhắn này không hỗ trợ thu hồi (chưa có ID từ Server).", Alert.AlertType.WARNING);
        }
    }

    private void handleEditMessageUI() {
        int selectedIndex = listMessages.getSelectionModel().getSelectedIndex();
        if (selectedIndex == -1) return;

        Long messageIdToEdit = findMessageIdByIndex(selectedIndex);
        if (messageIdToEdit != null) {
            Object oldItem = listMessages.getItems().get(selectedIndex);
            if (!(oldItem instanceof HBox)) return;
            
            HBox oldContainer = (HBox) oldItem;
            Object userData = oldContainer.getUserData();
            
            // Allow editing only if it's text (we saved raw content as UserData for text)
            if (userData == null || userData.toString().startsWith("/9j/")) { // "/9j/" is typical base64 prefix for images
                showAlert("Thông báo", "Chỉ hỗ trợ chỉnh sửa tin nhắn văn bản.", Alert.AlertType.WARNING);
                return;
            }
            
            String oldContent = userData.toString();

            TextInputDialog dialog = new TextInputDialog(oldContent);
            dialog.setTitle("Chỉnh sửa tin nhắn");
            dialog.setHeaderText("Nhập nội dung mới cho tin nhắn của bạn:");
            dialog.setContentText("Nội dung:");

            Optional<String> result = dialog.showAndWait();
            result.ifPresent(newContent -> {
                if (!newContent.trim().isEmpty() && !newContent.equals(oldContent)) {
                    JsonObject payload = new JsonObject();
                    payload.addProperty("messageId", messageIdToEdit);
                    payload.addProperty("newContent", newContent);
                    ClientApplication.getChatClient().sendPacket(new Packet("EDIT_MESSAGE", payload.toString()));
                    
                    HBox editedNode = createTextMessageNode("Bạn", newContent + " (Đã chỉnh sửa)", true);
                    editedNode.setUserData(newContent); // Store new raw content
                    listMessages.getItems().set(selectedIndex, editedNode);
                }
            });
        } else {
            showAlert("Không thể chỉnh sửa", "Tin nhắn này không hỗ trợ chỉnh sửa (chưa có ID từ Server).", Alert.AlertType.WARNING);
        }
    }

    private Long findMessageIdByIndex(int index) {
        for (Map.Entry<Long, Integer> entry : messageIdToIndexMap.entrySet()) {
            if (entry.getValue() == index) {
                return entry.getKey();
            }
        }
        return null;
    }

    private void showPushNotification(String title, String text) {
        Notifications.create()
                .title("Tin nhắn mới từ " + title)
                .text(text)
                .hideAfter(Duration.seconds(5))
                .showInformation();
    }

    private void showAlert(String title, String content, Alert.AlertType type) {
        Alert alert = new Alert(type);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(content);
        alert.showAndWait();
    }
}
