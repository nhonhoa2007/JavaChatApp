package org.example.client.controller;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.media.Media;
import javafx.scene.media.MediaPlayer;
import javafx.scene.media.MediaView;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.text.Text;
import javafx.scene.text.TextFlow;
import javafx.stage.FileChooser;
import javafx.util.Duration;
import org.controlsfx.control.Notifications;
import org.example.client.ClientApplication;
import org.example.client.call.CallManager;
import org.example.client.util.IconUtil;
import org.example.client.util.VoicePlayer;
import org.example.client.util.VoiceRecorder;
import org.example.common.network.CallPacketTypes;
import org.example.common.network.Packet;

import javax.sound.sampled.LineUnavailableException;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

public class ChatWorkspaceController {

    @FXML
    private VBox viewRecentConversations;

    @FXML
    private VBox viewChatMessages;

    @FXML
    private ListView<ConversationItem> listRecentConversations;

    @FXML
    private Label lblChatTitle;

    @FXML
    private StackPane chatHeaderAvatar;

    @FXML
    private Button btnCall;

    @FXML
    private Button btnVideoCall;

    @FXML
    private Button btnBlock;

    @FXML
    private Button btnMute;

    @FXML
    private Button btnGroupMute;

    @FXML
    private Button btnLeaveGroup;

    @FXML
    private Button btnDeleteGroup;

    @FXML
    private Button btnSendImage;

    @FXML
    private Button btnSendVideo;

    @FXML
    private Button btnSendFile;

    @FXML
    private Button btnSendMessage;

    @FXML
    private ListView<Object> listMessages;

    @FXML
    private Button btnVoice;

    @FXML
    private TextArea txtMessage;

    private ChatController parentController;
    private String currentUsername;
    private String currentConversationKey;
    private boolean suppressConversationSelection = false;

    private final ObservableList<ConversationItem> recentConversations = FXCollections.observableArrayList();
    
    public boolean recentConversationsEmpty() {
        return recentConversations.isEmpty();
    }
    
    private final Map<Long, Integer> messageIdToIndexMap = new HashMap<>();
    private final Map<Long, String> messageConversationMap = new HashMap<>();
    private static final String TWEMOJI_BASE_URL = "https://cdnjs.cloudflare.com/ajax/libs/twemoji/14.0.2/72x72/";

    private final Map<Long, HBox> reactionSummaryMap = new HashMap<>();
    private final Map<String, Image> reactionIconImageCache = new HashMap<>();
    private final Map<String, String> userAvatarByUsername = new HashMap<>();
    private final Map<String, String> userDisplayNameByUsername = new HashMap<>();

    private final VoiceRecorder voiceRecorder = new VoiceRecorder();
    private String voiceRecordingTarget;
    private String voiceRecordingConversation;

    private static final String LOCK_ICON = "/icon/lock.svg";
    private static final String UNLOCK_ICON = "/icon/unlock.svg";
    private static final String BELL_ICON = "/icon/bell.svg";
    private static final String BELL_OFF_ICON = "/icon/bell-off.svg";

    public void setParentController(ChatController parentController) {
        this.parentController = parentController;
    }

    public void init(String currentUsername) {
        this.currentUsername = currentUsername;
    }

    public void initialize() {
        configureStaticIcons();

        if (listRecentConversations != null) {
            listRecentConversations.setItems(recentConversations);
            listRecentConversations.setCellFactory(lv -> new ListCell<>() {
                @Override
                protected void updateItem(ConversationItem item, boolean empty) {
                    super.updateItem(item, empty);
                    if (empty || item == null) {
                        setText(null);
                        setGraphic(null);
                        return;
                    }

                    Label title = new Label(("GROUP".equals(item.type()) ? "Nhóm: " : "") + item.title());
                    title.getStyleClass().add("conversation-title");

                    Label preview = new Label(item.preview() == null || item.preview().isBlank() ? "Chưa có tin nhắn" : item.preview());
                    preview.getStyleClass().add("conversation-preview");

                    VBox textBox = new VBox(4, title, preview);
                    HBox box = new HBox(10, createAvatarNode(item.avatar(), item.displayName(), item.title(), 38), textBox);
                    box.setAlignment(Pos.CENTER_LEFT);
                    box.getStyleClass().add("conversation-cell");
                    setText(null);
                    setGraphic(box);
                }
            });
            listRecentConversations.getSelectionModel().selectedItemProperty().addListener((observable, oldValue, newValue) -> {
                if (!suppressConversationSelection
                        && newValue != null
                        && viewRecentConversations != null
                        && viewRecentConversations.isVisible()) {
                    handleRecentConversationSelected(newValue);
                }
            });
        }

        setupMessageContextMenu();
    }

    public void startPrivateChat(String username) {
        currentConversationKey = privateConversationKey(username);
        String displayName = userDisplayNameByUsername.getOrDefault(username, username);
        clearChatPaneForConversation("Chat với: " + displayName);
        updateHeaderAvatar(userAvatarByUsername.get(username), displayName, username);
        hideGroupControlButtons();
        updateControlButtons(username);
        switchToChatMessages();
        loadHistory(username);
        addToRecentConversations(displayName, currentConversationKey);
    }

    public void startGroupChat(String groupDisplay, Long groupId) {
        currentConversationKey = groupConversationKey(groupId);
        clearChatPaneForConversation("Nhóm: " + groupDisplay);
        updateHeaderAvatar("", groupDisplay, groupDisplay);
        hidePrivateControlButtons();
        updateGroupControlButtons(groupId);
        switchToChatMessages();
        loadGroupHistory(groupId);
        addToRecentConversations(groupDisplay, currentConversationKey);
    }

    private void handleRecentConversationSelected(ConversationItem item) {
        if ("GROUP".equals(item.type())) {
            Long groupId = Long.parseLong(item.key().substring(6));
            startGroupChat(item.title(), groupId);
        } else {
            String peer = item.key().substring(8);
            startPrivateChat(peer);
        }
    }

    private void configureStaticIcons() {
        setButtonIcon(btnCall, "/icon/phone.svg", "header-call-icon", 1.05);
        setButtonIcon(btnVideoCall, "/icon/video.svg", "header-video-icon", 1.05);
        setButtonIcon(btnSendImage, "/icon/image.svg", "composer-image-icon", 1.1);
        setButtonIcon(btnSendVideo, "/icon/video.svg", "composer-video-icon", 1.1);
        setButtonIcon(btnSendFile, "/icon/file.svg", "composer-file-icon", 1.35);
        setButtonIcon(btnVoice, "/icon/mic.svg", "composer-voice-icon", 1.05);
        setButtonIcon(btnSendMessage, "/icon/send.svg", "send-icon", 1.25);
        setButtonIcon(btnLeaveGroup, "/icon/logout.svg", "action-button-icon", 0.82);
        setButtonIcon(btnDeleteGroup, "/icon/trash.svg", "action-button-icon", 0.82);
    }

    private void setButtonIcon(Button button, String resourcePath, String styleClass, double scale) {
        if (button == null) return;
        button.setText(null);
        button.setGraphic(IconUtil.createIcon(resourcePath, styleClass, scale));
        button.setContentDisplay(ContentDisplay.GRAPHIC_ONLY);
    }

    public void switchToRecentConversations() {
        if (viewRecentConversations != null) {
            viewRecentConversations.setVisible(true);
            viewRecentConversations.setManaged(true);
        }
        if (viewChatMessages != null) {
            viewChatMessages.setVisible(false);
            viewChatMessages.setManaged(false);
        }
        suppressConversationSelection = true;
        try {
            listRecentConversations.getSelectionModel().clearSelection();
        } finally {
            suppressConversationSelection = false;
        }
    }

    public void switchToChatMessages() {
        if (viewRecentConversations != null) {
            viewRecentConversations.setVisible(false);
            viewRecentConversations.setManaged(false);
        }
        if (viewChatMessages != null) {
            viewChatMessages.setVisible(true);
            viewChatMessages.setManaged(true);
        }
    }

    private void clearChatPaneForConversation(String title) {
        lblChatTitle.setText(title);
        updateHeaderAvatar("", title, title);
        listMessages.getItems().clear();
        messageIdToIndexMap.clear();
        messageConversationMap.clear();
        reactionSummaryMap.clear();
    }

    private void loadHistory(String otherUser) {
        listMessages.getItems().clear();
        messageIdToIndexMap.clear();
        messageConversationMap.clear();
        reactionSummaryMap.clear();
        JsonObject payload = new JsonObject();
        payload.addProperty("otherUser", otherUser);
        ClientApplication.getChatClient().sendPacket(new Packet("LOAD_HISTORY_REQUEST", payload.toString()));
    }

    private void loadGroupHistory(long groupId) {
        listMessages.getItems().clear();
        messageIdToIndexMap.clear();
        messageConversationMap.clear();
        reactionSummaryMap.clear();
        JsonObject payload = new JsonObject();
        payload.addProperty("groupId", groupId);
        ClientApplication.getChatClient().sendPacket(new Packet("GROUP_HISTORY_REQUEST", payload.toString()));
    }

