package org.example.client.controller;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.text.Text;
import javafx.scene.text.TextFlow;
import javafx.stage.FileChooser;
import javafx.util.Duration;
import org.controlsfx.control.Notifications;
import org.example.client.ClientApplication;
import org.example.client.util.VoicePlayer;
import org.example.client.util.VoiceRecorder;
import org.example.common.network.Packet;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import javax.sound.sampled.LineUnavailableException;

public class ChatController {

    @FXML
    private ListView<String> listUsers;

    @FXML
    private ListView<String> listRequests;

    @FXML
    private ListView<String> listGroups;

    @FXML
    private ListView<Object> listMessages;

    @FXML
    private TextArea txtMessage;

    @FXML
    private TextField txtAddFriend;

    @FXML
    private TextField txtSearchFriend;

    @FXML
    private TextField txtGroupName;

    @FXML
    private TextField txtGroupMembers;

    @FXML
    private Label lblChatTitle;

    @FXML
    private Button btnBlock;

    @FXML
    private Button btnMute;

    @FXML
    private Button btnVoice;

    private String currentUsername;
    private final Map<Long, Integer> messageIdToIndexMap = new HashMap<>();
    private final ObservableList<String> allFriendItems = FXCollections.observableArrayList();
    private final Map<String, Long> groupDisplayToId = new HashMap<>();
    private final Map<Long, String> messageConversationMap = new HashMap<>();
    private final Map<Long, Label> reactionLabelMap = new HashMap<>();

    private final Map<String, Boolean> isBlockedByMeMap = new HashMap<>();
    private final Map<String, Boolean> isMutedByMeMap = new HashMap<>();
    private final VoiceRecorder voiceRecorder = new VoiceRecorder();
    private String voiceRecordingTarget;
    private String voiceRecordingConversation;
    private String currentConversationKey;

    private record MessageData(Long messageId, String type, String content, boolean isMe, String senderDisplay) {}

    private String extractUsername(String displayItem) {
        if (displayItem == null) return null;
        if (displayItem.endsWith(" [Online]")) {
            return displayItem.substring(0, displayItem.length() - 9);
        } else if (displayItem.endsWith(" [Offline]")) {
            return displayItem.substring(0, displayItem.length() - 10);
        }
        return displayItem;
    }

    private String buildFriendDisplayText(String username, String status) {
        return username + ("ONLINE".equals(status) ? " [Online]" : " [Offline]");
    }

    private String privateConversationKey(String username) {
        return "PRIVATE:" + username;
    }

    private String groupConversationKey(long groupId) {
        return "GROUP:" + groupId;
    }

    private Long extractGroupIdFromDisplay(String displayItem) {
        return groupDisplayToId.get(displayItem);
    }

    private void clearChatPaneForConversation(String title) {
        lblChatTitle.setText(title);
        listMessages.getItems().clear();
        messageIdToIndexMap.clear();
        messageConversationMap.clear();
        reactionLabelMap.clear();
    }

    private void applyFriendFilter() {
        String selectedUsername = extractUsername(listUsers.getSelectionModel().getSelectedItem());
        String keyword = txtSearchFriend == null ? "" : txtSearchFriend.getText().trim().toLowerCase();

        listUsers.getItems().setAll(
                allFriendItems.stream()
                        .filter(item -> keyword.isEmpty() || extractUsername(item).toLowerCase().contains(keyword))
                        .toList()
        );

        if (selectedUsername != null) {
            for (String item : listUsers.getItems()) {
                if (selectedUsername.equals(extractUsername(item))) {
                    listUsers.getSelectionModel().select(item);
                    break;
                }
            }
        }
    }

    private void updateFriendStatusInMaster(String username, String status) {
        for (int i = 0; i < allFriendItems.size(); i++) {
            String item = allFriendItems.get(i);
            String actualUsername = extractUsername(item);
            if (username.equals(actualUsername)) {
                allFriendItems.set(i, buildFriendDisplayText(actualUsername, status));
                return;
            }
        }
    }

