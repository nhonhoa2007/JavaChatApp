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
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
<<<<<<< HEAD
import javafx.scene.layout.Region;
=======
>>>>>>> 67bf400d8ef98f36308a989e33fbbb4dfc6f2a3e
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.SVGPath;
import javafx.scene.text.Text;
import javafx.scene.text.TextFlow;
import javafx.stage.FileChooser;
import javafx.animation.PauseTransition;
import javafx.util.Duration;
import org.controlsfx.control.Notifications;
import org.example.client.ClientApplication;
import org.example.client.call.CallEventListener;
import org.example.client.call.CallManager;
import org.example.client.call.CallSession;
import org.example.client.util.VoicePlayer;
import org.example.client.util.VoiceRecorder;
import org.example.common.network.CallPacketTypes;
import org.example.common.network.Packet;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
<<<<<<< HEAD
import java.text.Normalizer;
import java.util.regex.Pattern;
=======
>>>>>>> 67bf400d8ef98f36308a989e33fbbb4dfc6f2a3e
import java.util.Arrays;
import java.util.Base64;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import javax.sound.sampled.LineUnavailableException;

<<<<<<< HEAD
import javafx.scene.control.cell.PropertyValueFactory;

public class ChatController {

    public static class SearchUserResult {
        private final String username;
        private final String relation; // FRIEND, PENDING_SENT, PENDING_RECEIVED, NONE
        private final String status; // ONLINE, OFFLINE

        public SearchUserResult(String username, String relation, String status) {
            this.username = username;
            this.relation = relation;
            this.status = status;
        }

        public String getUsername() { return username; }
        public String getRelation() { return relation; }
        public String getStatus() { return status; }
    }

=======
public class ChatController {

>>>>>>> 67bf400d8ef98f36308a989e33fbbb4dfc6f2a3e
    @FXML
    private ListView<String> listUsers;

    @FXML
<<<<<<< HEAD
    private ListView<SearchUserResult> listSearchResults;

    @FXML
=======
>>>>>>> 67bf400d8ef98f36308a989e33fbbb4dfc6f2a3e
    private ListView<String> listRequests;

    @FXML
    private ListView<String> listGroups;

    @FXML
    private ListView<Object> listMessages;

    @FXML
    private ListView<ConversationItem> listRecentConversations;

    @FXML
    private TextArea txtMessage;

    @FXML
<<<<<<< HEAD
=======
    private TextField txtAddFriend;

    @FXML
>>>>>>> 67bf400d8ef98f36308a989e33fbbb4dfc6f2a3e
    private TextField txtSearchFriend;

    @FXML
    private Label lblChatTitle;

    @FXML
    private Button btnCall;

    @FXML
    private Button btnBlock;

    @FXML
    private Button btnMute;

    @FXML
    private Button btnVoice;

    @FXML
    private StackPane contentStack;

    @FXML
    private VBox viewSearch;

    @FXML
    private VBox viewChat;

    @FXML
    private VBox viewRecentConversations;

    @FXML
    private VBox viewChatMessages;

    @FXML
    private VBox viewContacts;

    @FXML
    private VBox viewGroups;

<<<<<<< HEAD
    @FXML
    private VBox viewAdmin;

    @FXML
    private Button btnAdmin;

    @FXML
    private Button btnNavSearch;

    @FXML
    private Button btnNavChat;

    @FXML
    private Button btnNavContacts;

    @FXML
    private Button btnNavGroups;

    @FXML
    private TableView<AdminUserRow> tblUsers;

    @FXML
    private TableColumn<AdminUserRow, String> colUsername;

    @FXML
    private TableColumn<AdminUserRow, String> colFullName;

    @FXML
    private TableColumn<AdminUserRow, String> colRole;

    @FXML
    private TableColumn<AdminUserRow, String> colStatus;

    @FXML
    private TableColumn<AdminUserRow, Boolean> colLocked;

    @FXML
    private TextField txtAdminSearch;

    @FXML
    private TextField txtAdminUsername;

    @FXML
    private TextField txtAdminFullName;

    @FXML
    private ComboBox<String> cmbAdminRole;

    @FXML
    private PasswordField txtAdminNewPassword;

    @FXML
    private Button btnAdminSave;

    @FXML
    private Button btnAdminToggleLock;

    @FXML
    private Button btnAdminResetPw;

    private final ObservableList<AdminUserRow> allAdminUsers = FXCollections.observableArrayList();
    private final ObservableList<AdminUserRow> filteredAdminUsers = FXCollections.observableArrayList();
    private String currentUserRole = "USER";
    private boolean isAdminCreateMode = false;

    private String currentUsername;
    private final Map<Long, Integer> messageIdToIndexMap = new HashMap<>();
    private final ObservableList<String> allFriendItems = FXCollections.observableArrayList();
    private final ObservableList<SearchUserResult> allSystemUsers = FXCollections.observableArrayList();
=======
    private String currentUsername;
    private final Map<Long, Integer> messageIdToIndexMap = new HashMap<>();
    private final ObservableList<String> allFriendItems = FXCollections.observableArrayList();
>>>>>>> 67bf400d8ef98f36308a989e33fbbb4dfc6f2a3e
    private final Map<String, Long> groupDisplayToId = new HashMap<>();
    private final Map<Long, String> messageConversationMap = new HashMap<>();
    private final Map<Long, Label> reactionLabelMap = new HashMap<>();

    private final Map<String, Boolean> isBlockedByMeMap = new HashMap<>();
    private final Map<String, Boolean> isMutedByMeMap = new HashMap<>();
    private final VoiceRecorder voiceRecorder = new VoiceRecorder();
    private String voiceRecordingTarget;
    private String voiceRecordingConversation;
    private String currentConversationKey;

    // Recent conversations tracking
    private final ObservableList<ConversationItem> recentConversations = FXCollections.observableArrayList();
    private boolean suppressConversationSelection;

    private static final String LOCK_ICON = "M17 8h-1V6c0-2.21-1.79-4-4-4S8 3.79 8 6v2H7c-1.1 0-2 .9-2 2v10c0 1.1 .9 2 2 2h10c1.1 0 2-.9 2-2V10c0-1.1-.9-2-2-2zM10 6c0-1.1 .9-2 2-2s2 .9 2 2v2h-4V6zm3 10.73V18h-2v-1.27c-.6-.35-1-.99-1-1.73 0-1.1 .9-2 2-2s2 .9 2 2c0 .74-.4 1.38-1 1.73z";
    private static final String UNLOCK_ICON = "M17 8h-7V6c0-1.1 .9-2 2-2 .73 0 1.41 .4 1.76 1.04l1.75-.96C14.8 2.8 13.47 2 12 2 9.79 2 8 3.79 8 6v2H7c-1.1 0-2 .9-2 2v10c0 1.1 .9 2 2 2h10c1.1 0 2-.9 2-2V10c0-1.1-.9-2-2-2zm-4 8.73V18h-2v-1.27c-.6-.35-1-.99-1-1.73 0-1.1 .9-2 2-2s2 .9 2 2c0 .74-.4 1.38-1 1.73z";
    private static final String BELL_ICON = "M12 22c1.1 0 2-.9 2-2h-4c0 1.1 .9 2 2 2zm6-6v-5c0-3.07-1.63-5.64-4.5-6.32V4c0-.83-.67-1.5-1.5-1.5S10.5 3.17 10.5 4v.68C7.63 5.36 6 7.92 6 11v5l-2 2v1h16v-1l-2-2z";
    private static final String BELL_OFF_ICON = "M20.59 21.99 2.01 3.41 3.42 2l18.58 18.58-1.41 1.41zM18 16v-5c0-3.07-1.63-5.64-4.5-6.32V4c0-.83-.67-1.5-1.5-1.5-.77 0-1.39 .58-1.48 1.32L18 11.29V16zm-2.18 3H4v-1l2-2v-5c0-1.17 .24-2.27 .68-3.24L15.82 17H20v1l-2 2h-2.18zM12 22c1.1 0 2-.9 2-2h-4c0 1.1 .9 2 2 2z";

    private record MessageData(Long messageId, String type, String content, boolean isMe, String senderDisplay) {}

<<<<<<< HEAD
    public static class AdminUserRow {
        private final Long id;
        private final String username;
        private final String fullName;
        private final String role;
        private final String status;
        private final boolean locked;
        private final String lastSeen;