    public void requestConversationList() {
        ClientApplication.getChatClient().sendPacket(new Packet("CONVERSATION_LIST_REQUEST", ""));
    }

    public void refreshActiveGroupControls() {
        Long groupId = getActiveGroupTargetId();
        if (groupId != null) {
            updateGroupControlButtons(groupId);
        }
    }

    public void handleConversationList(String payload) {
        JsonObject json = JsonParser.parseString(payload).getAsJsonObject();
        JsonArray conversations = json.getAsJsonArray("conversations");

        String selectedKey = listRecentConversations != null && listRecentConversations.getSelectionModel().getSelectedItem() != null
                ? listRecentConversations.getSelectionModel().getSelectedItem().key()
                : currentConversationKey;

        suppressConversationSelection = true;
        try {
            recentConversations.clear();
            recentConversations.addAll(buildAllConversationItems(conversations));

            if (listRecentConversations != null && selectedKey != null) {
                for (ConversationItem item : recentConversations) {
                    if (item.key().equals(selectedKey)) {
                        listRecentConversations.getSelectionModel().select(item);
                        break;
                    }
                }
            }
        } finally {
            suppressConversationSelection = false;
        }
    }

    private List<ConversationItem> buildAllConversationItems(JsonArray serverConversations) {
        Map<String, ConversationItem> conversationsByKey = new LinkedHashMap<>();

        if (serverConversations != null) {
            for (int i = 0; i < serverConversations.size(); i++) {
                JsonObject item = serverConversations.get(i).getAsJsonObject();
                ConversationItem conversation = new ConversationItem(
                        item.get("key").getAsString(),
                        item.get("title").getAsString(),
                        item.get("type").getAsString(),
                        item.has("preview") ? item.get("preview").getAsString() : "",
                        item.has("timestamp") ? item.get("timestamp").getAsString() : "",
                        item.has("avatar") ? item.get("avatar").getAsString() : "",
                        item.has("displayName") ? item.get("displayName").getAsString() : item.get("title").getAsString()
                );
                if ("PRIVATE".equals(conversation.type()) && conversation.key().startsWith("PRIVATE:")) {
                    registerUserMeta(conversation.key().substring(8), conversation.displayName(), conversation.avatar());
                }
                conversationsByKey.put(conversation.key(), conversation);
            }
        }

        if (parentController != null && parentController.getContactsViewController() != null) {
            for (String friendItem : parentController.getContactsViewController().getAllFriendItems()) {
                String username = extractUsername(friendItem);
                if (username == null || username.isBlank()) {
                    continue;
                }

                String key = privateConversationKey(username);
                String displayName = userDisplayNameByUsername.getOrDefault(username, username);
                conversationsByKey.putIfAbsent(key, new ConversationItem(key, displayName, "PRIVATE", "", "",
                        userAvatarByUsername.getOrDefault(username, ""), displayName));
            }
        }

        if (parentController != null && parentController.getGroupsViewController() != null) {
            for (String groupDisplay : parentController.getGroupsViewController().getGroupDisplayToId().keySet()) {
                Long groupId = parentController.getGroupsViewController().getGroupDisplayToId().get(groupDisplay);
                if (groupId == null) {
                    continue;
                }

                String key = groupConversationKey(groupId);
                conversationsByKey.putIfAbsent(key, new ConversationItem(key, groupDisplay, "GROUP", "", "", "", groupDisplay));
            }
        }

        return List.copyOf(conversationsByKey.values());
    }

    public void refreshConversationsFromLocalState() {
        String selectedKey = listRecentConversations != null && listRecentConversations.getSelectionModel().getSelectedItem() != null
                ? listRecentConversations.getSelectionModel().getSelectedItem().key()
                : currentConversationKey;

        Map<String, ConversationItem> conversationsByKey = new LinkedHashMap<>();
        for (ConversationItem item : recentConversations) {
            conversationsByKey.put(item.key(), item);
        }

        if (parentController != null && parentController.getContactsViewController() != null) {
            for (String friendItem : parentController.getContactsViewController().getAllFriendItems()) {
                String username = extractUsername(friendItem);
                if (username == null || username.isBlank()) {
                    continue;
                }

                String key = privateConversationKey(username);
                String displayName = userDisplayNameByUsername.getOrDefault(username, username);
                conversationsByKey.putIfAbsent(key, new ConversationItem(key, displayName, "PRIVATE", "", "",
                        userAvatarByUsername.getOrDefault(username, ""), displayName));
            }
        }

        if (parentController != null && parentController.getGroupsViewController() != null) {
            for (String groupDisplay : parentController.getGroupsViewController().getGroupDisplayToId().keySet()) {
                Long groupId = parentController.getGroupsViewController().getGroupDisplayToId().get(groupDisplay);
                if (groupId == null) {
                    continue;
                }

                String key = groupConversationKey(groupId);
                conversationsByKey.putIfAbsent(key, new ConversationItem(key, groupDisplay, "GROUP", "", "", "", groupDisplay));
            }
        }

        suppressConversationSelection = true;
        try {
            recentConversations.setAll(conversationsByKey.values());
            if (listRecentConversations != null && selectedKey != null) {
                for (ConversationItem item : recentConversations) {
                    if (item.key().equals(selectedKey)) {
                        listRecentConversations.getSelectionModel().select(item);
                        break;
                    }
                }
            }
        } finally {
            suppressConversationSelection = false;
        }
    }

    private void addToRecentConversations(String displayName, String conversationKey) {
        suppressConversationSelection = true;
        try {
            String existingPreview = "";
            String existingTimestamp = "";
            String existingAvatar = "";
            String existingDisplayName = displayName;
            for (ConversationItem item : recentConversations) {
                if (item.key().equals(conversationKey)) {
                    existingPreview = item.preview();
                    existingTimestamp = item.timestamp();
                    existingAvatar = item.avatar();
                    existingDisplayName = item.displayName();
                    break;
                }
            }
            if (conversationKey.startsWith("PRIVATE:")) {
                String username = conversationKey.substring(8);
                existingAvatar = userAvatarByUsername.getOrDefault(username, existingAvatar);
                existingDisplayName = userDisplayNameByUsername.getOrDefault(username, displayName);
            } else {
                existingDisplayName = displayName;
            }
            recentConversations.removeIf(item -> item.key().equals(conversationKey));
            ConversationItem updatedItem = new ConversationItem(
                    conversationKey,
                    existingDisplayName,
                    conversationKey.startsWith("GROUP:") ? "GROUP" : "PRIVATE",
                    existingPreview,
                    existingTimestamp,
                    existingAvatar,
                    existingDisplayName
            );
            recentConversations.add(0, updatedItem);

            if (listRecentConversations != null && conversationKey.equals(currentConversationKey)) {
                listRecentConversations.getSelectionModel().select(updatedItem);
            }
        } finally {
            suppressConversationSelection = false;
        }
    }

    public void handleChatHistory(String payload) {
        JsonObject json = JsonParser.parseString(payload).getAsJsonObject();
        String otherUser = json.get("otherUser").getAsString();
        
        if (!privateConversationKey(otherUser).equals(currentConversationKey)) {
            return;
        }

        JsonArray messages = json.getAsJsonArray("messages");
        for (int i = 0; i < messages.size(); i++) {
            JsonObject msg = messages.get(i).getAsJsonObject();
            long id = msg.get("id").getAsLong();
            String sender = msg.has("sender") ? msg.get("sender").getAsString() : "";
            String content = msg.get("content").getAsString();
            String type = msg.get("type").getAsString();
            String filename = msg.has("filename") ? msg.get("filename").getAsString() : null;
            String iconResource = "CALL_LOG".equals(type) ? getJsonString(msg, "icon", null) : filename;
            String senderUsername = getJsonString(msg, "senderUsername", "Bạn".equals(sender) ? currentUsername : sender);
            String senderDisplayName = getJsonString(msg, "senderDisplayName", sender);
            String senderAvatar = getJsonString(msg, "senderAvatar", userAvatarByUsername.getOrDefault(senderUsername, ""));
            registerUserMeta(senderUsername, senderDisplayName, senderAvatar);

            boolean isMe = "Bạn".equals(sender);
            String senderLabel = isMe ? sender : senderDisplayName;
            listMessages.getItems().add(createMessageNodeByType(id, senderLabel, content, isMe, type,
                    msg.has("reactions") ? msg.getAsJsonArray("reactions") : null, iconResource,
                    senderUsername, senderDisplayName, senderAvatar));

            if (!"CALL_LOG".equals(type)) {
                messageIdToIndexMap.put(id, listMessages.getItems().size() - 1);
                messageConversationMap.put(id, privateConversationKey(otherUser));
            }
        }
    }