    public void initialize() {
        ClientApplication.getChatClient().setOnPacketReceived(this::handleServerResponse);

        if (txtSearchFriend != null) {
            txtSearchFriend.textProperty().addListener((obs, oldValue, newValue) -> applyFriendFilter());
        }
        
        listUsers.getSelectionModel().selectedItemProperty().addListener((observable, oldValue, newValue) -> {
            String selectedUser = extractUsername(newValue);
            if (selectedUser != null) {
                if (listGroups != null) {
                    listGroups.getSelectionModel().clearSelection();
                }
                currentConversationKey = privateConversationKey(selectedUser);
                lblChatTitle.setText("Chat với: " + selectedUser);
                loadHistory(selectedUser);
                updateControlButtons(selectedUser);
            } else {
                clearChatPaneForConversation("Chọn cuộc trò chuyện để bắt đầu");
                currentConversationKey = null;
                if (btnBlock != null) btnBlock.setVisible(true);
                if (btnMute != null) btnMute.setVisible(false);
            }
        });

        if (listGroups != null) {
            listGroups.getSelectionModel().selectedItemProperty().addListener((obs, oldValue, newValue) -> {
                if (newValue != null) {
                    listUsers.getSelectionModel().clearSelection();
                    Long groupId = extractGroupIdFromDisplay(newValue);
                    if (groupId != null) {
                        currentConversationKey = groupConversationKey(groupId);
                        lblChatTitle.setText("Nhóm: " + newValue);
                        loadGroupHistory(groupId);
                        if (btnBlock != null) btnBlock.setVisible(false);
                        if (btnMute != null) btnMute.setVisible(false);
                    }
                }
            });
        }

        setupMessageContextMenu();
        setupRequestContextMenu();

        // Chủ động request danh sách bạn bè ngay khi ChatController đã sẵn sàng nhận response.
        // Trước đây server gửi LOAD_FRIENDS_SUCCESS ngay sau LOGIN_SUCCESS nhưng lúc đó
        // LoginController vẫn đang là handler → packet bị bỏ qua.
        ClientApplication.getChatClient().sendPacket(new Packet("LOAD_FRIENDS_REQUEST", ""));
        ClientApplication.getChatClient().sendPacket(new Packet("GROUP_LIST_REQUEST", ""));
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
        ContextMenu myMessageMenu = new ContextMenu();
        ContextMenu otherMessageMenu = new ContextMenu();

        MenuItem recallItem = new MenuItem("Thu hồi tin nhắn");
        recallItem.setOnAction(e -> handleRecallMessage());

        MenuItem editItem = new MenuItem("Chỉnh sửa tin nhắn");
        editItem.setOnAction(e -> handleEditMessageUI());

        Menu reactionMenuMine = buildReactionMenu();
        Menu reactionMenuOther = buildReactionMenu();
        MenuItem removeReactionMine = new MenuItem("Xóa cảm xúc");
        removeReactionMine.setOnAction(e -> handleRemoveReaction());
        MenuItem removeReactionOther = new MenuItem("Xóa cảm xúc");
        removeReactionOther.setOnAction(e -> handleRemoveReaction());

        myMessageMenu.getItems().addAll(editItem, recallItem, new SeparatorMenuItem(), reactionMenuMine, removeReactionMine);
        otherMessageMenu.getItems().addAll(reactionMenuOther, removeReactionOther);

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
                            setContextMenu(myMessageMenu);
                        } else {
                            setContextMenu(otherMessageMenu);
                        }
                        setStyle("-fx-background-color: transparent; -fx-padding: 5px;");
                    }
                }
            };
        });
    }

    private Menu buildReactionMenu() {
        Menu reactionMenu = new Menu("Thả cảm xúc");
        List<String> emojis = Arrays.asList("👍", "❤️", "😂", "😮", "😢", "🎉");
        for (String emoji : emojis) {
            MenuItem item = new MenuItem(emoji);
            item.setOnAction(e -> handleSetReaction(emoji));
            reactionMenu.getItems().add(item);
        }
        return reactionMenu;
    }

    private void loadHistory(String otherUser) {
        listMessages.getItems().clear();
        messageIdToIndexMap.clear();
        messageConversationMap.clear();
        reactionLabelMap.clear();
        JsonObject payload = new JsonObject();
        payload.addProperty("otherUser", otherUser);
        payload.addProperty("limit", 50); // Lấy 50 tin nhắn gần nhất
        ClientApplication.getChatClient().sendPacket(new Packet("LOAD_HISTORY_REQUEST", payload.toString()));
    }

    private void loadGroupHistory(long groupId) {
        listMessages.getItems().clear();
        messageIdToIndexMap.clear();
        messageConversationMap.clear();
        reactionLabelMap.clear();
        JsonObject payload = new JsonObject();
        payload.addProperty("groupId", groupId);
        payload.addProperty("limit", 50);
        ClientApplication.getChatClient().sendPacket(new Packet("GROUP_HISTORY_REQUEST", payload.toString()));
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
                case "GROUP_LIST":
                    handleGroupList(packet.getPayload());
                    break;
                case "GROUP_LIST_UPDATED":
                    ClientApplication.getChatClient().sendPacket(new Packet("GROUP_LIST_REQUEST", ""));
                    break;
                case "GROUP_SUCCESS":
                    showAlert("Thông báo", packet.getPayload(), Alert.AlertType.INFORMATION);
                    if (txtGroupName != null) txtGroupName.clear();
                    if (txtGroupMembers != null) txtGroupMembers.clear();
                    ClientApplication.getChatClient().sendPacket(new Packet("GROUP_LIST_REQUEST", ""));
                    break;
                case "GROUP_ERROR":
                    showAlert("Lỗi nhóm", packet.getPayload(), Alert.AlertType.ERROR);
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
                case "GROUP_HISTORY":
                    handleGroupHistory(packet.getPayload());
                    break;
                case "CHAT_ACK":
                    handleChatAck(packet.getPayload());
                    break;
                case "GROUP_MESSAGE_ACK":
                    handleGroupMessageAck(packet.getPayload());
                    break;
                case "CHAT_MESSAGE":
                    handleIncomingMessage(packet.getPayload());
                    break;
                case "GROUP_MESSAGE":
                    handleIncomingGroupMessage(packet.getPayload());
                    break;
                case "MESSAGE_RECALLED":
                    handleMessageRecalled(packet.getPayload());
                    break;
                case "MESSAGE_EDITED":
                    handleMessageEdited(packet.getPayload());
                    break;
                case "REACTION_UPDATED":
                    handleReactionUpdated(packet.getPayload());
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

        allFriendItems.clear();
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

            allFriendItems.add(buildFriendDisplayText(username, status));
        }

        applyFriendFilter();

        // Logic check lại sau khi load xong danh sách mới
        if (actualSelected != null) {
            boolean stillExists = false;
            for(String item : allFriendItems) {
                if(extractUsername(item).equals(actualSelected)) {
                    for (String visibleItem : listUsers.getItems()) {
                        if (extractUsername(visibleItem).equals(actualSelected)) {
                            listUsers.getSelectionModel().select(visibleItem);
                            break;
                        }
                    }
                    updateControlButtons(actualSelected);
                    stillExists = true;
                    break;
                }
            }
            
            if(!stillExists) {
                clearChatPaneForConversation("Người dùng không còn tồn tại trong danh sách bạn bè");
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

            String selectedUser = extractUsername(listUsers.getSelectionModel().getSelectedItem());
            updateFriendStatusInMaster(user, status);
            applyFriendFilter();

            if (selectedUser != null) {
                for (String item : listUsers.getItems()) {
                    if (selectedUser.equals(extractUsername(item))) {
                        listUsers.getSelectionModel().select(item);
                        break;
                    }
                }
            }
        }
    }

    private void handleGroupList(String payload) {
        if (listGroups == null) {
            return;
        }

        JsonObject json = JsonParser.parseString(payload).getAsJsonObject();
        JsonArray groups = json.getAsJsonArray("groups");

        String selected = listGroups.getSelectionModel().getSelectedItem();
        listGroups.getItems().clear();
        groupDisplayToId.clear();

        for (int i = 0; i < groups.size(); i++) {
            JsonObject g = groups.get(i).getAsJsonObject();
            long groupId = g.get("groupId").getAsLong();
            String name = g.get("groupName").getAsString();
            int memberCount = g.get("memberCount").getAsInt();
            String display = name + " (" + memberCount + ")";
            listGroups.getItems().add(display);
            groupDisplayToId.put(display, groupId);
        }

        if (selected != null && groupDisplayToId.containsKey(selected)) {
            listGroups.getSelectionModel().select(selected);
        }
    }

    private void handleGroupHistory(String payload) {
        JsonObject json = JsonParser.parseString(payload).getAsJsonObject();
        long groupId = json.get("groupId").getAsLong();
        String expectedConversation = groupConversationKey(groupId);
        if (!expectedConversation.equals(currentConversationKey)) {
            return;
        }

        JsonArray messages = json.getAsJsonArray("messages");
        for (int i = 0; i < messages.size(); i++) {
            JsonObject msg = messages.get(i).getAsJsonObject();
            long id = msg.get("id").getAsLong();
            String sender = msg.get("sender").getAsString();
            String content = msg.get("content").getAsString();
            String type = msg.get("type").getAsString();
            boolean isMe = "Bạn".equals(sender);

            HBox node = createMessageNodeByType(id, sender, content, isMe, type, msg.has("reactions") ? msg.getAsJsonArray("reactions") : null);
            listMessages.getItems().add(node);
            messageIdToIndexMap.put(id, listMessages.getItems().size() - 1);
            messageConversationMap.put(id, expectedConversation);
        }
    }

    private void handleGroupMessageAck(String payload) {
        JsonObject json = JsonParser.parseString(payload).getAsJsonObject();
        long messageId = json.get("id").getAsLong();
        long groupId = json.get("groupId").getAsLong();
        String conversationKey = groupConversationKey(groupId);

        if (!conversationKey.equals(currentConversationKey)) {
            return;
        }

        String content = json.get("content").getAsString();
        String type = json.has("type") ? json.get("type").getAsString() : "TEXT";
        HBox node = createMessageNodeByType(messageId, "Bạn", content, true, type, json.has("reactions") ? json.getAsJsonArray("reactions") : null);
        listMessages.getItems().add(node);
        messageIdToIndexMap.put(messageId, listMessages.getItems().size() - 1);
        messageConversationMap.put(messageId, conversationKey);
    }

    private void handleIncomingGroupMessage(String payload) {
        JsonObject json = JsonParser.parseString(payload).getAsJsonObject();
        long groupId = json.get("groupId").getAsLong();
        String conversationKey = groupConversationKey(groupId);

        long messageId = json.get("id").getAsLong();
        String sender = json.get("sender").getAsString();
        String content = json.get("content").getAsString();
        String type = json.has("type") ? json.get("type").getAsString() : "TEXT";

        if (conversationKey.equals(currentConversationKey)) {
            HBox node = createMessageNodeByType(messageId, sender, content, false, type, json.has("reactions") ? json.getAsJsonArray("reactions") : null);
            listMessages.getItems().add(node);
            messageIdToIndexMap.put(messageId, listMessages.getItems().size() - 1);
            messageConversationMap.put(messageId, conversationKey);
        }
        showPushNotification("Nhóm", sender + ": " + ("TEXT".equals(type) ? content : "[" + type + "]"));
    }

    private void handleReactionUpdated(String payload) {
        JsonObject json = JsonParser.parseString(payload).getAsJsonObject();
        long messageId = json.get("messageId").getAsLong();
        String conversationKey = messageConversationMap.get(messageId);
        if (conversationKey != null && !conversationKey.equals(currentConversationKey)) {
            return;
        }
        Label reactionLabel = reactionLabelMap.get(messageId);
        if (reactionLabel != null) {
            reactionLabel.setText(formatReactionSummary(json.getAsJsonArray("reactions")));
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

    @FXML
    public void handleCreateGroup(ActionEvent event) {
        if (txtGroupName == null || txtGroupMembers == null) {
            return;
        }

        String groupName = txtGroupName.getText().trim();
        String rawMembers = txtGroupMembers.getText().trim();

        if (groupName.isEmpty() || rawMembers.isEmpty()) {
            showAlert("Thông báo", "Vui lòng nhập tên nhóm và danh sách thành viên.", Alert.AlertType.WARNING);
            return;
        }

        JsonArray members = new JsonArray();
        Arrays.stream(rawMembers.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .distinct()
                .forEach(members::add);

        JsonObject payload = new JsonObject();
        payload.addProperty("groupName", groupName);
        payload.add("members", members);
        ClientApplication.getChatClient().sendPacket(new Packet("CREATE_GROUP_REQUEST", payload.toString()));
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
            listMessages.getItems().add(createMessageNodeByType(id, sender, content, isMe, type, msg.has("reactions") ? msg.getAsJsonArray("reactions") : null));
            messageIdToIndexMap.put(id, listMessages.getItems().size() - 1);
            messageConversationMap.put(id, privateConversationKey(otherUser));
        }
    }

    private void handleChatAck(String payload) {
        JsonObject json = JsonParser.parseString(payload).getAsJsonObject();
        long messageId = json.get("id").getAsLong();
        String content = json.get("content").getAsString();
        String type = json.has("type") ? json.get("type").getAsString() : "TEXT";
        String receiver = json.has("receiver") ? json.get("receiver").getAsString() : null;

        if (receiver == null) {
            return;
        }

        String conversationKey = privateConversationKey(receiver);
        if (!conversationKey.equals(currentConversationKey)) {
            return;
        }

        listMessages.getItems().add(createMessageNodeByType(messageId, "Bạn", content, true, type, json.has("reactions") ? json.getAsJsonArray("reactions") : null));

        messageIdToIndexMap.put(messageId, listMessages.getItems().size() - 1);
        messageConversationMap.put(messageId, conversationKey);
    }

    private void handleIncomingMessage(String payload) {
        JsonObject json = JsonParser.parseString(payload).getAsJsonObject();
        String sender = json.get("sender").getAsString();
        String content = json.get("content").getAsString();
        String type = json.has("type") ? json.get("type").getAsString() : "TEXT";
        
        String selectedUser = extractUsername(listUsers.getSelectionModel().getSelectedItem());
        if (selectedUser != null && selectedUser.equals(sender)) {
            if (json.has("id")) {
                long messageId = json.get("id").getAsLong();
                listMessages.getItems().add(createMessageNodeByType(messageId, sender, content, false, type, json.has("reactions") ? json.getAsJsonArray("reactions") : null));
                messageIdToIndexMap.put(messageId, listMessages.getItems().size() - 1);
                messageConversationMap.put(messageId, privateConversationKey(sender));
            } else {
                listMessages.getItems().add(createMessageNodeByType(null, sender, content, false, type, null));
            }
        }

        boolean isMuted = json.has("isMuted") && json.get("isMuted").getAsBoolean();
        if (!isMuted) {
            String notiText;
            if ("IMAGE".equals(type)) {
                notiText = "[Hình ảnh]";
            } else if ("VOICE".equals(type)) {
                notiText = "[Voice]";
            } else {
                notiText = content;
            }
            showPushNotification(sender, notiText);
        }
    }

    private HBox createMessageNodeByType(Long messageId, String sender, String content, boolean isMe, String type, JsonArray reactions) {
        if ("IMAGE".equals(type)) {
            return createImageMessageNode(messageId, sender, content, isMe, reactions);
        }
        if ("VOICE".equals(type)) {
            return createVoiceMessageNode(messageId, sender, content, isMe, reactions);
        }
        return createTextMessageNode(messageId, sender, content, isMe, reactions);
    }

    // Helper method to create a modern text message UI node
    private HBox createTextMessageNode(Long messageId, String sender, String content, boolean isMe, JsonArray reactions) {
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

        Label reactionLabel = createReactionLabel(messageId, reactions);
        messageBox.getChildren().add(reactionLabel);

        container.setUserData(new MessageData(messageId, "TEXT", content, isMe, sender));

        container.getChildren().add(messageBox);
        return container;
    }

    // Helper method to create a modern image message UI node
    private HBox createImageMessageNode(Long messageId, String sender, String base64Content, boolean isMe, JsonArray reactions) {
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

        Label reactionLabel = createReactionLabel(messageId, reactions);
        messageBox.getChildren().add(reactionLabel);

        container.setUserData(new MessageData(messageId, "IMAGE", base64Content, isMe, sender));
        container.getChildren().add(messageBox);
        return container;
    }

    private HBox createVoiceMessageNode(Long messageId, String sender, String base64Content, boolean isMe, JsonArray reactions) {
        HBox container = new HBox();
        container.setSpacing(10);

        VBox messageBox = new VBox();
        messageBox.setSpacing(2);
        messageBox.setMaxWidth(400);

        Label senderLabel = new Label(sender);
        senderLabel.setStyle("-fx-font-size: 11px; -fx-text-fill: #888;");

        Button playButton = new Button("▶ Phát voice");
        playButton.setStyle("-fx-background-color: white; -fx-text-fill: #2196F3; -fx-font-weight: bold; -fx-cursor: hand; -fx-background-radius: 10px;");
        playButton.setOnAction(e -> playVoiceMessage(base64Content));

        Label voiceLabel = new Label("Tin nhắn thoại");
        voiceLabel.setStyle("-fx-font-size: 13px;");

        HBox voiceBox = new HBox(8, playButton, voiceLabel);
        voiceBox.setPadding(new javafx.geometry.Insets(8, 12, 8, 12));

        if (isMe) {
            voiceLabel.setTextFill(Color.WHITE);
            voiceBox.setStyle("-fx-background-color: #2196F3; -fx-background-radius: 15px 15px 0px 15px;");
            messageBox.setAlignment(Pos.CENTER_RIGHT);
            container.setAlignment(Pos.CENTER_RIGHT);
            messageBox.getChildren().add(voiceBox);
        } else {
            voiceLabel.setTextFill(Color.BLACK);
            voiceBox.setStyle("-fx-background-color: #E0E0E0; -fx-background-radius: 15px 15px 15px 0px;");
            messageBox.setAlignment(Pos.CENTER_LEFT);
            container.setAlignment(Pos.CENTER_LEFT);
            messageBox.getChildren().addAll(senderLabel, voiceBox);
        }

        Label reactionLabel = createReactionLabel(messageId, reactions);
        messageBox.getChildren().add(reactionLabel);

        container.setUserData(new MessageData(messageId, "VOICE", base64Content, isMe, sender));
        container.getChildren().add(messageBox);
        return container;
    }

    private Label createReactionLabel(Long messageId, JsonArray reactions) {
        Label reactionLabel = new Label(formatReactionSummary(reactions));
        reactionLabel.setStyle("-fx-font-size: 11px; -fx-text-fill: #666;");
        if (messageId != null) {
            reactionLabelMap.put(messageId, reactionLabel);
        }
        return reactionLabel;
    }

    private String formatReactionSummary(JsonArray reactions) {
        if (reactions == null || reactions.size() == 0) {
            return "";
        }

        StringBuilder summary = new StringBuilder();
        for (int i = 0; i < reactions.size(); i++) {
            JsonObject item = reactions.get(i).getAsJsonObject();
            if (i > 0) {
                summary.append("  ");
            }
            summary.append(item.get("emoji").getAsString())
                    .append(" ")
                    .append(item.get("count").getAsInt());
        }
        return summary.toString();
    }

    private void playVoiceMessage(String base64Content) {
        new Thread(() -> {
            try {
                byte[] audioBytes = Base64.getDecoder().decode(base64Content);
                VoicePlayer.play(audioBytes);
            } catch (Exception e) {
                Platform.runLater(() -> showAlert("Lỗi phát voice", "Không thể phát tin nhắn thoại.", Alert.AlertType.ERROR));
            }
        }, "voice-player").start();
    }

    private void handleMessageRecalled(String payload) {
        JsonObject json = JsonParser.parseString(payload).getAsJsonObject();
        long messageId = json.get("messageId").getAsLong();
        String conversationKey = messageConversationMap.get(messageId);
        if (conversationKey != null && !conversationKey.equals(currentConversationKey)) {
            return;
        }

        if (messageIdToIndexMap.containsKey(messageId)) {
            int index = messageIdToIndexMap.get(messageId);
            if (index < listMessages.getItems().size()) {
                Object oldItem = listMessages.getItems().get(index);
                if (oldItem instanceof HBox) {
                    HBox oldContainer = (HBox) oldItem;
                    boolean isMe = oldContainer.getAlignment() == Pos.CENTER_RIGHT;
                    MessageData oldData = oldContainer.getUserData() instanceof MessageData ? (MessageData) oldContainer.getUserData() : null;
                    String sender = oldData != null ? oldData.senderDisplay() : (isMe ? "Bạn" : "Người dùng");

                    HBox recalledNode = createTextMessageNode(messageId, sender, "🚫 Tin nhắn đã bị thu hồi", isMe, null);
                    listMessages.getItems().set(index, recalledNode);
                }
            }
        }
    }

    private void handleMessageEdited(String payload) {
        JsonObject json = JsonParser.parseString(payload).getAsJsonObject();
        long messageId = json.get("messageId").getAsLong();
        String newContent = json.get("newContent").getAsString();
        String conversationKey = messageConversationMap.get(messageId);
        if (conversationKey != null && !conversationKey.equals(currentConversationKey)) {
            return;
        }

        if (messageIdToIndexMap.containsKey(messageId)) {
            int index = messageIdToIndexMap.get(messageId);
            if (index < listMessages.getItems().size()) {
                Object oldItem = listMessages.getItems().get(index);
                if (oldItem instanceof HBox) {
                    HBox oldContainer = (HBox) oldItem;
                    MessageData oldData = oldContainer.getUserData() instanceof MessageData ? (MessageData) oldContainer.getUserData() : null;
                    if (oldData == null || !"TEXT".equals(oldData.type())) {
                        showAlert("Thông báo", "Chỉ hỗ trợ chỉnh sửa tin nhắn văn bản.", Alert.AlertType.WARNING);
                        return;
                    }

                    boolean isMe = oldContainer.getAlignment() == Pos.CENTER_RIGHT;
                    String sender = oldData.senderDisplay();

                    HBox editedNode = createTextMessageNode(messageId, sender, newContent + " (Đã chỉnh sửa)", isMe, null);
                    // Store original un-appended newContent for further edits
                    editedNode.setUserData(new MessageData(messageId, "TEXT", newContent, isMe, sender));
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
        } else if (listGroups != null && listGroups.getSelectionModel().getSelectedItem() != null) {
            Long groupId = extractGroupIdFromDisplay(listGroups.getSelectionModel().getSelectedItem());
            if (groupId == null) {
                showAlert("Thông báo", "Không xác định được nhóm chat.", Alert.AlertType.WARNING);
                return;
            }
            payload.addProperty("groupId", groupId);
            ClientApplication.getChatClient().sendPacket(new Packet("GROUP_MESSAGE", payload.toString()));
        } else {
            showAlert("Thông báo", "Vui lòng chọn bạn bè hoặc nhóm để gửi tin nhắn.", Alert.AlertType.WARNING);
        }
        
        txtMessage.clear();
    }

    @FXML
    public void handleSendImage(ActionEvent event) {
        String selectedUser = extractUsername(listUsers.getSelectionModel().getSelectedItem());
        String selectedGroupDisplay = listGroups != null ? listGroups.getSelectionModel().getSelectedItem() : null;
        Long selectedGroupId = selectedGroupDisplay != null ? extractGroupIdFromDisplay(selectedGroupDisplay) : null;

        if (selectedUser == null && selectedGroupId == null) {
            showAlert("Thông báo", "Vui lòng chọn bạn bè hoặc nhóm để gửi ảnh.", Alert.AlertType.WARNING);
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
                Packet chatPacket;
                if (selectedUser != null) {
                    payload.addProperty("receiver", selectedUser);
                    chatPacket = new Packet("PRIVATE_MESSAGE", payload.toString());
                } else {
                    payload.addProperty("groupId", selectedGroupId);
                    chatPacket = new Packet("GROUP_MESSAGE", payload.toString());
                }
                ClientApplication.getChatClient().sendPacket(chatPacket);
                
            } catch (IOException e) {
                showAlert("Lỗi", "Không thể đọc file hình ảnh.", Alert.AlertType.ERROR);
                e.printStackTrace();
            }
        }
    }

    @FXML
    public void handleSendVoice(ActionEvent event) {
        String selectedUser = extractUsername(listUsers.getSelectionModel().getSelectedItem());
        String selectedGroupDisplay = listGroups != null ? listGroups.getSelectionModel().getSelectedItem() : null;
        Long selectedGroupId = selectedGroupDisplay != null ? extractGroupIdFromDisplay(selectedGroupDisplay) : null;

        if (!voiceRecorder.isRecording()) {
            if (selectedUser == null && selectedGroupId == null) {
                showAlert("Thông báo", "Vui lòng chọn bạn bè hoặc nhóm để gửi voice.", Alert.AlertType.WARNING);
                return;
            }

            try {
                if (voiceRecorder.start()) {
                    voiceRecordingTarget = selectedUser;
                    voiceRecordingConversation = selectedGroupId == null ? null : String.valueOf(selectedGroupId);
                    updateVoiceButton(true);
                }
            } catch (LineUnavailableException e) {
                updateVoiceButton(false);
                showAlert("Lỗi", "Không thể truy cập micro để ghi âm.", Alert.AlertType.ERROR);
            }
            return;
        }

        try {
            byte[] wavBytes = voiceRecorder.stop();
            updateVoiceButton(false);

            if (wavBytes.length == 0) {
                showAlert("Thông báo", "Không ghi được dữ liệu voice.", Alert.AlertType.WARNING);
                return;
            }

            if (wavBytes.length > 2 * 1024 * 1024) {
                showAlert("Lỗi", "Voice quá lớn. Vui lòng ghi ngắn hơn.", Alert.AlertType.ERROR);
                return;
            }

            if (voiceRecordingTarget == null) {
                if (voiceRecordingConversation == null) {
                    showAlert("Lỗi", "Không xác định được người nhận voice.", Alert.AlertType.ERROR);
                    return;
                }
            }

            JsonObject payload = new JsonObject();
            payload.addProperty("content", Base64.getEncoder().encodeToString(wavBytes));
            payload.addProperty("type", "VOICE");
            payload.addProperty("format", "WAV");
            Packet chatPacket;
            if (voiceRecordingTarget != null) {
                payload.addProperty("receiver", voiceRecordingTarget);
                chatPacket = new Packet("PRIVATE_MESSAGE", payload.toString());
            } else {
                payload.addProperty("groupId", Long.parseLong(voiceRecordingConversation));
                chatPacket = new Packet("GROUP_MESSAGE", payload.toString());
            }
            ClientApplication.getChatClient().sendPacket(chatPacket);
        } catch (Exception e) {
            updateVoiceButton(false);
            showAlert("Lỗi", "Không thể gửi voice.", Alert.AlertType.ERROR);
        } finally {
            voiceRecordingTarget = null;
            voiceRecordingConversation = null;
        }
    }

    private void updateVoiceButton(boolean recording) {
        if (btnVoice == null) return;

        if (recording) {
            btnVoice.setText("⏹");
            btnVoice.setStyle("-fx-font-size: 16px; -fx-background-color: #f44336; -fx-text-fill: white; -fx-cursor: hand;");
        } else {
            btnVoice.setText("🎤");
            btnVoice.setStyle("-fx-font-size: 16px; -fx-background-color: #e0e0e0; -fx-cursor: hand;");
        }
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
            HBox recalledNode = createTextMessageNode(messageIdToRecall, "Bạn", "🚫 Tin nhắn đã bị thu hồi", true, null);
            listMessages.getItems().set(selectedIndex, recalledNode);
        } else {
            showAlert("Không thể thu hồi", "Tin nhắn này không hỗ trợ thu hồi (chưa có ID từ Server).", Alert.AlertType.WARNING);
        }
    }

    private void handleSetReaction(String emoji) {
        int selectedIndex = listMessages.getSelectionModel().getSelectedIndex();
        if (selectedIndex == -1) {
            return;
        }

        Long messageId = findMessageIdByIndex(selectedIndex);
        if (messageId == null) {
            return;
        }

        JsonObject payload = new JsonObject();
        payload.addProperty("messageId", messageId);
        payload.addProperty("emoji", emoji);
        ClientApplication.getChatClient().sendPacket(new Packet("REACTION_SET_REQUEST", payload.toString()));
    }

    private void handleRemoveReaction() {
        int selectedIndex = listMessages.getSelectionModel().getSelectedIndex();
        if (selectedIndex == -1) {
            return;
        }

        Long messageId = findMessageIdByIndex(selectedIndex);
        if (messageId == null) {
            return;
        }

        JsonObject payload = new JsonObject();
        payload.addProperty("messageId", messageId);
        ClientApplication.getChatClient().sendPacket(new Packet("REACTION_REMOVE_REQUEST", payload.toString()));
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
            MessageData oldData = userData instanceof MessageData ? (MessageData) userData : null;

            // Allow editing only if it's text
            if (oldData == null || !"TEXT".equals(oldData.type())) {
                showAlert("Thông báo", "Chỉ hỗ trợ chỉnh sửa tin nhắn văn bản.", Alert.AlertType.WARNING);
                return;
            }

            String oldContent = oldData.content();

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
                    
                    HBox editedNode = createTextMessageNode(messageIdToEdit, "Bạn", newContent + " (Đã chỉnh sửa)", true, null);
                    editedNode.setUserData(new MessageData(messageIdToEdit, "TEXT", newContent, true, "Bạn"));
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
