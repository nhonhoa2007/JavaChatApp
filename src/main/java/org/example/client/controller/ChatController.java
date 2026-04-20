package org.example.client.controller;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
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
                    } else if (item instanceof String) {
                        String text = (String) item;
                        setText(text);
                        setGraphic(null);
                        if (text.startsWith("Bạn")) {
                            setContextMenu(contextMenu);
                        } else {
                            setContextMenu(null);
                        }
                    } else if (item instanceof HBox) {
                        setText(null);
                        setGraphic((HBox) item);
                        setContextMenu(null); // Không support sửa/thu hồi hình ảnh tạm thời
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
            // Nếu người đang chọn vẫn nằm trong list mới (mình chặn họ thì vẫn còn, nhưng họ chặn mình thì mình bị mất)
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
            
            if (type.equals("IMAGE")) {
                listMessages.getItems().add(createImageNode(sender, content));
            } else {
                String displayText = sender + ": " + content;
                listMessages.getItems().add(displayText);
            }
            messageIdToIndexMap.put(id, listMessages.getItems().size() - 1);
        }
    }

    private void handleChatAck(String payload) {
        JsonObject json = JsonParser.parseString(payload).getAsJsonObject();
        long messageId = json.get("id").getAsLong();
        String content = json.get("content").getAsString();
        String receiver = json.get("receiver").getAsString();
        String type = json.has("type") ? json.get("type").getAsString() : "TEXT";
        
        if (type.equals("IMAGE")) {
            listMessages.getItems().add(createImageNode("Bạn", content));
        } else {
            String displayText = "Bạn (tới " + receiver + "): " + content;
            listMessages.getItems().add(displayText);
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
                listMessages.getItems().add(createImageNode(sender, content));
            } else {
                String displayText = sender + ": " + content;
                listMessages.getItems().add(displayText);
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

    private HBox createImageNode(String sender, String base64Content) {
        HBox hbox = new HBox(5);
        try {
            byte[] imageBytes = Base64.getDecoder().decode(base64Content);
            Image image = new Image(new ByteArrayInputStream(imageBytes));
            ImageView imageView = new ImageView(image);
            imageView.setFitWidth(150);
            imageView.setPreserveRatio(true);
            
            Label senderLabel = new Label(sender + ": ");
            senderLabel.setStyle("-fx-font-weight: bold;");
            
            hbox.getChildren().addAll(senderLabel, imageView);
        } catch (Exception e) {
            hbox.getChildren().add(new Label(sender + ": [Lỗi hiển thị hình ảnh]"));
        }
        return hbox;
    }

    private void handleMessageRecalled(String payload) {
        JsonObject json = JsonParser.parseString(payload).getAsJsonObject();
        long messageId = json.get("messageId").getAsLong();

        if (messageIdToIndexMap.containsKey(messageId)) {
            int index = messageIdToIndexMap.get(messageId);
            if (index < listMessages.getItems().size()) {
                Object oldItem = listMessages.getItems().get(index);
                if (oldItem instanceof String) {
                    String oldText = (String) oldItem;
                    String senderPrefix = oldText.substring(0, oldText.indexOf(":") + 1);
                    listMessages.getItems().set(index, senderPrefix + " [Tin nhắn đã bị thu hồi]");
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
                if (oldItem instanceof String) {
                    String oldText = (String) oldItem;
                    String senderPrefix = oldText.substring(0, oldText.indexOf(":") + 1);
                    listMessages.getItems().set(index, senderPrefix + " " + newContent + " (Đã chỉnh sửa)");
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
            
            Object oldItem = listMessages.getItems().get(selectedIndex);
            if (oldItem instanceof String) {
                String oldText = (String) oldItem;
                String prefix = oldText.substring(0, oldText.indexOf(":") + 1);
                listMessages.getItems().set(selectedIndex, prefix + " [Bạn đã thu hồi tin nhắn này]");
            }
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
            if (!(oldItem instanceof String)) {
                showAlert("Thông báo", "Chỉ hỗ trợ chỉnh sửa tin nhắn văn bản.", Alert.AlertType.WARNING);
                return;
            }
            
            String oldText = (String) oldItem;
            int colonIndex = oldText.indexOf(":");
            if(colonIndex == -1) return;
            
            String oldContent = oldText.substring(colonIndex + 2);
            if (oldContent.endsWith(" (Đã chỉnh sửa)")) {
                oldContent = oldContent.replace(" (Đã chỉnh sửa)", "");
            }

            TextInputDialog dialog = new TextInputDialog(oldContent);
            dialog.setTitle("Chỉnh sửa tin nhắn");
            dialog.setHeaderText("Nhập nội dung mới cho tin nhắn của bạn:");
            dialog.setContentText("Nội dung:");

            Optional<String> result = dialog.showAndWait();
            String finalOldContent = oldContent;
            result.ifPresent(newContent -> {
                if (!newContent.trim().isEmpty() && !newContent.equals(finalOldContent)) {
                    JsonObject payload = new JsonObject();
                    payload.addProperty("messageId", messageIdToEdit);
                    payload.addProperty("newContent", newContent);
                    ClientApplication.getChatClient().sendPacket(new Packet("EDIT_MESSAGE", payload.toString()));
                    
                    String prefix = oldText.substring(0, colonIndex + 1);
                    listMessages.getItems().set(selectedIndex, prefix + " " + newContent + " (Đã chỉnh sửa)");
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