    public void handleChatAck(String payload) {
        JsonObject json = JsonParser.parseString(payload).getAsJsonObject();
        long messageId = json.get("id").getAsLong();
        String content = json.get("content").getAsString();
        String type = json.has("type") ? json.get("type").getAsString() : "TEXT";
        String filename = json.has("filename") ? json.get("filename").getAsString() : null;
        String receiver = json.has("receiver") ? json.get("receiver").getAsString() : null;
        String senderUsername = getJsonString(json, "senderUsername", currentUsername);
        String senderDisplayName = getJsonString(json, "senderDisplayName", "Bạn");
        String senderAvatar = getJsonString(json, "senderAvatar", userAvatarByUsername.getOrDefault(senderUsername, ""));
        registerUserMeta(senderUsername, senderDisplayName, senderAvatar);

        if (receiver == null) {
            return;
        }

        String conversationKey = privateConversationKey(receiver);
        if (!conversationKey.equals(currentConversationKey)) {
            return;
        }

        listMessages.getItems().add(createMessageNodeByType(messageId, "Bạn", content, true, type,
                json.has("reactions") ? json.getAsJsonArray("reactions") : null, filename,
                senderUsername, senderDisplayName, senderAvatar));

        messageIdToIndexMap.put(messageId, listMessages.getItems().size() - 1);
        messageConversationMap.put(messageId, conversationKey);

        addToRecentConversations(receiver, conversationKey);
    }

    public void handleIncomingMessage(String payload) {
        JsonObject json = JsonParser.parseString(payload).getAsJsonObject();
        String sender = json.get("sender").getAsString();
        String content = json.get("content").getAsString();
        String type = json.has("type") ? json.get("type").getAsString() : "TEXT";
        String filename = json.has("filename") ? json.get("filename").getAsString() : null;
        String senderUsername = getJsonString(json, "senderUsername", sender);
        String senderDisplayName = getJsonString(json, "senderDisplayName", sender);
        String senderAvatar = getJsonString(json, "senderAvatar", userAvatarByUsername.getOrDefault(senderUsername, ""));
        registerUserMeta(senderUsername, senderDisplayName, senderAvatar);

        String conversationKey = privateConversationKey(sender);
        if (conversationKey.equals(currentConversationKey)) {
            if (json.has("id")) {
                long messageId = json.get("id").getAsLong();
                listMessages.getItems().add(createMessageNodeByType(messageId, senderDisplayName, content, false, type,
                        json.has("reactions") ? json.getAsJsonArray("reactions") : null, filename,
                        senderUsername, senderDisplayName, senderAvatar));
                messageIdToIndexMap.put(messageId, listMessages.getItems().size() - 1);
                messageConversationMap.put(messageId, conversationKey);
            } else {
                listMessages.getItems().add(createMessageNodeByType(null, senderDisplayName, content, false, type,
                        null, filename, senderUsername, senderDisplayName, senderAvatar));
            }
        }

        addToRecentConversations(senderDisplayName, conversationKey);

        boolean isMuted = json.has("isMuted") && json.get("isMuted").getAsBoolean();
        if (!isMuted) {
            String notiText;
            if ("IMAGE".equals(type)) {
                notiText = "[Hình ảnh]";
            } else if ("VOICE".equals(type)) {
                notiText = "[Voice]";
            } else if ("VIDEO".equals(type)) {
                notiText = filename == null || filename.isBlank() ? "[Video]" : "[Video] " + filename;
            } else if ("FILE".equals(type)) {
                notiText = filename == null || filename.isBlank() ? "[Tệp]" : "[Tệp] " + filename;
            } else {
                notiText = content;
            }
            showPushNotification(sender, notiText);
        }
    }