        public AdminUserRow(Long id, String username, String fullName, String role, String status, boolean locked, String lastSeen) {
            this.id = id;
            this.username = username;
            this.fullName = fullName;
            this.role = role;
            this.status = status;
            this.locked = locked;
            this.lastSeen = lastSeen;
        }

        public Long getId() { return id; }
        public String getUsername() { return username; }
        public String getFullName() { return fullName; }
        public String getRole() { return role; }
        public String getStatus() { return status; }
        public boolean isLocked() { return locked; }
        public String getLastSeen() { return lastSeen; }
    }

=======
>>>>>>> 67bf400d8ef98f36308a989e33fbbb4dfc6f2a3e
    private record ConversationItem(String key, String title, String type, String preview, String timestamp) {
        @Override
        public String toString() {
            String prefix = "GROUP".equals(type) ? "[Nhóm] " : "";
            if (preview == null || preview.isBlank()) {
                return prefix + title;
            }
            return prefix + title + System.lineSeparator() + preview;
        }
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

    private String buildFriendDisplayText(String username, String status) {
        return username + ("ONLINE".equals(status) ? " [Online]" : " [Offline]");
    }

<<<<<<< HEAD
    private static String removeAccents(String src) {
        if (src == null) return "";
        String nfdNormalizedString = Normalizer.normalize(src, Normalizer.Form.NFD);
        Pattern pattern = Pattern.compile("\\p{InCombiningDiacriticalMarks}+");
        return pattern.matcher(nfdNormalizedString).replaceAll("").replace('đ', 'd').replace('Đ', 'D');
    }

    private static int levenshteinDistance(String s1, String s2) {
        int len1 = s1.length();
        int len2 = s2.length();
        int[] prev = new int[len2 + 1];
        int[] curr = new int[len2 + 1];
        for (int j = 0; j <= len2; j++) {
            prev[j] = j;
        }
        for (int i = 1; i <= len1; i++) {
            curr[0] = i;
            for (int j = 1; j <= len2; j++) {
                int cost = (s1.charAt(i - 1) == s2.charAt(j - 1)) ? 0 : 1;
                curr[j] = Math.min(Math.min(curr[j - 1] + 1, prev[j] + 1), prev[j - 1] + cost);
            }
            int[] temp = prev;
            prev = curr;
            curr = temp;
        }
        return prev[len2];
    }

    private static boolean isSubsequence(String str, String query) {
        int i = 0, j = 0;
        while (i < str.length() && j < query.length()) {
            if (str.charAt(i) == query.charAt(j)) {
                j++;
            }
            i++;
        }
        return j == query.length();
    }

    private static double computeSearchScore(String username, String query) {
        if (query == null || query.isEmpty()) {
            return 1.0;
        }
        
        String normUser = removeAccents(username).toLowerCase();
        String normQuery = removeAccents(query).toLowerCase();
        
        if (normUser.equals(normQuery)) {
            return 100.0;
        }
        if (normUser.startsWith(normQuery)) {
            return 90.0 + (double) normQuery.length() / normUser.length();
        }
        if (normUser.contains(normQuery)) {
            return 80.0 + (double) normQuery.length() / normUser.length();
        }
        if (isSubsequence(normUser, normQuery)) {
            return 60.0 + (double) normQuery.length() / normUser.length();
        }
        if (normQuery.length() >= 3) {
            int distance = levenshteinDistance(normUser, normQuery);
            if (distance <= 2) {
                return 50.0 - distance;
            }
        }
        return 0.0;
    }

=======
>>>>>>> 67bf400d8ef98f36308a989e33fbbb4dfc6f2a3e
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

    private void switchView(VBox activeView) {
        if (viewSearch != null) {
            viewSearch.setVisible(viewSearch == activeView);
            viewSearch.setManaged(viewSearch == activeView);
        }
        if (viewChat != null) {
            viewChat.setVisible(viewChat == activeView);
            viewChat.setManaged(viewChat == activeView);
        }
        if (viewContacts != null) {
            viewContacts.setVisible(viewContacts == activeView);
            viewContacts.setManaged(viewContacts == activeView);
        }
        if (viewGroups != null) {
            viewGroups.setVisible(viewGroups == activeView);
            viewGroups.setManaged(viewGroups == activeView);
        }
<<<<<<< HEAD
        if (viewAdmin != null) {
            viewAdmin.setVisible(viewAdmin == activeView);
            viewAdmin.setManaged(viewAdmin == activeView);
        }
=======
>>>>>>> 67bf400d8ef98f36308a989e33fbbb4dfc6f2a3e

        if (viewChat == activeView) {
            if (viewRecentConversations != null) {
                viewRecentConversations.setVisible(true);
                viewRecentConversations.setManaged(true);
            }
            if (viewChatMessages != null) {
                viewChatMessages.setVisible(false);
                viewChatMessages.setManaged(false);
            }
        }
    }

    private void switchToChatMessages() {
        if (viewRecentConversations != null) {
            viewRecentConversations.setVisible(false);
            viewRecentConversations.setManaged(false);
        }
        if (viewChatMessages != null) {
            viewChatMessages.setVisible(true);
            viewChatMessages.setManaged(true);
        }
    }

    private void switchToRecentConversations() {
        if (viewRecentConversations != null) {
            viewRecentConversations.setVisible(true);
            viewRecentConversations.setManaged(true);
        }
        if (viewChatMessages != null) {
            viewChatMessages.setVisible(false);
            viewChatMessages.setManaged(false);
        }
    }

    @FXML
    public void handleShowSearchView(ActionEvent event) {
        switchView(viewSearch);
<<<<<<< HEAD
        requestSearchAllUsers();
=======
>>>>>>> 67bf400d8ef98f36308a989e33fbbb4dfc6f2a3e
    }

    @FXML
    public void handleShowChatView(ActionEvent event) {
        switchView(viewChat);
        switchToRecentConversations();
        requestConversationList();
    }

    @FXML
    public void handleShowContactsView(ActionEvent event) {
        switchView(viewContacts);
    }

    @FXML
    public void handleShowGroupsView(ActionEvent event) {
        switchView(viewGroups);
    }