    public void handleGroupHistory(String payload) {
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
            String filename = msg.has("filename") ? msg.get("filename").getAsString() : null;
            String iconResource = "CALL_LOG".equals(type) ? getJsonString(msg, "icon", null) : filename;
            String senderUsername = getJsonString(msg, "senderUsername", isMe ? currentUsername : sender);
            String senderDisplayName = getJsonString(msg, "senderDisplayName", sender);
            String senderAvatar = getJsonString(msg, "senderAvatar", userAvatarByUsername.getOrDefault(senderUsername, ""));
            registerUserMeta(senderUsername, senderDisplayName, senderAvatar);

            String senderLabel = isMe ? sender : senderDisplayName;
            HBox node = createMessageNodeByType(id, senderLabel, content, isMe, type,
                    msg.has("reactions") ? msg.getAsJsonArray("reactions") : null, iconResource,
                    senderUsername, senderDisplayName, senderAvatar);
            listMessages.getItems().add(node);
            messageIdToIndexMap.put(id, listMessages.getItems().size() - 1);
            messageConversationMap.put(id, expectedConversation);
        }
    }

    public void handleGroupMessageAck(String payload) {
        JsonObject json = JsonParser.parseString(payload).getAsJsonObject();
        long messageId = json.get("id").getAsLong();
        long groupId = json.get("groupId").getAsLong();
        String conversationKey = groupConversationKey(groupId);

        if (!conversationKey.equals(currentConversationKey)) {
            return;
        }

        String content = json.get("content").getAsString();
        String type = json.has("type") ? json.get("type").getAsString() : "TEXT";
        String filename = json.has("filename") ? json.get("filename").getAsString() : null;
        String senderUsername = getJsonString(json, "senderUsername", currentUsername);
        String senderDisplayName = getJsonString(json, "senderDisplayName", "Bạn");
        String senderAvatar = getJsonString(json, "senderAvatar", userAvatarByUsername.getOrDefault(senderUsername, ""));
        registerUserMeta(senderUsername, senderDisplayName, senderAvatar);
        HBox node = createMessageNodeByType(messageId, "Bạn", content, true, type,
                json.has("reactions") ? json.getAsJsonArray("reactions") : null, filename,
                senderUsername, senderDisplayName, senderAvatar);
        listMessages.getItems().add(node);
        messageIdToIndexMap.put(messageId, listMessages.getItems().size() - 1);
        messageConversationMap.put(messageId, conversationKey);

        if (parentController != null && parentController.getGroupsViewController() != null) {
            String groupDisplay = null;
            for (String item : parentController.getGroupsViewController().getGroupDisplayToId().keySet()) {
                if (parentController.getGroupsViewController().getGroupDisplayToId().get(item) == groupId) {
                    groupDisplay = item;
                    break;
                }
            }
            if (groupDisplay != null) {
                addToRecentConversations(groupDisplay, conversationKey);
            }
        }
    }

    public void handleIncomingGroupMessage(String payload) {
        JsonObject json = JsonParser.parseString(payload).getAsJsonObject();
        long groupId = json.get("groupId").getAsLong();
        String conversationKey = groupConversationKey(groupId);

        long messageId = json.get("id").getAsLong();
        String sender = json.get("sender").getAsString();
        String content = json.get("content").getAsString();
        String type = json.has("type") ? json.get("type").getAsString() : "TEXT";
        String filename = json.has("filename") ? json.get("filename").getAsString() : null;
        String senderUsername = getJsonString(json, "senderUsername", sender);
        String senderDisplayName = getJsonString(json, "senderDisplayName", sender);
        String senderAvatar = getJsonString(json, "senderAvatar", userAvatarByUsername.getOrDefault(senderUsername, ""));
        registerUserMeta(senderUsername, senderDisplayName, senderAvatar);

        if (conversationKey.equals(currentConversationKey)) {
            HBox node = createMessageNodeByType(messageId, senderDisplayName, content, false, type,
                    json.has("reactions") ? json.getAsJsonArray("reactions") : null, filename,
                    senderUsername, senderDisplayName, senderAvatar);
            listMessages.getItems().add(node);
            messageIdToIndexMap.put(messageId, listMessages.getItems().size() - 1);
            messageConversationMap.put(messageId, conversationKey);
        }

        if (parentController != null && parentController.getGroupsViewController() != null) {
            String groupDisplay = null;
            for (String item : parentController.getGroupsViewController().getGroupDisplayToId().keySet()) {
                if (parentController.getGroupsViewController().getGroupDisplayToId().get(item) == groupId) {
                    groupDisplay = item;
                    break;
                }
            }
            if (groupDisplay != null) {
                addToRecentConversations(groupDisplay, conversationKey);
            }
        }

        boolean isMuted = json.has("isMuted") && json.get("isMuted").getAsBoolean();
        if (!isMuted) {
            showPushNotification("Nhóm", sender + ": " + ("TEXT".equals(type) ? content : "[" + type + "]"));
        }
    }

    public void handleGroupRemoved(String payload) {
        JsonObject json = JsonParser.parseString(payload).getAsJsonObject();
        long groupId = json.get("groupId").getAsLong();
        String reason = getJsonString(json, "reason", "Nhóm không còn khả dụng.");
        String removedKey = groupConversationKey(groupId);

        recentConversations.removeIf(item -> item.key().equals(removedKey));
        if (removedKey.equals(currentConversationKey)) {
            currentConversationKey = null;
            listMessages.getItems().clear();
            messageIdToIndexMap.clear();
            messageConversationMap.clear();
            reactionSummaryMap.clear();
            hidePrivateControlButtons();
            hideGroupControlButtons();
            switchToRecentConversations();
            showAlert("Thông báo", reason, Alert.AlertType.INFORMATION);
        }
    }

    public void handleReactionUpdated(String payload) {
        JsonObject json = JsonParser.parseString(payload).getAsJsonObject();
        long messageId = json.get("messageId").getAsLong();
        String conversationKey = messageConversationMap.get(messageId);
        if (conversationKey != null && !conversationKey.equals(currentConversationKey)) {
            return;
        }
        HBox reactionSummary = reactionSummaryMap.get(messageId);
        if (reactionSummary != null) {
            renderReactionSummary(reactionSummary, json.getAsJsonArray("reactions"));
        }
    }

    public void handleMessageRecalled(String payload) {
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

                    HBox recalledNode = oldData == null
                            ? createTextMessageNode(messageId, sender, "🚫 Tin nhắn đã bị thu hồi", isMe, null)
                            : createTextMessageNode(messageId, sender, "🚫 Tin nhắn đã bị thu hồi", isMe, null,
                                    oldData.senderUsername(), oldData.senderDisplay(), oldData.senderAvatar());
                    listMessages.getItems().set(index, recalledNode);
                }
            }
        }
    }

    public void handleMessageEdited(String payload) {
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

                    HBox editedNode = createTextMessageNode(messageId, sender, newContent + " (Đã chỉnh sửa)", isMe, null,
                            oldData.senderUsername(), oldData.senderDisplay(), oldData.senderAvatar());
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
        
        String selectedUser = getActivePrivateTarget();
        if (selectedUser != null) {
            payload.addProperty("receiver", selectedUser);
            Packet chatPacket = new Packet("PRIVATE_MESSAGE", payload.toString());
            ClientApplication.getChatClient().sendPacket(chatPacket);
        } else {
            Long groupId = getActiveGroupTargetId();
            if (groupId == null) {
                showAlert("Thông báo", "Không xác định được nhóm chat.", Alert.AlertType.WARNING);
                return;
            }
            payload.addProperty("groupId", groupId);
            ClientApplication.getChatClient().sendPacket(new Packet("GROUP_MESSAGE", payload.toString()));
        }
        
        txtMessage.clear();
    }

    @FXML
    public void handleSendImage(ActionEvent event) {
        String selectedUser = getActivePrivateTarget();
        Long selectedGroupId = getActiveGroupTargetId();

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
                if (selectedFile.length() > 10 * 1024 * 1024) {
                    showAlert("Lỗi", "Vui lòng chọn ảnh có kích thước nhỏ hơn 10MB.", Alert.AlertType.ERROR);
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
    public void handleSendFile(ActionEvent event) {
        String selectedUser = getActivePrivateTarget();
        Long selectedGroupId = getActiveGroupTargetId();

        if (selectedUser == null && selectedGroupId == null) {
            showAlert("Thông báo", "Vui lòng chọn bạn bè hoặc nhóm để gửi tệp.", Alert.AlertType.WARNING);
            return;
        }

        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Chọn tệp để gửi");
        File selectedFile = fileChooser.showOpenDialog(txtMessage.getScene().getWindow());
        if (selectedFile != null) {
            try {
                if (selectedFile.length() > 20 * 1024 * 1024) {
                    showAlert("Lỗi", "Vui lòng chọn tệp có kích thước nhỏ hơn 20MB.", Alert.AlertType.ERROR);
                    return;
                }

                byte[] fileContent = Files.readAllBytes(selectedFile.toPath());
                String encodedString = Base64.getEncoder().encodeToString(fileContent);

                JsonObject payload = new JsonObject();
                payload.addProperty("content", encodedString);
                payload.addProperty("type", "FILE");
                payload.addProperty("filename", selectedFile.getName());
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
                showAlert("Lỗi", "Không thể đọc file.", Alert.AlertType.ERROR);
                e.printStackTrace();
            }
        }
    }

    @FXML
    public void handleSendVideo(ActionEvent event) {
        String selectedUser = getActivePrivateTarget();
        Long selectedGroupId = getActiveGroupTargetId();

        if (selectedUser == null && selectedGroupId == null) {
            showAlert("Thông báo", "Vui lòng chọn bạn bè hoặc nhóm để gửi video.", Alert.AlertType.WARNING);
            return;
        }

        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Chọn video để gửi");
        fileChooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("Video Files", "*.mp4", "*.m4v", "*.mov", "*.avi")
        );

        File selectedFile = fileChooser.showOpenDialog(txtMessage.getScene().getWindow());
        if (selectedFile != null) {
            try {
                if (selectedFile.length() > 20 * 1024 * 1024) {
                    showAlert("Lỗi", "Vui lòng chọn video có kích thước nhỏ hơn 20MB.", Alert.AlertType.ERROR);
                    return;
                }

                byte[] fileContent = Files.readAllBytes(selectedFile.toPath());
                String encodedString = Base64.getEncoder().encodeToString(fileContent);

                JsonObject payload = new JsonObject();
                payload.addProperty("content", encodedString);
                payload.addProperty("type", "VIDEO");
                payload.addProperty("filename", selectedFile.getName());

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
                showAlert("Lỗi", "Không thể đọc file video.", Alert.AlertType.ERROR);
                e.printStackTrace();
            }
        }
    }

    @FXML
    public void handleSendVoice(ActionEvent event) {
        String selectedUser = getActivePrivateTarget();
        Long selectedGroupId = getActiveGroupTargetId();

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
            btnVoice.setText(null);
            btnVoice.setGraphic(IconUtil.createIcon("/icon/stop.svg", "composer-stop-icon", 1.05));
            btnVoice.setContentDisplay(ContentDisplay.GRAPHIC_ONLY);
            btnVoice.setStyle("-fx-min-width: 44px; -fx-min-height: 44px; -fx-background-color: #ef4444; -fx-background-radius: 9px; -fx-cursor: hand;");
        } else {
            btnVoice.setText(null);
            btnVoice.setGraphic(IconUtil.createIcon("/icon/mic.svg", "composer-voice-icon", 1.05));
            btnVoice.setContentDisplay(ContentDisplay.GRAPHIC_ONLY);
            btnVoice.setStyle("-fx-min-width: 44px; -fx-min-height: 44px; -fx-background-color: #edf2f7; -fx-background-radius: 9px; -fx-cursor: hand;");
        }
    }

    @FXML
    public void handleCallUser(ActionEvent event) {
        String peer = getActivePrivateTarget();
        if (peer == null) {
            showAlert("Thông báo", "Vui lòng chọn bạn bè để gọi.", Alert.AlertType.WARNING);
            return;
        }
        if (CallManager.getInstance().isInCall()) {
            showAlert("Thông báo", "Bạn đang trong cuộc gọi khác.", Alert.AlertType.WARNING);
            return;
        }
        CallManager.getInstance().startCall(peer, CallPacketTypes.TYPE_VOICE);
    }

    @FXML
    public void handleVideoCallUser(ActionEvent event) {
        String peer = getActivePrivateTarget();
        if (peer == null) {
            showAlert("Thông báo", "Vui lòng chọn bạn bè để gọi.", Alert.AlertType.WARNING);
            return;
        }
        if (CallManager.getInstance().isInCall()) {
            showAlert("Thông báo", "Bạn đang trong cuộc gọi khác.", Alert.AlertType.WARNING);
            return;
        }
        CallManager.getInstance().startCall(peer, CallPacketTypes.TYPE_VIDEO);
    }


    @FXML
    public void handleBlockUser(ActionEvent event) {
        String selectedUser = getActivePrivateTarget();
        if (selectedUser != null && parentController != null) {
            JsonObject payload = new JsonObject();
            payload.addProperty("targetUsername", selectedUser);
            ClientApplication.getChatClient().sendPacket(new Packet("BLOCK_USER_REQUEST", payload.toString()));
            parentController.getIsBlockedByMeMap().put(selectedUser, !parentController.getIsBlockedByMeMap().getOrDefault(selectedUser, false));
            updateControlButtons(selectedUser);
        } else {
            showAlert("Thông báo", "Vui lòng chọn một người bạn để chặn.", Alert.AlertType.WARNING);
        }
    }

    @FXML
    public void handleMuteUser(ActionEvent event) {
        String selectedUser = getActivePrivateTarget();
        if (selectedUser != null && parentController != null) {
            JsonObject payload = new JsonObject();
            payload.addProperty("targetUsername", selectedUser);
            ClientApplication.getChatClient().sendPacket(new Packet("MUTE_USER_REQUEST", payload.toString()));
            parentController.getIsMutedByMeMap().put(selectedUser, !parentController.getIsMutedByMeMap().getOrDefault(selectedUser, false));
            updateControlButtons(selectedUser);
        } else {
            showAlert("Thông báo", "Vui lòng chọn một người bạn để tắt thông báo.", Alert.AlertType.WARNING);
        }
    }

    @FXML
    public void handleMuteGroup(ActionEvent event) {
        Long groupId = getActiveGroupTargetId();
        if (groupId == null) {
            showAlert("Thông báo", "Vui lòng chọn nhóm để tắt thông báo.", Alert.AlertType.WARNING);
            return;
        }

        JsonObject payload = new JsonObject();
        payload.addProperty("groupId", groupId);
        ClientApplication.getChatClient().sendPacket(new Packet("GROUP_MUTE_REQUEST", payload.toString()));
    }

    @FXML
    public void handleLeaveGroup(ActionEvent event) {
        Long groupId = getActiveGroupTargetId();
        if (groupId == null) {
            showAlert("Thông báo", "Vui lòng chọn nhóm để rời.", Alert.AlertType.WARNING);
            return;
        }
        if (!confirmGroupAction("Rời nhóm", "Bạn có chắc muốn rời nhóm này?")) {
            return;
        }

        JsonObject payload = new JsonObject();
        payload.addProperty("groupId", groupId);
        ClientApplication.getChatClient().sendPacket(new Packet("GROUP_LEAVE_REQUEST", payload.toString()));
    }

    @FXML
    public void handleDeleteGroup(ActionEvent event) {
        Long groupId = getActiveGroupTargetId();
        if (groupId == null) {
            showAlert("Thông báo", "Vui lòng chọn nhóm để xóa.", Alert.AlertType.WARNING);
            return;
        }
        if (!confirmGroupAction("Xóa nhóm", "Bạn có chắc muốn xóa nhóm này? Toàn bộ tin nhắn nhóm sẽ bị xóa.")) {
            return;
        }

        JsonObject payload = new JsonObject();
        payload.addProperty("groupId", groupId);
        ClientApplication.getChatClient().sendPacket(new Packet("GROUP_DELETE_REQUEST", payload.toString()));
    }

    private boolean confirmGroupAction(String title, String content) {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(content);
        return alert.showAndWait().filter(button -> button == ButtonType.OK).isPresent();
    }

    private void updateControlButtons(String targetUser) {
        if (btnBlock == null || btnMute == null || parentController == null) return;
        if (btnCall != null) {
            btnCall.setVisible(true);
            btnCall.setManaged(true);
        }
        if (btnVideoCall != null) {
            btnVideoCall.setVisible(true);
            btnVideoCall.setManaged(true);
        }
        btnBlock.setVisible(true);
        btnBlock.setManaged(true);
        btnMute.setVisible(true);
        btnMute.setManaged(true);

        boolean isBlocked = parentController.getIsBlockedByMeMap().getOrDefault(targetUser, false);
        configureActionButton(
                btnBlock,
                isBlocked ? UNLOCK_ICON : LOCK_ICON,
                isBlocked ? "Mở chặn" : "Chặn",
                isBlocked ? "success-button" : "danger-button");

        boolean isMuted = parentController.getIsMutedByMeMap().getOrDefault(targetUser, false);
        configureActionButton(
                btnMute,
                isMuted ? BELL_ICON : BELL_OFF_ICON,
                isMuted ? "Mở TB" : "Tắt TB",
                isMuted ? "success-button" : "warning-button");
    }

    private void hidePrivateControlButtons() {
        setButtonVisibility(btnCall, false);
        setButtonVisibility(btnVideoCall, false);
        setButtonVisibility(btnBlock, false);
        setButtonVisibility(btnMute, false);
    }

    private void hideGroupControlButtons() {
        setButtonVisibility(btnGroupMute, false);
        setButtonVisibility(btnLeaveGroup, false);
        setButtonVisibility(btnDeleteGroup, false);
    }

    private void updateGroupControlButtons(Long groupId) {
        if (groupId == null || parentController == null || parentController.getGroupsViewController() == null) {
            hideGroupControlButtons();
            return;
        }

        boolean isMuted = parentController.getGroupsViewController().isGroupMutedByMe(groupId);
        configureActionButton(
                btnGroupMute,
                isMuted ? BELL_ICON : BELL_OFF_ICON,
                isMuted ? "Mở TB nhóm" : "Tắt TB nhóm",
                isMuted ? "success-button" : "warning-button");
        setButtonVisibility(btnGroupMute, true);

        boolean isCreator = parentController.getGroupsViewController().isGroupCreatedByMe(groupId);
        setButtonVisibility(btnLeaveGroup, !isCreator);
        if (!isCreator) {
            configureActionButton(btnLeaveGroup, "/icon/logout.svg", "Rời nhóm", "warning-button");
        }
        setButtonVisibility(btnDeleteGroup, isCreator);
        if (isCreator) {
            configureActionButton(btnDeleteGroup, "/icon/trash.svg", "Xóa nhóm", "danger-button");
        }
    }

    private void setButtonVisibility(Button button, boolean visible) {
        if (button == null) return;
        button.setVisible(visible);
        button.setManaged(visible);
    }

    private void configureActionButton(Button button, String iconPath, String tooltipText, String stateStyleClass) {
        if (button == null) return;
        button.setText(null);
        button.setGraphic(IconUtil.createIcon(iconPath, "action-button-icon", 0.82));
        button.setContentDisplay(ContentDisplay.GRAPHIC_ONLY);
        button.setTooltip(new Tooltip(tooltipText));
        button.setAccessibleText(tooltipText);
        button.getStyleClass().setAll("button", "control-icon-button", stateStyleClass);
    }

    private String getActivePrivateTarget() {
        if (currentConversationKey != null && currentConversationKey.startsWith("PRIVATE:")) {
            return currentConversationKey.substring(8);
        }
        return null;
    }

    private Long getActiveGroupTargetId() {
        if (currentConversationKey != null && currentConversationKey.startsWith("GROUP:")) {
            return Long.parseLong(currentConversationKey.substring(6));
        }
        return null;
    }

    private HBox createMessageNodeByType(Long messageId, String sender, String content, boolean isMe, String type, JsonArray reactions, String filename) {
        return createMessageNodeByType(messageId, sender, content, isMe, type, reactions, filename,
                isMe ? currentUsername : sender, sender, "");
    }

    private HBox createMessageNodeByType(Long messageId, String sender, String content, boolean isMe, String type,
                                         JsonArray reactions, String filename, String senderUsername,
                                         String senderDisplayName, String senderAvatar) {
        if ("IMAGE".equals(type)) {
            return createImageMessageNode(messageId, sender, content, isMe, reactions, senderUsername, senderDisplayName, senderAvatar);
        }
        if ("VOICE".equals(type)) {
            return createVoiceMessageNode(messageId, sender, content, isMe, reactions, senderUsername, senderDisplayName, senderAvatar);
        }
        if ("VIDEO".equals(type)) {
            return createVideoMessageNode(messageId, sender, content, filename, isMe, reactions, senderUsername, senderDisplayName, senderAvatar);
        }
        if ("FILE".equals(type)) {
            return createFileMessageNode(messageId, sender, content, filename, isMe, reactions, senderUsername, senderDisplayName, senderAvatar);
        }
        if ("CALL_LOG".equals(type)) {
            return createCallLogNode(content, filename);
        }
        return createTextMessageNode(messageId, sender, content, isMe, reactions, senderUsername, senderDisplayName, senderAvatar);
    }

    private HBox createCallLogNode(String content, String iconResource) {
        HBox container = new HBox();
        container.setAlignment(Pos.CENTER);

        HBox callLog = new HBox(6);
        callLog.setAlignment(Pos.CENTER);
        callLog.setStyle("-fx-padding: 6 14; "
                + "-fx-background-color: #f1f5f9; -fx-background-radius: 12;");

        if (iconResource != null && !iconResource.isBlank()) {
            callLog.getChildren().add(IconUtil.createIcon(iconResource, "call-log-icon", 0.72));
        }
        Label label = new Label(content);
        label.setStyle("-fx-font-size: 12px; -fx-text-fill: #64748b;");

        callLog.getChildren().add(label);
        container.getChildren().add(callLog);
        container.setUserData(new MessageData(null, "CALL_LOG", content, false, "", "", "", null));
        return container;
    }

    private HBox createTextMessageNode(Long messageId, String sender, String content, boolean isMe, JsonArray reactions) {
        return createTextMessageNode(messageId, sender, content, isMe, reactions, isMe ? currentUsername : sender, sender, "");
    }

    private HBox createTextMessageNode(Long messageId, String sender, String content, boolean isMe, JsonArray reactions,
                                       String senderUsername, String senderDisplayName, String senderAvatar) {
        HBox container = new HBox();
        container.setSpacing(10);
        
        VBox messageBox = new VBox();
        messageBox.setSpacing(4);
        messageBox.setMaxWidth(420);

        Label senderLabel = new Label(sender);
        senderLabel.setStyle("-fx-font-size: 11px; -fx-text-fill: #64748b;");

        Text text = new Text(content);
        text.setStyle("-fx-font-size: 14px;");

        TextFlow textFlow = new TextFlow(text);
        textFlow.setPadding(new javafx.geometry.Insets(9, 13, 9, 13));
        
        if (isMe) {
            text.setFill(Color.WHITE);
            textFlow.setStyle("-fx-background-color: #2563eb; -fx-background-radius: 16px 16px 4px 16px;");
            messageBox.setAlignment(Pos.CENTER_RIGHT);
            container.setAlignment(Pos.CENTER_RIGHT);
            messageBox.getChildren().add(textFlow);
        } else {
            text.setFill(Color.web("#172033"));
            textFlow.setStyle("-fx-background-color: #e8edf3; -fx-background-radius: 16px 16px 16px 4px;");
            messageBox.setAlignment(Pos.CENTER_LEFT);
            container.setAlignment(Pos.CENTER_LEFT);
            messageBox.getChildren().addAll(senderLabel, textFlow);
        }

        HBox reactionSummary = createReactionSummaryNode(messageId, reactions);
        messageBox.getChildren().add(reactionSummary);

        attachMessageNode(container, messageBox, messageId, "TEXT", content, isMe, sender,
                senderUsername, senderDisplayName, senderAvatar, null);
        return container;
    }

    private HBox createImageMessageNode(Long messageId, String sender, String base64Content, boolean isMe, JsonArray reactions) {
        return createImageMessageNode(messageId, sender, base64Content, isMe, reactions, isMe ? currentUsername : sender, sender, "");
    }

    private HBox createImageMessageNode(Long messageId, String sender, String base64Content, boolean isMe, JsonArray reactions,
                                        String senderUsername, String senderDisplayName, String senderAvatar) {
        HBox container = new HBox();
        container.setSpacing(10);
        
        VBox messageBox = new VBox();
        messageBox.setSpacing(4);
        
        Label senderLabel = new Label(sender);
        senderLabel.setStyle("-fx-font-size: 11px; -fx-text-fill: #64748b;");

        try {
            byte[] imageBytes = Base64.getDecoder().decode(base64Content);
            Image image = new Image(new ByteArrayInputStream(imageBytes));
            ImageView imageView = new ImageView(image);
            imageView.setFitWidth(220);
            imageView.setPreserveRatio(true);
            imageView.setCursor(javafx.scene.Cursor.HAND);
            
            imageView.setOnMouseClicked(e -> showImagePreviewDialog(image, imageBytes));
            
            VBox imageWrapper = new VBox(imageView);
            imageWrapper.setPadding(new javafx.geometry.Insets(6));
            
            if (isMe) {
                imageWrapper.setStyle("-fx-background-color: #2563eb; -fx-background-radius: 14px 14px 4px 14px;");
                messageBox.setAlignment(Pos.CENTER_RIGHT);
                container.setAlignment(Pos.CENTER_RIGHT);
                messageBox.getChildren().add(imageWrapper);
            } else {
                imageWrapper.setStyle("-fx-background-color: #e8edf3; -fx-background-radius: 14px 14px 14px 4px;");
                messageBox.setAlignment(Pos.CENTER_LEFT);
                container.setAlignment(Pos.CENTER_LEFT);
                messageBox.getChildren().addAll(senderLabel, imageWrapper);
            }
        } catch (Exception e) {
            Label errorLabel = new Label("[Lỗi hiển thị hình ảnh]");
            messageBox.getChildren().add(errorLabel);
        }

        HBox reactionSummary = createReactionSummaryNode(messageId, reactions);
        messageBox.getChildren().add(reactionSummary);

        attachMessageNode(container, messageBox, messageId, "IMAGE", base64Content, isMe, sender,
                senderUsername, senderDisplayName, senderAvatar, null);
        return container;
    }

    private void showImagePreviewDialog(Image image, byte[] imageBytes) {
        javafx.stage.Stage previewStage = new javafx.stage.Stage();
        previewStage.setTitle("Xem ảnh");
        previewStage.initModality(javafx.stage.Modality.APPLICATION_MODAL);

        ImageView fullImageView = new ImageView(image);
        fullImageView.setPreserveRatio(true);
        fullImageView.setFitWidth(Math.min(image.getWidth(), 800));
        fullImageView.setFitHeight(Math.min(image.getHeight(), 600));

        Button saveButton = new Button("💾 Tải xuống");
        saveButton.setStyle("-fx-background-color: #2563eb; -fx-text-fill: white; -fx-font-weight: bold; -fx-cursor: hand; -fx-background-radius: 8px; -fx-padding: 8 16;");
        saveButton.setOnAction(e -> {
            FileChooser saver = new FileChooser();
            saver.setTitle("Lưu hình ảnh");
            saver.setInitialFileName("image.png");
            saver.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("PNG", "*.png"),
                new FileChooser.ExtensionFilter("JPG", "*.jpg"),
                new FileChooser.ExtensionFilter("All Files", "*.*")
            );
            File dest = saver.showSaveDialog(previewStage);
            if (dest != null) {
                try {
                    Files.write(dest.toPath(), imageBytes);
                    showAlert("Hoàn tất", "Ảnh đã được lưu: " + dest.getAbsolutePath(), Alert.AlertType.INFORMATION);
                } catch (IOException ex) {
                    showAlert("Lỗi", "Không thể lưu ảnh.", Alert.AlertType.ERROR);
                }
            }
        });

        HBox buttonBar = new HBox(saveButton);
        buttonBar.setAlignment(Pos.CENTER);
        buttonBar.setPadding(new javafx.geometry.Insets(10));

        javafx.scene.control.ScrollPane scrollPane = new javafx.scene.control.ScrollPane(fullImageView);
        scrollPane.setFitToWidth(true);
        scrollPane.setFitToHeight(true);
        scrollPane.setStyle("-fx-background-color: #1e1e1e;");

        VBox root = new VBox(scrollPane, buttonBar);
        root.setStyle("-fx-background-color: #1e1e1e;");
        VBox.setVgrow(scrollPane, Priority.ALWAYS);

        javafx.scene.Scene scene = new javafx.scene.Scene(root, 820, 660);
        previewStage.setScene(scene);
        previewStage.show();
    }

    private HBox createVoiceMessageNode(Long messageId, String sender, String base64Content, boolean isMe, JsonArray reactions) {
        return createVoiceMessageNode(messageId, sender, base64Content, isMe, reactions, isMe ? currentUsername : sender, sender, "");
    }

    private HBox createVoiceMessageNode(Long messageId, String sender, String base64Content, boolean isMe, JsonArray reactions,
                                        String senderUsername, String senderDisplayName, String senderAvatar) {
        HBox container = new HBox();
        container.setSpacing(10);

        VBox messageBox = new VBox();
        messageBox.setSpacing(4);
        messageBox.setMaxWidth(420);

        Label senderLabel = new Label(sender);
        senderLabel.setStyle("-fx-font-size: 11px; -fx-text-fill: #64748b;");

        Button playButton = new Button("▶ Phát voice");
        playButton.setStyle("-fx-background-color: white; -fx-text-fill: #2563eb; -fx-font-weight: bold; -fx-cursor: hand; -fx-background-radius: 10px;");
        playButton.setOnAction(e -> playVoiceMessage(base64Content));

        Label voiceLabel = new Label("Tin nhắn thoại");
        voiceLabel.setStyle("-fx-font-size: 13px;");

        HBox voiceBox = new HBox(8, playButton, voiceLabel);
        voiceBox.setPadding(new javafx.geometry.Insets(8, 12, 8, 12));

        if (isMe) {
            voiceLabel.setTextFill(Color.WHITE);
            voiceBox.setStyle("-fx-background-color: #2563eb; -fx-background-radius: 16px 16px 4px 16px;");
            messageBox.setAlignment(Pos.CENTER_RIGHT);
            container.setAlignment(Pos.CENTER_RIGHT);
            messageBox.getChildren().add(voiceBox);
        } else {
            voiceLabel.setTextFill(Color.web("#172033"));
            voiceBox.setStyle("-fx-background-color: #e8edf3; -fx-background-radius: 16px 16px 16px 4px;");
            messageBox.setAlignment(Pos.CENTER_LEFT);
            container.setAlignment(Pos.CENTER_LEFT);
            messageBox.getChildren().addAll(senderLabel, voiceBox);
        }

        HBox reactionSummary = createReactionSummaryNode(messageId, reactions);
        messageBox.getChildren().add(reactionSummary);

        attachMessageNode(container, messageBox, messageId, "VOICE", base64Content, isMe, sender,
                senderUsername, senderDisplayName, senderAvatar, null);
        return container;
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

    private HBox createFileMessageNode(Long messageId, String sender, String base64Content, String filename, boolean isMe, JsonArray reactions) {
        return createFileMessageNode(messageId, sender, base64Content, filename, isMe, reactions,
                isMe ? currentUsername : sender, sender, "");
    }

    private HBox createFileMessageNode(Long messageId, String sender, String base64Content, String filename, boolean isMe,
                                       JsonArray reactions, String senderUsername, String senderDisplayName, String senderAvatar) {
        HBox container = new HBox();
        container.setSpacing(10);

        VBox messageBox = new VBox();
        messageBox.setSpacing(4);
        messageBox.setMaxWidth(420);

        Label senderLabel = new Label(sender);
        senderLabel.setStyle("-fx-font-size: 11px; -fx-text-fill: #64748b;");

        Label fileNameLabel = new Label(filename == null ? "(tệp)" : filename);
        fileNameLabel.setStyle("-fx-font-size: 13px; -fx-font-weight: bold;");

        Button downloadButton = new Button("Tải xuống");
        downloadButton.setStyle("-fx-background-color: white; -fx-text-fill: #2563eb; -fx-font-weight: bold; -fx-cursor: hand; -fx-background-radius: 10px;");
        downloadButton.setOnAction(e -> {
            try {
                byte[] data = Base64.getDecoder().decode(base64Content);
                FileChooser saver = new FileChooser();
                saver.setTitle("Lưu tệp");
                saver.setInitialFileName(filename == null ? "file.bin" : filename);
                File dest = saver.showSaveDialog(txtMessage.getScene().getWindow());
                if (dest != null) {
                    Files.write(dest.toPath(), data);
                    showAlert("Hoàn tất", "Tệp đã được lưu: " + dest.getAbsolutePath(), Alert.AlertType.INFORMATION);
                }
            } catch (Exception ex) {
                showAlert("Lỗi", "Không thể lưu tệp.", Alert.AlertType.ERROR);
            }
        });

        Node fileIcon = IconUtil.createIcon("/icon/file.svg", "composer-file-icon", 1.1);
        HBox fileBox = new HBox(8, fileIcon, fileNameLabel, downloadButton);
        fileBox.setPadding(new javafx.geometry.Insets(8, 12, 8, 12));

        if (isMe) {
            fileBox.setStyle("-fx-background-color: #2563eb; -fx-background-radius: 16px 16px 4px 16px;");
            fileNameLabel.setTextFill(Color.WHITE);
            messageBox.setAlignment(Pos.CENTER_RIGHT);
            container.setAlignment(Pos.CENTER_RIGHT);
            messageBox.getChildren().add(fileBox);
        } else {
            fileBox.setStyle("-fx-background-color: #e8edf3; -fx-background-radius: 16px 16px 16px 4px;");
            fileNameLabel.setTextFill(Color.web("#172033"));
            messageBox.setAlignment(Pos.CENTER_LEFT);
            container.setAlignment(Pos.CENTER_LEFT);
            messageBox.getChildren().addAll(senderLabel, fileBox);
        }

        HBox reactionSummary = createReactionSummaryNode(messageId, reactions);
        messageBox.getChildren().add(reactionSummary);

        attachMessageNode(container, messageBox, messageId, "FILE", filename == null ? "" : filename, isMe, sender,
                senderUsername, senderDisplayName, senderAvatar, filename);
        return container;
    }

    private HBox createVideoMessageNode(Long messageId, String sender, String base64Content, String filename, boolean isMe,
                                        JsonArray reactions, String senderUsername, String senderDisplayName, String senderAvatar) {
        HBox container = new HBox();
        container.setSpacing(10);

        VBox messageBox = new VBox();
        messageBox.setSpacing(4);
        messageBox.setMaxWidth(420);

        Label senderLabel = new Label(sender);
        senderLabel.setStyle("-fx-font-size: 11px; -fx-text-fill: #64748b;");

        Node videoIcon = IconUtil.createIcon("/icon/video.svg", "composer-video-icon", 1.1);

        Label fileNameLabel = new Label(filename == null ? "video" : filename);
        fileNameLabel.setStyle("-fx-font-size: 13px; -fx-font-weight: bold;");

        Button playButton = new Button("Phát");
        playButton.setStyle("-fx-background-color: white; -fx-text-fill: #7c3aed; -fx-font-weight: bold; -fx-cursor: hand; -fx-background-radius: 10px;");
        playButton.setOnAction(e -> playVideoMessage(base64Content, filename));

        Button downloadButton = new Button("Tải xuống");
        downloadButton.setStyle("-fx-background-color: white; -fx-text-fill: #2563eb; -fx-font-weight: bold; -fx-cursor: hand; -fx-background-radius: 10px;");
        downloadButton.setOnAction(e -> saveBase64ToFile(base64Content, filename == null ? "video.mp4" : filename));

        HBox videoBox = new HBox(8, videoIcon, fileNameLabel, playButton, downloadButton);
        videoBox.setPadding(new javafx.geometry.Insets(8, 12, 8, 12));

        if (isMe) {
            fileNameLabel.setTextFill(Color.WHITE);
            videoBox.setStyle("-fx-background-color: #2563eb; -fx-background-radius: 16px 16px 4px 16px;");
            messageBox.setAlignment(Pos.CENTER_RIGHT);
            container.setAlignment(Pos.CENTER_RIGHT);
            messageBox.getChildren().add(videoBox);
        } else {
            fileNameLabel.setTextFill(Color.web("#172033"));
            videoBox.setStyle("-fx-background-color: #e8edf3; -fx-background-radius: 16px 16px 16px 4px;");
            messageBox.setAlignment(Pos.CENTER_LEFT);
            container.setAlignment(Pos.CENTER_LEFT);
            messageBox.getChildren().addAll(senderLabel, videoBox);
        }

        HBox reactionSummary = createReactionSummaryNode(messageId, reactions);
        messageBox.getChildren().add(reactionSummary);

        attachMessageNode(container, messageBox, messageId, "VIDEO", base64Content, isMe, sender,
                senderUsername, senderDisplayName, senderAvatar, filename);
        return container;
    }

    private void attachMessageNode(HBox container, VBox messageBox, Long messageId, String type, String content,
                                   boolean isMe, String senderDisplay, String senderUsername,
                                   String senderDisplayName, String senderAvatar, String filename) {
        Node avatarNode = createAvatarNode(senderAvatar, senderDisplayName, senderUsername, 30);
        avatarNode.getStyleClass().add("message-avatar");
        if (isMe) {
            container.getChildren().addAll(messageBox, avatarNode);
        } else {
            container.getChildren().addAll(avatarNode, messageBox);
        }
        container.setUserData(new MessageData(messageId, type, content, isMe, senderDisplay,
                senderUsername == null ? "" : senderUsername,
                senderAvatar == null ? "" : senderAvatar,
                filename));
    }

    private Node createAvatarNode(String avatar, String displayName, String username, double size) {
        if (avatar != null && !avatar.isBlank()) {
            if (avatar.length() > 10) {
                try {
                    byte[] bytes = Base64.getDecoder().decode(avatar);
                    Image image = new Image(new ByteArrayInputStream(bytes), size, size, false, true);
                    ImageView imageView = new ImageView(image);
                    imageView.setFitWidth(size);
                    imageView.setFitHeight(size);
                    Circle clip = new Circle(size / 2, size / 2, size / 2);
                    imageView.setClip(clip);
                    return imageView;
                } catch (Exception ignored) {
                    // dùng avatar chữ cái nếu tải ảnh lỗi
                }
            } else {
                Label emoji = new Label(avatar);
                emoji.setMinSize(size, size);
                emoji.setPrefSize(size, size);
                emoji.setAlignment(Pos.CENTER);
                emoji.getStyleClass().add("avatar-emoji");
                return emoji;
            }
        }

        StackPane avatarCircle = new StackPane();
        avatarCircle.setMinSize(size, size);
        avatarCircle.setPrefSize(size, size);
        avatarCircle.setMaxSize(size, size);
        avatarCircle.getStyleClass().add("avatar-circle");
        Label initial = new Label(getInitial(displayName != null && !displayName.isBlank() ? displayName : username));
        initial.getStyleClass().add("avatar-initial");
        avatarCircle.getChildren().add(initial);
        return avatarCircle;
    }

    private void updateHeaderAvatar(String avatar, String displayName, String username) {
        if (chatHeaderAvatar == null) {
            return;
        }
        chatHeaderAvatar.getChildren().setAll(createAvatarNode(avatar, displayName, username, 34));
    }

    private void registerUserMeta(String username, String displayName, String avatar) {
        if (username == null || username.isBlank()) {
            return;
        }
        if (displayName != null && !displayName.isBlank()) {
            userDisplayNameByUsername.put(username, displayName);
        }
        if (avatar != null && !avatar.isBlank()) {
            userAvatarByUsername.put(username, avatar);
        }
    }

    private String getJsonString(JsonObject json, String key, String fallback) {
        if (json != null && json.has(key) && !json.get(key).isJsonNull()) {
            return json.get(key).getAsString();
        }
        return fallback == null ? "" : fallback;
    }

    private String getInitial(String value) {
        if (value == null || value.isBlank()) {
            return "?";
        }
        return value.trim().substring(0, 1).toUpperCase();
    }

    private void playVideoMessage(String base64Content, String filename) {
        try {
            byte[] data = Base64.getDecoder().decode(base64Content);
            String suffix = videoSuffix(filename);
            Path tempVideo = Files.createTempFile("chat-video-", suffix);
            Files.write(tempVideo, data);

            Media media = new Media(tempVideo.toUri().toString());
            MediaPlayer mediaPlayer = new MediaPlayer(media);
            MediaView mediaView = new MediaView(mediaPlayer);
            mediaView.setPreserveRatio(true);
            mediaView.setFitWidth(760);
            mediaView.setFitHeight(480);

            Button closeButton = new Button("Đóng");
            closeButton.setOnAction(e -> ((javafx.stage.Stage) closeButton.getScene().getWindow()).close());

            VBox root = new VBox(10, mediaView, closeButton);
            root.setAlignment(Pos.CENTER);
            root.setPadding(new javafx.geometry.Insets(12));
            root.setStyle("-fx-background-color: #111827;");

            javafx.stage.Stage stage = new javafx.stage.Stage();
            stage.setTitle(filename == null || filename.isBlank() ? "Video" : filename);
            stage.initModality(javafx.stage.Modality.APPLICATION_MODAL);
            stage.setScene(new javafx.scene.Scene(root, 820, 560));
            stage.setOnHidden(e -> {
                mediaPlayer.stop();
                mediaPlayer.dispose();
                try {
                    Files.deleteIfExists(tempVideo);
                } catch (IOException ignored) {
                }
            });
            stage.show();
            mediaPlayer.play();
        } catch (Exception e) {
            showAlert("Lỗi phát video", "Không thể phát video này.", Alert.AlertType.ERROR);
        }
    }

    private String videoSuffix(String filename) {
        if (filename == null) {
            return ".mp4";
        }
        String lower = filename.toLowerCase(Locale.ROOT);
        int dot = lower.lastIndexOf('.');
        if (dot >= 0 && dot < lower.length() - 1) {
            return lower.substring(dot);
        }
        return ".mp4";
    }

    private void saveBase64ToFile(String base64Content, String filename) {
        try {
            byte[] data = Base64.getDecoder().decode(base64Content);
            FileChooser saver = new FileChooser();
            saver.setTitle("Lưu tệp");
            saver.setInitialFileName(filename == null || filename.isBlank() ? "file.bin" : filename);
            File dest = saver.showSaveDialog(txtMessage.getScene().getWindow());
            if (dest != null) {
                Files.write(dest.toPath(), data);
                showAlert("Hoàn tất", "Tệp đã được lưu: " + dest.getAbsolutePath(), Alert.AlertType.INFORMATION);
            }
        } catch (Exception ex) {
            showAlert("Lỗi", "Không thể lưu tệp.", Alert.AlertType.ERROR);
        }
    }

    private HBox createReactionSummaryNode(Long messageId, JsonArray reactions) {
        HBox reactionSummary = new HBox(6);
        reactionSummary.getStyleClass().add("reaction-summary");
        renderReactionSummary(reactionSummary, reactions);
        if (messageId != null) {
            reactionSummaryMap.put(messageId, reactionSummary);
        }
        return reactionSummary;
    }

    private void renderReactionSummary(HBox reactionSummary, JsonArray reactions) {
        reactionSummary.getChildren().clear();
        if (reactions == null || reactions.size() == 0) {
            reactionSummary.setVisible(false);
            reactionSummary.setManaged(false);
            return;
        }

        reactionSummary.setVisible(true);
        reactionSummary.setManaged(true);
        for (int i = 0; i < reactions.size(); i++) {
            JsonObject item = reactions.get(i).getAsJsonObject();
            HBox chip = new HBox(3);
            chip.getStyleClass().add("reaction-chip");
            chip.setAlignment(Pos.CENTER);

            Label countLabel = new Label(String.valueOf(item.get("count").getAsInt()));
            countLabel.getStyleClass().add("reaction-count");
            chip.getChildren().addAll(createReactionIcon(item.get("emoji").getAsString(), 16), countLabel);
            reactionSummary.getChildren().add(chip);
        }
    }

    private Node createReactionIcon(String emoji, double size) {
        ImageView imageView = new ImageView(getReactionIconImage(emoji));
        imageView.setFitWidth(size);
        imageView.setFitHeight(size);
        imageView.setPreserveRatio(true);
        imageView.setSmooth(true);
        imageView.setMouseTransparent(true);

        Label fallbackEmoji = new Label(emoji);
        fallbackEmoji.getStyleClass().add("reaction-emoji-fallback");
        fallbackEmoji.setMouseTransparent(true);

        StackPane icon = new StackPane(fallbackEmoji, imageView);
        icon.setMinSize(size, size);
        icon.setPrefSize(size, size);
        icon.setMaxSize(size, size);
        return icon;
    }

    private Image getReactionIconImage(String emoji) {
        return reactionIconImageCache.computeIfAbsent(emoji,
                key -> new Image(TWEMOJI_BASE_URL + toTwemojiCodepoint(key) + ".png", true));
    }

    private String toTwemojiCodepoint(String emoji) {
        StringBuilder codepoint = new StringBuilder();
        emoji.codePoints().forEach(value -> {
            if (value == 0xFE0E || value == 0xFE0F) {
                return;
            }
            if (codepoint.length() > 0) {
                codepoint.append("-");
            }
            codepoint.append(Integer.toHexString(value));
        });
        return codepoint.toString();
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
                        
                        if (container.getAlignment() == Pos.CENTER_RIGHT) {
                            setContextMenu(myMessageMenu);
                        } else {
                            setContextMenu(otherMessageMenu);
                        }
                        setStyle("-fx-background-color: transparent; -fx-padding: 4px 0px 4px 0px;");
                    }
                }
            };
        });
    }

    private Menu buildReactionMenu() {
        Menu reactionMenu = new Menu("Thả cảm xúc");
        List<String> emojis = Arrays.asList("👍", "❤️", "😂", "😮", "😢", "🎉");
        for (String emoji : emojis) {
            MenuItem item = new MenuItem();
            item.setGraphic(createReactionMenuGraphic(emoji));
            item.setOnAction(e -> handleSetReaction(emoji));
            reactionMenu.getItems().add(item);
        }
        return reactionMenu;
    }

    private Node createReactionMenuGraphic(String emoji) {
        HBox graphic = new HBox(8);
        graphic.getStyleClass().add("reaction-menu-graphic");
        graphic.setAlignment(Pos.CENTER_LEFT);
        graphic.getChildren().add(createReactionIcon(emoji, 18));
        return graphic;
    }

    private void handleRecallMessage() {
        int selectedIndex = listMessages.getSelectionModel().getSelectedIndex();
        if (selectedIndex == -1) return;

        Long messageIdToRecall = findMessageIdByIndex(selectedIndex);
        if (messageIdToRecall != null) {
            JsonObject payload = new JsonObject();
            payload.addProperty("messageId", messageIdToRecall);
            ClientApplication.getChatClient().sendPacket(new Packet("RECALL_MESSAGE", payload.toString()));
            
            MessageData oldData = listMessages.getItems().get(selectedIndex) instanceof HBox oldContainer
                    && oldContainer.getUserData() instanceof MessageData data ? data : null;
            HBox recalledNode = oldData == null
                    ? createTextMessageNode(messageIdToRecall, "Bạn", "🚫 Tin nhắn đã bị thu hồi", true, null)
                    : createTextMessageNode(messageIdToRecall, oldData.senderDisplay(), "🚫 Tin nhắn đã bị thu hồi", true, null,
                            oldData.senderUsername(), oldData.senderDisplay(), oldData.senderAvatar());
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
                    
                    HBox editedNode = createTextMessageNode(messageIdToEdit, oldData.senderDisplay(),
                            newContent + " (Đã chỉnh sửa)", true, null,
                            oldData.senderUsername(), oldData.senderDisplay(), oldData.senderAvatar());
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
        if (parentController != null) {
            parentController.showAlert(title, content, type);
        } else {
            Alert alert = new Alert(type);
            alert.setTitle(title);
            alert.setHeaderText(null);
            alert.setContentText(content);
            alert.showAndWait();
        }
    }

    private String privateConversationKey(String username) {
        return "PRIVATE:" + username;
    }

    private String groupConversationKey(long groupId) {
        return "GROUP:" + groupId;
    }

    private String extractUsername(String displayItem) {
        if (displayItem == null) return null;
        if (displayItem.endsWith(" [Online]")) {
            return displayItem.substring(0, displayItem.length() - 9);
        } else if (displayItem.endsWith(" [Offline]")) {
            return displayItem.substring(0, displayItem.length() - 10);
        }
        return displayItem;
    }
}