    private void applyFriendFilter() {
        String selectedUsername = extractUsername(listUsers.getSelectionModel().getSelectedItem());
<<<<<<< HEAD
        String keyword = txtSearchFriend == null ? "" : txtSearchFriend.getText().trim();

        // 1. Update the original listUsers (Contacts tab) with simple substring filter
        listUsers.getItems().setAll(
                allFriendItems.stream()
                        .filter(item -> keyword.isEmpty() || extractUsername(item).toLowerCase().contains(keyword.toLowerCase()))
=======
        String keyword = txtSearchFriend == null ? "" : txtSearchFriend.getText().trim().toLowerCase();

        listUsers.getItems().setAll(
                allFriendItems.stream()
                        .filter(item -> keyword.isEmpty() || extractUsername(item).toLowerCase().contains(keyword))
>>>>>>> 67bf400d8ef98f36308a989e33fbbb4dfc6f2a3e
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
<<<<<<< HEAD

        // 2. Update listSearchResults (Search tab) using the fuzzy/typo-tolerant scoring algorithm
        if (listSearchResults != null) {
            if (keyword.isEmpty()) {
                // If search is empty, display all system users sorted alphabetically
                listSearchResults.getItems().setAll(
                        allSystemUsers.stream()
                                .sorted((a, b) -> a.getUsername().compareToIgnoreCase(b.getUsername()))
                                .toList()
                );
            } else {
                class SearchResult {
                    final SearchUserResult user;
                    final double score;
                    SearchResult(SearchUserResult user, double score) {
                        this.user = user;
                        this.score = score;
                    }
                }
                
                List<SearchUserResult> sortedResults = allSystemUsers.stream()
                        .map(user -> new SearchResult(user, computeSearchScore(user.getUsername(), keyword)))
                        .filter(obj -> obj.score > 0)
                        .sorted((a, b) -> Double.compare(b.score, a.score))
                        .map(obj -> obj.user)
                        .toList();
                
                listSearchResults.getItems().setAll(sortedResults);
            }
        }
=======
>>>>>>> 67bf400d8ef98f36308a989e33fbbb4dfc6f2a3e
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

        CallManager.init(ClientApplication.getChatClient());
        ClientApplication.getChatClient().addListener(CallManager.getInstance()::handlePacket);

        // Voice call event listener — mở UI thật thay vì Alert
        CallManager.getInstance().addListener(new CallEventListener() {
            @Override
            public void onIncomingCall(CallSession session) {
                Platform.runLater(() -> openIncomingCallWindow(session));
            }

            @Override
            public void onOutgoingCallStarted(CallSession session) {
                Platform.runLater(() -> openCallViewWindow(session));
            }

            @Override
            public void onCallActive(CallSession session) {
                Platform.runLater(() -> {
                    // Đóng popup incoming call nếu đang hiện (callee vừa accept)
                    closeIncomingCallWindow();
                    // Nếu chưa có CallView (callee side), mở nó
                    if (callViewStage == null || !callViewStage.isShowing()) {
                        openCallViewWindow(session);
                    }
                    // Caller side: CallView đã mở từ onOutgoingCallStarted, nó tự update qua listener riêng
                });
            }

            @Override
            public void onCallEnded(CallSession session, String reason) {
                Platform.runLater(() -> {
                    closeIncomingCallWindow();
                    closeCallViewWindow();
                    showPushNotification("Cuộc gọi", reason);
                });
            }
        });

        // Default tab after login is Chat.
        switchView(viewChat);

        if (txtSearchFriend != null) {
            // Debounce 200ms: không rebuild list ngay lập tức sau mỗi keystroke
            PauseTransition searchDebounce = new PauseTransition(Duration.millis(200));
            searchDebounce.setOnFinished(e -> applyFriendFilter());
            txtSearchFriend.textProperty().addListener((obs, oldValue, newValue) -> {
                searchDebounce.stop();
                searchDebounce.playFromStart();
            });
        }
        
        // Setup recent conversations list
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

                    VBox box = new VBox(4, title, preview);
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

        listUsers.getSelectionModel().selectedItemProperty().addListener((observable, oldValue, newValue) -> {
            if (suppressConversationSelection) {
                return;
            }
            String selectedUser = extractUsername(newValue);
            if (selectedUser != null) {
                if (listGroups != null) {
                    listGroups.getSelectionModel().clearSelection();
                }
                currentConversationKey = privateConversationKey(selectedUser);
                lblChatTitle.setText("Chat với: " + selectedUser);
                loadHistory(selectedUser);
                updateControlButtons(selectedUser);
                switchToChatMessages();
                addToRecentConversations(selectedUser, currentConversationKey);
            } else {
                clearChatPaneForConversation("Chọn cuộc trò chuyện để bắt đầu");
                currentConversationKey = null;
                if (btnBlock != null) btnBlock.setVisible(true);
                if (btnMute != null) btnMute.setVisible(false);
                switchToRecentConversations();
            }
        });

<<<<<<< HEAD
        if (listSearchResults != null) {
            listSearchResults.setCellFactory(lv -> new ListCell<>() {
                private final HBox hbox = new HBox(10);
                private final Label lblStatus = new Label("●");
                private final Label lblName = new Label();
                private final Label lblRelation = new Label();
                private final Button btnAction = new Button();
                private final Region spacer = new Region();

                {
                    hbox.setAlignment(Pos.CENTER_LEFT);
                    hbox.setStyle("-fx-padding: 5 10 5 10;");
                    HBox.setHgrow(spacer, Priority.ALWAYS);
                    
                    lblRelation.setStyle("-fx-text-fill: #64748b; -fx-font-style: italic; -fx-font-size: 12px;");
                    
                    // Styled buttons
                    btnAction.setStyle("-fx-background-color: #2563eb; -fx-text-fill: white; -fx-background-radius: 4px; -fx-font-size: 12px; -fx-padding: 4 8 4 8; -fx-cursor: hand;");
                    
                    hbox.getChildren().addAll(lblStatus, lblName, lblRelation, spacer, btnAction);
                }

                @Override
                protected void updateItem(SearchUserResult item, boolean empty) {
                    super.updateItem(item, empty);
                    if (empty || item == null) {
                        setGraphic(null);
                        setText(null);
                    } else {
                        // 1. Status dot
                        if ("ONLINE".equals(item.getStatus())) {
                            lblStatus.setStyle("-fx-text-fill: #22c55e; -fx-font-size: 14px;");
                        } else {
                            lblStatus.setStyle("-fx-text-fill: #94a3b8; -fx-font-size: 14px;");
                        }

                        // 2. Name
                        lblName.setText(item.getUsername());
                        lblName.setStyle("-fx-font-weight: bold; -fx-font-size: 13px; -fx-text-fill: #1e293b;");

                        // 3. Relation & Button
                        switch (item.getRelation()) {
                            case "FRIEND":
                                lblRelation.setText("(bạn bè)");
                                btnAction.setVisible(false);
                                btnAction.setManaged(false);
                                break;
                            case "PENDING_SENT":
                                lblRelation.setText("");
                                btnAction.setText("Đã gửi");
                                btnAction.setDisable(true);
                                btnAction.setStyle("-fx-background-color: #cbd5e1; -fx-text-fill: #64748b; -fx-background-radius: 4px; -fx-font-size: 12px; -fx-padding: 4 8 4 8;");
                                btnAction.setVisible(true);
                                btnAction.setManaged(true);
                                break;
                            case "PENDING_RECEIVED":
                                lblRelation.setText("");
                                btnAction.setText("Chấp nhận");
                                btnAction.setDisable(false);
                                btnAction.setStyle("-fx-background-color: #22c55e; -fx-text-fill: white; -fx-background-radius: 4px; -fx-font-size: 12px; -fx-padding: 4 8 4 8; -fx-cursor: hand;");
                                btnAction.setVisible(true);
                                btnAction.setManaged(true);
                                btnAction.setOnAction(e -> handleAcceptFriendFromSearch(item.getUsername()));
                                break;
                            case "NONE":
                            default:
                                lblRelation.setText("");
                                btnAction.setText("Thêm bạn");
                                btnAction.setDisable(false);
                                btnAction.setStyle("-fx-background-color: #2563eb; -fx-text-fill: white; -fx-background-radius: 4px; -fx-font-size: 12px; -fx-padding: 4 8 4 8; -fx-cursor: hand;");
                                btnAction.setVisible(true);
                                btnAction.setManaged(true);
                                btnAction.setOnAction(e -> handleAddFriendFromSearch(item.getUsername()));
                                break;
                        }
                        setGraphic(hbox);
                    }
                }
            });

            // Click listener
            listSearchResults.getSelectionModel().selectedItemProperty().addListener((observable, oldValue, newValue) -> {
                if (suppressConversationSelection || newValue == null) {
                    return;
                }
                // Click on search result is only for chatting with friends
                if ("FRIEND".equals(newValue.getRelation())) {
                    String selectedUser = newValue.getUsername();
                    suppressConversationSelection = true;
                    try {
                        if (listGroups != null) {
                            listGroups.getSelectionModel().clearSelection();
                        }
                        if (listUsers != null) {
                            listUsers.getSelectionModel().clearSelection();
                        }
                    } finally {
                        suppressConversationSelection = false;
                    }
                    currentConversationKey = privateConversationKey(selectedUser);
                    lblChatTitle.setText("Chat với: " + selectedUser);
                    loadHistory(selectedUser);
                    updateControlButtons(selectedUser);
                    switchView(viewChat);
                    switchToChatMessages();
                    addToRecentConversations(selectedUser, currentConversationKey);
                }
                
                // Clear selection so selecting again works
                Platform.runLater(() -> {
                    suppressConversationSelection = true;
                    try {
                        listSearchResults.getSelectionModel().clearSelection();
                    } finally {
                        suppressConversationSelection = false;
                    }
                });
            });
        }

=======
>>>>>>> 67bf400d8ef98f36308a989e33fbbb4dfc6f2a3e
        if (listGroups != null) {
            listGroups.getSelectionModel().selectedItemProperty().addListener((obs, oldValue, newValue) -> {
                if (suppressConversationSelection) {
                    return;
                }
                if (newValue != null) {
                    listUsers.getSelectionModel().clearSelection();
                    Long groupId = extractGroupIdFromDisplay(newValue);
                    if (groupId != null) {
                        currentConversationKey = groupConversationKey(groupId);
                        lblChatTitle.setText("Nhóm: " + newValue);
                        loadGroupHistory(groupId);
                        if (btnCall != null) btnCall.setVisible(false);
                        if (btnBlock != null) btnBlock.setVisible(false);
                        if (btnMute != null) btnMute.setVisible(false);
                        switchToChatMessages();
                        addToRecentConversations(newValue, currentConversationKey);
                    }
                }
            });
        }

        setupMessageContextMenu();
        setupRequestContextMenu();

<<<<<<< HEAD
        ClientApplication.getChatClient().sendPacket(new Packet("LOAD_FRIENDS_REQUEST", ""));
        ClientApplication.getChatClient().sendPacket(new Packet("GROUP_LIST_REQUEST", ""));
        ClientApplication.getChatClient().sendPacket(new Packet("GET_USER_INFO", ""));
        requestConversationList();

        // Initialize Admin Dashboard TableView & ComboBox
        if (tblUsers != null) {
            colUsername.setCellValueFactory(new PropertyValueFactory<>("username"));
            colFullName.setCellValueFactory(new PropertyValueFactory<>("fullName"));
            colRole.setCellValueFactory(new PropertyValueFactory<>("role"));
            colStatus.setCellValueFactory(new PropertyValueFactory<>("status"));
            colLocked.setCellValueFactory(new PropertyValueFactory<>("locked"));

            tblUsers.setItems(allAdminUsers);

            tblUsers.getSelectionModel().selectedItemProperty().addListener((obs, oldSelection, newSelection) -> {
                if (newSelection != null) {
                    if (isAdminCreateMode) {
                        toggleAdminCreateMode(false);
                    }
                    txtAdminUsername.setText(newSelection.getUsername());
                    txtAdminFullName.setText(newSelection.getFullName());
                    cmbAdminRole.setValue(newSelection.getRole());
                    btnAdminToggleLock.setText(newSelection.isLocked() ? "Mở khóa" : "Khóa");
                    txtAdminNewPassword.clear();
                }
            });
        }
        if (cmbAdminRole != null) {
            cmbAdminRole.getItems().addAll("USER", "ADMIN");
            cmbAdminRole.setValue("USER");
        }
        if (txtAdminSearch != null) {
            txtAdminSearch.textProperty().addListener((obs, oldValue, newValue) -> {
                handleAdminSearch(null);
            });
        }
=======
        // Chủ động request danh sách bạn bè ngay khi ChatController đã sẵn sàng nhận response.
        // Trước đây server gửi LOAD_FRIENDS_SUCCESS ngay sau LOGIN_SUCCESS nhưng lúc đó
        // LoginController vẫn đang là handler → packet bị bỏ qua.
        ClientApplication.getChatClient().sendPacket(new Packet("LOAD_FRIENDS_REQUEST", ""));
        ClientApplication.getChatClient().sendPacket(new Packet("GROUP_LIST_REQUEST", ""));
        requestConversationList();
>>>>>>> 67bf400d8ef98f36308a989e33fbbb4dfc6f2a3e
    }

    private void handleRecentConversationSelected(ConversationItem item) {
        String conversationKey = item.key();
        if (conversationKey.startsWith("PRIVATE:")) {
            openPrivateConversation(conversationKey.substring(8), false);
        } else if (conversationKey.startsWith("GROUP:")) {
            openGroupConversation(item.title(), Long.parseLong(conversationKey.substring(6)), false);
        }
    }

    private void addToRecentConversations(String displayName, String conversationKey) {
        suppressConversationSelection = true;
        try {
            String existingPreview = "";
            String existingTimestamp = "";
            for (ConversationItem item : recentConversations) {
                if (item.key().equals(conversationKey)) {
                    existingPreview = item.preview();
                    existingTimestamp = item.timestamp();
                    break;
                }
            }
            recentConversations.removeIf(item -> item.key().equals(conversationKey));
            ConversationItem updatedItem = new ConversationItem(
                    conversationKey,
                    displayName,
                    conversationKey.startsWith("GROUP:") ? "GROUP" : "PRIVATE",
                    existingPreview,
                    existingTimestamp
            );
            recentConversations.add(0, updatedItem);

            if (listRecentConversations != null && conversationKey.equals(currentConversationKey)) {
                listRecentConversations.getSelectionModel().select(updatedItem);
            }
        } finally {
            suppressConversationSelection = false;
        }
    }

    private void requestConversationList() {
        ClientApplication.getChatClient().sendPacket(new Packet("CONVERSATION_LIST_REQUEST", ""));
    }

    private void openPrivateConversation(String username, boolean addToRecent) {
        suppressConversationSelection = true;
        try {
            if (listGroups != null) {
                listGroups.getSelectionModel().clearSelection();
            }
            if (listUsers != null) {
                listUsers.getSelectionModel().clearSelection();
            }
        } finally {
            suppressConversationSelection = false;
        }

        currentConversationKey = privateConversationKey(username);
        clearChatPaneForConversation("Chat với: " + username);
        updateControlButtons(username);
        switchToChatMessages();
        loadHistory(username);
        if (addToRecent) {
            addToRecentConversations(username, currentConversationKey);
        }
    }

    private void openGroupConversation(String groupTitle, long groupId, boolean addToRecent) {
        suppressConversationSelection = true;
        try {
            if (listUsers != null) {
                listUsers.getSelectionModel().clearSelection();
            }
            if (listGroups != null) {
                listGroups.getSelectionModel().clearSelection();
            }
        } finally {
            suppressConversationSelection = false;
        }

        currentConversationKey = groupConversationKey(groupId);
        clearChatPaneForConversation("Nhóm: " + groupTitle);
        if (btnCall != null) btnCall.setVisible(false);
        if (btnBlock != null) btnBlock.setVisible(false);
        if (btnMute != null) btnMute.setVisible(false);
        switchToChatMessages();
        loadGroupHistory(groupId);
        if (addToRecent) {
            addToRecentConversations(groupTitle, currentConversationKey);
        }
    }

    private String getActivePrivateTarget() {
        if (currentConversationKey != null && currentConversationKey.startsWith("PRIVATE:")) {
            return currentConversationKey.substring(8);
        }
        if (currentConversationKey != null) {
            return null;
        }
        return extractUsername(listUsers.getSelectionModel().getSelectedItem());
    }

    private Long getActiveGroupTargetId() {
        if (currentConversationKey != null && currentConversationKey.startsWith("GROUP:")) {
            return Long.parseLong(currentConversationKey.substring(6));
        }
        if (currentConversationKey != null) {
            return null;
        }
        String selectedGroupDisplay = listGroups != null ? listGroups.getSelectionModel().getSelectedItem() : null;
        return selectedGroupDisplay != null ? extractGroupIdFromDisplay(selectedGroupDisplay) : null;
    }

    private void updateControlButtons(String targetUser) {
        if (btnBlock == null || btnMute == null) return;
        if (btnCall != null) btnCall.setVisible(true);
        btnBlock.setVisible(true);
        btnMute.setVisible(true);

        boolean isBlocked = isBlockedByMeMap.getOrDefault(targetUser, false);
        configureActionButton(
                btnBlock,
                isBlocked ? UNLOCK_ICON : LOCK_ICON,
                isBlocked ? "Mở chặn" : "Chặn",
                isBlocked ? "success-button" : "danger-button");

        boolean isMuted = isMutedByMeMap.getOrDefault(targetUser, false);
        configureActionButton(
                btnMute,
                isMuted ? BELL_ICON : BELL_OFF_ICON,
                isMuted ? "Mở TB" : "Tắt TB",
                isMuted ? "success-button" : "warning-button");
    }

    private void configureActionButton(Button button, String iconPath, String tooltipText, String stateStyleClass) {
        SVGPath icon = new SVGPath();
        icon.setContent(iconPath);
        icon.setScaleX(0.82);
        icon.setScaleY(0.82);
        icon.getStyleClass().add("action-button-icon");

        button.setText(null);
        button.setGraphic(icon);
        button.setContentDisplay(ContentDisplay.GRAPHIC_ONLY);
        button.setTooltip(new Tooltip(tooltipText));
        button.setAccessibleText(tooltipText);
        button.getStyleClass().setAll("button", "control-icon-button", stateStyleClass);
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
<<<<<<< HEAD
                case "SEARCH_ALL_USERS_SUCCESS":
                    handleSearchAllUsersSuccess(packet.getPayload());
                    break;
=======
>>>>>>> 67bf400d8ef98f36308a989e33fbbb4dfc6f2a3e
                case "GROUP_LIST":
                    handleGroupList(packet.getPayload());
                    break;
                case "CONVERSATION_LIST":
                    handleConversationList(packet.getPayload());
                    break;
                case "GROUP_LIST_UPDATED":
                    ClientApplication.getChatClient().sendPacket(new Packet("GROUP_LIST_REQUEST", ""));
                    requestConversationList();
                    break;
                case "GROUP_SUCCESS":
                    showAlert("Thông báo", packet.getPayload(), Alert.AlertType.INFORMATION);
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
<<<<<<< HEAD
                    requestSearchAllUsers();
=======
                    txtAddFriend.clear();
>>>>>>> 67bf400d8ef98f36308a989e33fbbb4dfc6f2a3e
                    break;
                case "FRIEND_ERROR":
                    showAlert("Lỗi Kết Bạn", packet.getPayload(), Alert.AlertType.ERROR);
                    break;
                case "RELOAD_FRIENDS":
                    ClientApplication.getChatClient().sendPacket(new Packet("LOAD_FRIENDS_REQUEST", ""));
<<<<<<< HEAD
                    requestSearchAllUsers();
=======
>>>>>>> 67bf400d8ef98f36308a989e33fbbb4dfc6f2a3e
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
<<<<<<< HEAD
                case "USER_INFO":
                    handleUserInfo(packet.getPayload());
                    break;
                case "ADMIN_USER_LIST":
                    handleAdminUserList(packet.getPayload());
                    break;
                case "ADMIN_SUCCESS":
                    showAlert("Thành công", packet.getPayload(), Alert.AlertType.INFORMATION);
                    break;
                case "ADMIN_ERROR":
                    showAlert("Lỗi hệ thống", packet.getPayload(), Alert.AlertType.ERROR);
                    break;
                case "FORCE_LOGOUT":
                    handleForceLogout(packet.getPayload());
                    break;
=======
>>>>>>> 67bf400d8ef98f36308a989e33fbbb4dfc6f2a3e
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

        refreshConversationsFromLocalState();
        // Chỉ request conversation từ server nếu chưa có data gì (lần đầu load)
        // Tránh gửi request thừa sau mỗi lần load friends/groups
        if (recentConversations.isEmpty()) {
            requestConversationList();
        }
    }

<<<<<<< HEAD
    private void handleSearchAllUsersSuccess(String payload) {
        JsonObject json = JsonParser.parseString(payload).getAsJsonObject();
        JsonArray users = json.getAsJsonArray("users");

        allSystemUsers.clear();
        for (int i = 0; i < users.size(); i++) {
            JsonObject u = users.get(i).getAsJsonObject();
            String username = u.get("username").getAsString();
            String status = u.get("status").getAsString();
            String relation = u.get("relation").getAsString();

            allSystemUsers.add(new SearchUserResult(username, relation, status));
        }

        applyFriendFilter();
    }

    private void requestSearchAllUsers() {
        ClientApplication.getChatClient().sendPacket(new Packet("SEARCH_ALL_USERS_REQUEST", ""));
    }

    private void handleAddFriendFromSearch(String username) {
        JsonObject payload = new JsonObject();
        payload.addProperty("friendUsername", username);
        ClientApplication.getChatClient().sendPacket(new Packet("ADD_FRIEND_REQUEST", payload.toString()));
    }

    private void handleAcceptFriendFromSearch(String username) {
        JsonObject payload = new JsonObject();
        payload.addProperty("senderUsername", username);
        ClientApplication.getChatClient().sendPacket(new Packet("ACCEPT_FRIEND_REQUEST", payload.toString()));
    }

=======
>>>>>>> 67bf400d8ef98f36308a989e33fbbb4dfc6f2a3e
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

            // Chỉ update 1 item thay vì rebuild toàn bộ list
            boolean found = false;
            for (int i = 0; i < allFriendItems.size(); i++) {
                String item = allFriendItems.get(i);
                if (user.equals(extractUsername(item))) {
                    String newDisplayText = buildFriendDisplayText(user, status);
                    allFriendItems.set(i, newDisplayText);
                    found = true;
                    break;
                }
            }

            // Chỉ apply filter nếu user này có trong danh sách
            if (found) {
                String keyword = txtSearchFriend == null ? "" : txtSearchFriend.getText().trim().toLowerCase();
                // Tìm và update trực tiếp trong visible list để tránh rebuild toàn bộ
                for (int i = 0; i < listUsers.getItems().size(); i++) {
                    String visibleItem = listUsers.getItems().get(i);
                    if (user.equals(extractUsername(visibleItem))) {
                        String newText = buildFriendDisplayText(user, status);
                        listUsers.getItems().set(i, newText);
                        break;
                    }
                }
<<<<<<< HEAD
                if (listSearchResults != null) {
                    for (int i = 0; i < listSearchResults.getItems().size(); i++) {
                        SearchUserResult visibleItem = listSearchResults.getItems().get(i);
                        if (user.equals(visibleItem.getUsername())) {
                            listSearchResults.getItems().set(i, new SearchUserResult(user, visibleItem.getRelation(), status));
                            break;
                        }
                    }
                }
=======
>>>>>>> 67bf400d8ef98f36308a989e33fbbb4dfc6f2a3e
                // Restore selection nếu đang chọn user này
                if (user.equals(selectedUser)) {
                    for (String item : listUsers.getItems()) {
                        if (user.equals(extractUsername(item))) {
                            listUsers.getSelectionModel().select(item);
                            break;
                        }
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

        refreshConversationsFromLocalState();
        // Tương tự: chỉ request nếu chưa có data, tránh render waterfall
        if (recentConversations.isEmpty()) {
            requestConversationList();
        }
    }

    private void handleConversationList(String payload) {
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
                        item.has("timestamp") ? item.get("timestamp").getAsString() : ""
                );
                conversationsByKey.put(conversation.key(), conversation);
            }
        }

        for (String friendItem : allFriendItems) {
            String username = extractUsername(friendItem);
            if (username == null || username.isBlank()) {
                continue;
            }

            String key = privateConversationKey(username);
            conversationsByKey.putIfAbsent(key, new ConversationItem(key, username, "PRIVATE", "", ""));
        }

        if (listGroups != null) {
            for (String groupDisplay : listGroups.getItems()) {
                Long groupId = extractGroupIdFromDisplay(groupDisplay);
                if (groupId == null) {
                    continue;
                }

                String key = groupConversationKey(groupId);
                conversationsByKey.putIfAbsent(key, new ConversationItem(key, groupDisplay, "GROUP", "", ""));
            }
        }

        return List.copyOf(conversationsByKey.values());
    }

    private void refreshConversationsFromLocalState() {
        String selectedKey = listRecentConversations != null && listRecentConversations.getSelectionModel().getSelectedItem() != null
                ? listRecentConversations.getSelectionModel().getSelectedItem().key()
                : currentConversationKey;

        Map<String, ConversationItem> conversationsByKey = new LinkedHashMap<>();
        for (ConversationItem item : recentConversations) {
            conversationsByKey.put(item.key(), item);
        }

        for (String friendItem : allFriendItems) {
            String username = extractUsername(friendItem);
            if (username == null || username.isBlank()) {
                continue;
            }

            String key = privateConversationKey(username);
            conversationsByKey.putIfAbsent(key, new ConversationItem(key, username, "PRIVATE", "", ""));
        }

        if (listGroups != null) {
            for (String groupDisplay : listGroups.getItems()) {
                Long groupId = extractGroupIdFromDisplay(groupDisplay);
                if (groupId == null) {
                    continue;
                }

                String key = groupConversationKey(groupId);
                conversationsByKey.putIfAbsent(key, new ConversationItem(key, groupDisplay, "GROUP", "", ""));
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
            String filename = msg.has("filename") ? msg.get("filename").getAsString() : null;

            HBox node = createMessageNodeByType(id, sender, content, isMe, type, msg.has("reactions") ? msg.getAsJsonArray("reactions") : null, filename);
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
        String filename = json.has("filename") ? json.get("filename").getAsString() : null;
        HBox node = createMessageNodeByType(messageId, "Bạn", content, true, type, json.has("reactions") ? json.getAsJsonArray("reactions") : null, filename);
        listMessages.getItems().add(node);
        messageIdToIndexMap.put(messageId, listMessages.getItems().size() - 1);
        messageConversationMap.put(messageId, conversationKey);

        // Add group to recent conversations
        if (listGroups != null) {
            String groupDisplay = null;
            for (String item : listGroups.getItems()) {
                if (extractGroupIdFromDisplay(item) == groupId) {
                    groupDisplay = item;
                    break;
                }
            }
            if (groupDisplay != null) {
                addToRecentConversations(groupDisplay, conversationKey);
            }
        }
    }

    private void handleIncomingGroupMessage(String payload) {
        JsonObject json = JsonParser.parseString(payload).getAsJsonObject();
        long groupId = json.get("groupId").getAsLong();
        String conversationKey = groupConversationKey(groupId);

        long messageId = json.get("id").getAsLong();
        String sender = json.get("sender").getAsString();
        String content = json.get("content").getAsString();
        String type = json.has("type") ? json.get("type").getAsString() : "TEXT";
        String filename = json.has("filename") ? json.get("filename").getAsString() : null;

        if (conversationKey.equals(currentConversationKey)) {
            HBox node = createMessageNodeByType(messageId, sender, content, false, type, json.has("reactions") ? json.getAsJsonArray("reactions") : null, filename);
            listMessages.getItems().add(node);
            messageIdToIndexMap.put(messageId, listMessages.getItems().size() - 1);
            messageConversationMap.put(messageId, conversationKey);
        }

        // Add group to recent conversations
        if (listGroups != null) {
            String groupDisplay = null;
            for (String item : listGroups.getItems()) {
                if (extractGroupIdFromDisplay(item) == groupId) {
                    groupDisplay = item;
                    break;
                }
            }
            if (groupDisplay != null) {
                addToRecentConversations(groupDisplay, conversationKey);
            }
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
<<<<<<< HEAD
=======
    public void handleAddFriend(ActionEvent event) {
        String friendUsername = txtAddFriend.getText().trim();
        if (friendUsername.isEmpty()) return;

        JsonObject payload = new JsonObject();
        payload.addProperty("friendUsername", friendUsername);
        ClientApplication.getChatClient().sendPacket(new Packet("ADD_FRIEND_REQUEST", payload.toString()));
    }

    @FXML
>>>>>>> 67bf400d8ef98f36308a989e33fbbb4dfc6f2a3e
    public void handleCreateGroup(ActionEvent event) {
        List<String> friendUsernames = allFriendItems.stream()
                .map(this::extractUsername)
                .filter(username -> username != null && !username.isBlank())
                .toList();

        if (friendUsernames.size() < 2) {
            showAlert("Thông báo", "Bạn cần có ít nhất 2 bạn bè để tạo nhóm.", Alert.AlertType.WARNING);
            return;
        }

        TextInputDialog nameDialog = new TextInputDialog();
        nameDialog.setTitle("Tạo nhóm");
        nameDialog.setHeaderText("Đặt tên nhóm");
        nameDialog.setContentText("Tên nhóm:");

        Optional<String> groupNameResult = nameDialog.showAndWait();
        if (groupNameResult.isEmpty()) {
            return;
        }

        String groupName = groupNameResult.get().trim();
        if (groupName.isEmpty()) {
            showAlert("Thông báo", "Tên nhóm không được để trống.", Alert.AlertType.WARNING);
            return;
        }

        Optional<List<String>> selectedMembers = showGroupMemberDialog(friendUsernames);
        if (selectedMembers.isEmpty()) {
            return;
        }

        List<String> memberUsernames = selectedMembers.get();
        if (memberUsernames.size() < 2) {
            showAlert("Thông báo", "Vui lòng chọn ít nhất 2 bạn bè để tạo nhóm.", Alert.AlertType.WARNING);
            return;
        }

        JsonArray members = new JsonArray();
        memberUsernames.forEach(members::add);

        JsonObject payload = new JsonObject();
        payload.addProperty("groupName", groupName);
        payload.add("members", members);
        ClientApplication.getChatClient().sendPacket(new Packet("CREATE_GROUP_REQUEST", payload.toString()));
    }

    private Optional<List<String>> showGroupMemberDialog(List<String> friendUsernames) {
        Dialog<List<String>> dialog = new Dialog<>();
        dialog.setTitle("Tạo nhóm");
        dialog.setHeaderText("Chọn bạn bè để thêm vào nhóm");

        ButtonType createButtonType = new ButtonType("Tạo nhóm", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(createButtonType, ButtonType.CANCEL);

        Label hint = new Label("Chọn ít nhất 2 bạn bè. Bạn sẽ được tự động thêm vào nhóm.");
        ListView<CheckBox> friendList = new ListView<>();
        friendList.setPrefHeight(320);
        friendList.setPrefWidth(360);

        for (String username : friendUsernames) {
            CheckBox checkBox = new CheckBox(username);
            checkBox.setMaxWidth(Double.MAX_VALUE);
            friendList.getItems().add(checkBox);
        }

        Node createButton = dialog.getDialogPane().lookupButton(createButtonType);
        createButton.setDisable(true);

        Runnable updateCreateButtonState = () -> createButton.setDisable(
                friendList.getItems().stream().filter(CheckBox::isSelected).count() < 2
        );
        friendList.getItems().forEach(checkBox -> checkBox.selectedProperty().addListener(
                (obs, wasSelected, isSelected) -> updateCreateButtonState.run()
        ));

        VBox content = new VBox(10, hint, friendList);
        dialog.getDialogPane().setContent(content);
        dialog.setResultConverter(button -> {
            if (button != createButtonType) {
                return null;
            }
            return friendList.getItems().stream()
                    .filter(CheckBox::isSelected)
                    .map(CheckBox::getText)
                    .toList();
        });

        return dialog.showAndWait();
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
    public void handleBlockUser(ActionEvent event) {
        String selectedUser = getActivePrivateTarget();
        if (selectedUser != null) {
            JsonObject payload = new JsonObject();
            payload.addProperty("targetUsername", selectedUser);
            ClientApplication.getChatClient().sendPacket(new Packet("BLOCK_USER_REQUEST", payload.toString()));
            isBlockedByMeMap.put(selectedUser, !isBlockedByMeMap.getOrDefault(selectedUser, false));
            updateControlButtons(selectedUser);
        } else {
            showAlert("Thông báo", "Vui lòng chọn một người bạn để chặn.", Alert.AlertType.WARNING);
        }
    }

    @FXML
    public void handleMuteUser(ActionEvent event) {
        String selectedUser = getActivePrivateTarget();
        if (selectedUser != null) {
            JsonObject payload = new JsonObject();
            payload.addProperty("targetUsername", selectedUser);
            ClientApplication.getChatClient().sendPacket(new Packet("MUTE_USER_REQUEST", payload.toString()));
            isMutedByMeMap.put(selectedUser, !isMutedByMeMap.getOrDefault(selectedUser, false));
            updateControlButtons(selectedUser);
        } else {
            showAlert("Thông báo", "Vui lòng chọn một người bạn để tắt thông báo.", Alert.AlertType.WARNING);
        }
    }

    private void handleChatHistory(String payload) {
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

            boolean isMe = "Bạn".equals(sender);
            listMessages.getItems().add(createMessageNodeByType(id, sender, content, isMe, type, msg.has("reactions") ? msg.getAsJsonArray("reactions") : null, filename));

            // CALL_LOG entries không cần track trong messageIdToIndexMap
            if (!"CALL_LOG".equals(type)) {
                messageIdToIndexMap.put(id, listMessages.getItems().size() - 1);
                messageConversationMap.put(id, privateConversationKey(otherUser));
            }
        }
    }

    private void handleChatAck(String payload) {
        JsonObject json = JsonParser.parseString(payload).getAsJsonObject();
        long messageId = json.get("id").getAsLong();
        String content = json.get("content").getAsString();
        String type = json.has("type") ? json.get("type").getAsString() : "TEXT";
        String filename = json.has("filename") ? json.get("filename").getAsString() : null;
        String receiver = json.has("receiver") ? json.get("receiver").getAsString() : null;

        if (receiver == null) {
            return;
        }

        String conversationKey = privateConversationKey(receiver);
        if (!conversationKey.equals(currentConversationKey)) {
            return;
        }

        listMessages.getItems().add(createMessageNodeByType(messageId, "Bạn", content, true, type, json.has("reactions") ? json.getAsJsonArray("reactions") : null, filename));

        messageIdToIndexMap.put(messageId, listMessages.getItems().size() - 1);
        messageConversationMap.put(messageId, conversationKey);

        // Add to recent conversations
        addToRecentConversations(receiver, conversationKey);
    }

    private void handleIncomingMessage(String payload) {
        JsonObject json = JsonParser.parseString(payload).getAsJsonObject();
        String sender = json.get("sender").getAsString();
        String content = json.get("content").getAsString();
        String type = json.has("type") ? json.get("type").getAsString() : "TEXT";
        String filename = json.has("filename") ? json.get("filename").getAsString() : null;

        String conversationKey = privateConversationKey(sender);
        if (conversationKey.equals(currentConversationKey)) {
            if (json.has("id")) {
                long messageId = json.get("id").getAsLong();
                listMessages.getItems().add(createMessageNodeByType(messageId, sender, content, false, type, json.has("reactions") ? json.getAsJsonArray("reactions") : null, filename));
                messageIdToIndexMap.put(messageId, listMessages.getItems().size() - 1);
                messageConversationMap.put(messageId, conversationKey);
            } else {
                listMessages.getItems().add(createMessageNodeByType(null, sender, content, false, type, null, filename));
            }
        }

        // Add to recent conversations
        addToRecentConversations(sender, conversationKey);

        boolean isMuted = json.has("isMuted") && json.get("isMuted").getAsBoolean();
        if (!isMuted) {
            String notiText;
            if ("IMAGE".equals(type)) {
                notiText = "[Hình ảnh]";
            } else if ("VOICE".equals(type)) {
                notiText = "[Voice]";
            } else if ("FILE".equals(type)) {
                notiText = filename == null || filename.isBlank() ? "[Tệp]" : "[Tệp] " + filename;
            } else {
                notiText = content;
            }
            showPushNotification(sender, notiText);
        }
    }

    private HBox createMessageNodeByType(Long messageId, String sender, String content, boolean isMe, String type, JsonArray reactions, String filename) {
        if ("IMAGE".equals(type)) {
            return createImageMessageNode(messageId, sender, content, isMe, reactions);
        }
        if ("VOICE".equals(type)) {
            return createVoiceMessageNode(messageId, sender, content, isMe, reactions);
        }
        if ("FILE".equals(type)) {
            return createFileMessageNode(messageId, sender, content, filename, isMe, reactions);
        }
        if ("CALL_LOG".equals(type)) {
            return createCallLogNode(content);
        }
        return createTextMessageNode(messageId, sender, content, isMe, reactions);
    }

    // Render call log entry (cuộc gọi đã kết thúc) — hiển thị ở giữa
    private HBox createCallLogNode(String content) {
        HBox container = new HBox();
        container.setAlignment(Pos.CENTER);

        Label label = new Label(content);
        label.setStyle("-fx-font-size: 12px; -fx-text-fill: #64748b; -fx-padding: 6 14; "
                + "-fx-background-color: #f1f5f9; -fx-background-radius: 12;");

        container.getChildren().add(label);
        container.setUserData(new MessageData(null, "CALL_LOG", content, false, ""));
        return container;
    }

    // Helper method to create a modern text message UI node
    private HBox createTextMessageNode(Long messageId, String sender, String content, boolean isMe, JsonArray reactions) {
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
            messageBox.getChildren().add(textFlow); // Don't show sender name for "Me"
        } else {
            text.setFill(Color.web("#172033"));
            textFlow.setStyle("-fx-background-color: #e8edf3; -fx-background-radius: 16px 16px 16px 4px;");
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
            
            // Click to view full size and save
            imageView.setOnMouseClicked(e -> {
                showImagePreviewDialog(image, imageBytes);
            });
            
            // Add a small border radius effect using CSS wrapper
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

        Label reactionLabel = createReactionLabel(messageId, reactions);
        messageBox.getChildren().add(reactionLabel);

        container.setUserData(new MessageData(messageId, "IMAGE", base64Content, isMe, sender));
        container.getChildren().add(messageBox);
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

        Label reactionLabel = createReactionLabel(messageId, reactions);
        messageBox.getChildren().add(reactionLabel);

        container.setUserData(new MessageData(messageId, "VOICE", base64Content, isMe, sender));
        container.getChildren().add(messageBox);
        return container;
    }

    private HBox createFileMessageNode(Long messageId, String sender, String base64Content, String filename, boolean isMe, JsonArray reactions) {
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

        HBox fileBox = new HBox(8, new Label("📎"), fileNameLabel, downloadButton);
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

        Label reactionLabel = createReactionLabel(messageId, reactions);
        messageBox.getChildren().add(reactionLabel);

        container.setUserData(new MessageData(messageId, "FILE", filename == null ? "" : filename, isMe, sender));
        container.getChildren().add(messageBox);
        return container;
    }

    private Label createReactionLabel(Long messageId, JsonArray reactions) {
        Label reactionLabel = new Label(formatReactionSummary(reactions));
        reactionLabel.setStyle("-fx-font-size: 11px; -fx-text-fill: #64748b;");
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
            btnVoice.setText("⏹");
            btnVoice.setStyle("-fx-min-width: 44px; -fx-min-height: 44px; -fx-font-size: 17px; -fx-background-color: #ef4444; -fx-background-radius: 9px; -fx-text-fill: white; -fx-cursor: hand;");
        } else {
            btnVoice.setText("🎤");
            btnVoice.setStyle("-fx-min-width: 44px; -fx-min-height: 44px; -fx-font-size: 17px; -fx-background-color: #edf2f7; -fx-background-radius: 9px; -fx-text-fill: #22314a; -fx-cursor: hand;");
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

    // ── Voice Call UI helpers ──────────────────────────────

    private javafx.stage.Stage incomingCallStage;
    private IncomingCallController incomingCallController;
    private javafx.stage.Stage callViewStage;
    private CallViewController callViewController;

    private void openIncomingCallWindow(CallSession session) {
        try {
            javafx.fxml.FXMLLoader loader = new javafx.fxml.FXMLLoader(getClass().getResource("/fxml/IncomingCall.fxml"));
            javafx.scene.Parent root = loader.load();
            incomingCallController = loader.getController();
            incomingCallController.initData(session);

            incomingCallStage = new javafx.stage.Stage();
            incomingCallStage.setTitle("Cuộc gọi đến");
            incomingCallStage.initStyle(javafx.stage.StageStyle.UTILITY);
            incomingCallStage.setAlwaysOnTop(true);
            incomingCallStage.setResizable(false);
            incomingCallStage.setScene(new javafx.scene.Scene(root));
            incomingCallStage.setOnCloseRequest(e -> {
                // User đóng cửa sổ = reject
                org.example.client.call.RingtonePlayer.getInstance().stop();
                CallManager.getInstance().rejectCall(CallPacketTypes.REASON_USER_REJECT);
            });
            incomingCallStage.show();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void openCallViewWindow(CallSession session) {
        try {
            javafx.fxml.FXMLLoader loader = new javafx.fxml.FXMLLoader(getClass().getResource("/fxml/CallView.fxml"));
            javafx.scene.Parent root = loader.load();
            callViewController = loader.getController();
            callViewController.initData(session);

            callViewStage = new javafx.stage.Stage();
            callViewStage.setTitle("Cuộc gọi - " + session.peerUsername);
            callViewStage.initStyle(javafx.stage.StageStyle.UTILITY);
            callViewStage.setResizable(false);
            callViewStage.setScene(new javafx.scene.Scene(root));
            callViewStage.setOnCloseRequest(e -> {
                CallManager.getInstance().endCall();
            });
            callViewStage.show();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void closeIncomingCallWindow() {
        if (incomingCallController != null) {
            incomingCallController.forceClose();
            incomingCallController = null;
        }
        if (incomingCallStage != null) {
            if (incomingCallStage.isShowing()) incomingCallStage.close();
            incomingCallStage = null;
        }
    }

    private void closeCallViewWindow() {
        if (callViewController != null) {
            callViewController.forceClose();
            callViewController = null;
        }
        if (callViewStage != null) {
            if (callViewStage.isShowing()) callViewStage.close();
            callViewStage = null;
        }
    }
<<<<<<< HEAD

    private void handleUserInfo(String payload) {
        try {
            JsonObject json = JsonParser.parseString(payload).getAsJsonObject();
            String role = json.get("role").getAsString();
            this.currentUserRole = role;
            Platform.runLater(() -> {
                boolean isAdmin = "ADMIN".equals(role);
                if (btnAdmin != null) {
                    btnAdmin.setVisible(isAdmin);
                    btnAdmin.setManaged(isAdmin);
                }
                if (btnNavSearch != null) {
                    btnNavSearch.setVisible(!isAdmin);
                    btnNavSearch.setManaged(!isAdmin);
                }
                if (btnNavChat != null) {
                    btnNavChat.setVisible(!isAdmin);
                    btnNavChat.setManaged(!isAdmin);
                }
                if (btnNavContacts != null) {
                    btnNavContacts.setVisible(!isAdmin);
                    btnNavContacts.setManaged(!isAdmin);
                }
                if (btnNavGroups != null) {
                    btnNavGroups.setVisible(!isAdmin);
                    btnNavGroups.setManaged(!isAdmin);
                }

                if (isAdmin) {
                    handleShowAdminView(null);
                } else {
                    switchView(viewChat);
                }
            });
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void handleAdminUserList(String payload) {
        Platform.runLater(() -> {
            try {
                JsonArray jsonArray = JsonParser.parseString(payload).getAsJsonArray();
                allAdminUsers.clear();
                for (int i = 0; i < jsonArray.size(); i++) {
                    JsonObject jsonUser = jsonArray.get(i).getAsJsonObject();
                    Long id = jsonUser.get("id").getAsLong();
                    String username = jsonUser.get("username").getAsString();
                    String fullName = jsonUser.get("fullName").getAsString();
                    String role = jsonUser.get("role").getAsString();
                    String status = jsonUser.get("status").getAsString();
                    boolean locked = jsonUser.get("locked").getAsBoolean();
                    String lastSeen = jsonUser.get("lastSeen").getAsString();

                    allAdminUsers.add(new AdminUserRow(id, username, fullName, role, status, locked, lastSeen));
                }
                handleAdminSearch(null);
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }

    private void handleForceLogout(String message) {
        Platform.runLater(() -> {
            showAlert("Thông báo", message, Alert.AlertType.WARNING);
            try {
                ClientApplication.getChatClient().disconnect();
                ClientApplication.getChatClient().connect(
                    ClientApplication.getChatClient().getServerHost() != null ? ClientApplication.getChatClient().getServerHost() : "localhost", 
                    8888
                );
                javafx.stage.Stage stage = (javafx.stage.Stage) contentStack.getScene().getWindow();
                javafx.fxml.FXMLLoader loader = new javafx.fxml.FXMLLoader(getClass().getResource("/fxml/Login.fxml"));
                javafx.scene.Parent loginRoot = loader.load();
                stage.setScene(new javafx.scene.Scene(loginRoot, 400, 400));
                stage.centerOnScreen();
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }

    @FXML
    public void handleShowAdminView(ActionEvent event) {
        switchView(viewAdmin);
        ClientApplication.getChatClient().sendPacket(new Packet("ADMIN_GET_USERS", ""));
    }

    @FXML
    public void handleAdminSearch(ActionEvent event) {
        if (txtAdminSearch == null) return;
        String keyword = txtAdminSearch.getText().trim().toLowerCase();
        if (keyword.isEmpty()) {
            tblUsers.setItems(allAdminUsers);
        } else {
            String cleanKeyword = removeAccents(keyword).replace("?", "");
            filteredAdminUsers.setAll(
                allAdminUsers.stream()
                    .filter(u -> {
                        String cleanUsername = removeAccents(u.getUsername().toLowerCase()).replace("?", "");
                        String cleanFullName = removeAccents(u.getFullName().toLowerCase()).replace("?", "");
                        return cleanUsername.contains(cleanKeyword) || cleanFullName.contains(cleanKeyword);
                    })
                    .toList()
            );
            tblUsers.setItems(filteredAdminUsers);
        }
    }

    @FXML
    public void handleAdminOpenCreateForm(ActionEvent event) {
        toggleAdminCreateMode(true);
    }

    private void toggleAdminCreateMode(boolean enable) {
        isAdminCreateMode = enable;
        if (enable) {
            tblUsers.getSelectionModel().clearSelection();
            txtAdminUsername.setText("");
            txtAdminUsername.setEditable(true);
            txtAdminUsername.getStyleClass().remove("app-input-disabled");
            txtAdminFullName.setText("");
            cmbAdminRole.setValue("USER");
            txtAdminNewPassword.clear();
            
            btnAdminSave.setText("Tạo mới");
            btnAdminSave.setPrefWidth(120.0);
            btnAdminToggleLock.setVisible(false);
            btnAdminToggleLock.setManaged(false);
            btnAdminResetPw.setText("Hủy");
            btnAdminResetPw.setPrefWidth(120.0);
        } else {
            txtAdminUsername.setEditable(false);
            if (!txtAdminUsername.getStyleClass().contains("app-input-disabled")) {
                txtAdminUsername.getStyleClass().add("app-input-disabled");
            }
            btnAdminSave.setText("Lưu");
            btnAdminSave.setPrefWidth(70.0);
            btnAdminToggleLock.setVisible(true);
            btnAdminToggleLock.setManaged(true);
            btnAdminResetPw.setText("Đặt lại MK");
            btnAdminResetPw.setPrefWidth(100.0);
            
            AdminUserRow selected = tblUsers.getSelectionModel().getSelectedItem();
            if (selected != null) {
                txtAdminUsername.setText(selected.getUsername());
                txtAdminFullName.setText(selected.getFullName());
                cmbAdminRole.setValue(selected.getRole());
                btnAdminToggleLock.setText(selected.isLocked() ? "Mở khóa" : "Khóa");
            }
        }
    }

    @FXML
    public void handleAdminSaveUser(ActionEvent event) {
        if (isAdminCreateMode) {
            String username = txtAdminUsername.getText().trim();
            String fullName = txtAdminFullName.getText().trim();
            String role = cmbAdminRole.getValue();
            String password = txtAdminNewPassword.getText().trim();

            if (username.isEmpty() || fullName.isEmpty() || password.isEmpty()) {
                showAlert("Lỗi", "Vui lòng điền đầy đủ thông tin: Tài khoản, Họ tên, Mật khẩu!", Alert.AlertType.ERROR);
                return;
            }

            JsonObject payload = new JsonObject();
            payload.addProperty("username", username);
            payload.addProperty("fullName", fullName);
            payload.addProperty("role", role);
            payload.addProperty("password", password);

            ClientApplication.getChatClient().sendPacket(new Packet("ADMIN_CREATE_USER", payload.toString()));
            toggleAdminCreateMode(false);
        } else {
            AdminUserRow selected = tblUsers.getSelectionModel().getSelectedItem();
            if (selected == null) {
                showAlert("Lỗi", "Vui lòng chọn một tài khoản từ danh sách!", Alert.AlertType.WARNING);
                return;
            }

            String fullName = txtAdminFullName.getText().trim();
            String role = cmbAdminRole.getValue();

            if (fullName.isEmpty()) {
                showAlert("Lỗi", "Họ và tên không được để trống!", Alert.AlertType.ERROR);
                return;
            }

            JsonObject payload = new JsonObject();
            payload.addProperty("id", selected.getId());
            payload.addProperty("fullName", fullName);
            payload.addProperty("role", role);

            ClientApplication.getChatClient().sendPacket(new Packet("ADMIN_UPDATE_USER", payload.toString()));
        }
    }

    @FXML
    public void handleAdminToggleLock(ActionEvent event) {
        AdminUserRow selected = tblUsers.getSelectionModel().getSelectedItem();
        if (selected == null) {
            showAlert("Lỗi", "Vui lòng chọn một tài khoản từ danh sách!", Alert.AlertType.WARNING);
            return;
        }

        JsonObject payload = new JsonObject();
        payload.addProperty("id", selected.getId());

        ClientApplication.getChatClient().sendPacket(new Packet("ADMIN_TOGGLE_LOCK", payload.toString()));
    }

    @FXML
    public void handleAdminResetPassword(ActionEvent event) {
        if (isAdminCreateMode) {
            toggleAdminCreateMode(false);
        } else {
            AdminUserRow selected = tblUsers.getSelectionModel().getSelectedItem();
            if (selected == null) {
                showAlert("Lỗi", "Vui lòng chọn một tài khoản từ danh sách!", Alert.AlertType.WARNING);
                return;
            }

            String newPassword = txtAdminNewPassword.getText().trim();
            if (newPassword.isEmpty()) {
                showAlert("Lỗi", "Vui lòng nhập mật khẩu mới vào ô bên trên!", Alert.AlertType.ERROR);
                return;
            }

            JsonObject payload = new JsonObject();
            payload.addProperty("id", selected.getId());
            payload.addProperty("newPassword", newPassword);

            ClientApplication.getChatClient().sendPacket(new Packet("ADMIN_RESET_PASSWORD", payload.toString()));
            txtAdminNewPassword.clear();
        }
    }
=======
>>>>>>> 67bf400d8ef98f36308a989e33fbbb4dfc6f2a3e
}
