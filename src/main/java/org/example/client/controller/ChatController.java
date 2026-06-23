package org.example.client.controller;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;
import javafx.scene.control.*;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.util.Duration;
import org.controlsfx.control.Notifications;
import org.example.client.ClientApplication;
import org.example.client.call.CallEventListener;
import org.example.client.call.CallManager;
import org.example.client.call.CallSession;
import org.example.client.call.RingtonePlayer;
import org.example.client.util.IconUtil;
import org.example.common.network.CallPacketTypes;
import org.example.common.network.Packet;

import java.util.HashMap;
import java.util.Map;

public class ChatController {

    @FXML
    private Button btnNavSearch;

    @FXML
    private Button btnNavChat;

    @FXML
    private Button btnNavContacts;

    @FXML
    private Button btnNavGroups;

    @FXML
    private Button btnNavProfile;

    @FXML
    private Button btnNavLogout;

    @FXML
    private Button btnAdmin;

    @FXML
    private StackPane contentStack;

    // node gốc của các view fxml được include
    @FXML
    private VBox viewSearch;

    @FXML
    private VBox viewChat;

    @FXML
    private VBox viewContacts;

    @FXML
    private VBox viewGroups;

    @FXML
    private VBox viewProfile;

    @FXML
    private VBox viewAdmin;

    // controller được inject từ các view con
    @FXML
    private SearchViewController viewSearchController;

    @FXML
    private ChatWorkspaceController viewChatController;

    @FXML
    private ContactsViewController viewContactsController;

    @FXML
    private GroupsViewController viewGroupsController;

    @FXML
    private ProfileViewController viewProfileController;

    @FXML
    private AdminViewController viewAdminController;

    private String currentUsername;
    private String currentUserRole = "USER";
    private boolean isFirstUserInfo = true;
    private boolean suppressConversationSelection = false;

    // map trạng thái dùng chung với controller con
    private final Map<String, Boolean> isBlockedByMeMap = new HashMap<>();
    private final Map<String, Boolean> isMutedByMeMap = new HashMap<>();

    // trạng thái cửa sổ cuộc gọi
    private javafx.stage.Stage incomingCallStage;
    private IncomingCallController incomingCallController;
    private javafx.stage.Stage callViewStage;
    private CallViewController callViewController;

    public void initData(String username) {
        this.currentUsername = username;
        
        // truyền dependency cho controller con
        if (viewSearchController != null) {
            viewSearchController.setParentController(this);
        }
        if (viewChatController != null) {
            viewChatController.setParentController(this);
            viewChatController.init(username);
        }
        if (viewContactsController != null) {
            viewContactsController.setParentController(this);
        }
        if (viewGroupsController != null) {
            viewGroupsController.setParentController(this);
        }
        if (viewProfileController != null) {
            viewProfileController.setParentController(this);
        }
        if (viewAdminController != null) {
            viewAdminController.setParentController(this);
        }
    }

    public void initialize() {
        configureNavIcons();

        ClientApplication.getChatClient().setOnPacketReceived(this::handleServerResponse);

        CallManager.init(ClientApplication.getChatClient());
        ClientApplication.getChatClient().addListener(CallManager.getInstance()::handlePacket);

        // lắng nghe sự kiện cuộc gọi
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
                    closeIncomingCallWindow();
                    if (callViewStage == null || !callViewStage.isShowing()) {
                        openCallViewWindow(session);
                    }
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

        // mở tab chat mặc định
        switchView(viewChat);

        // gửi yêu cầu tải dữ liệu ban đầu
        ClientApplication.getChatClient().sendPacket(new Packet("LOAD_FRIENDS_REQUEST", ""));
        ClientApplication.getChatClient().sendPacket(new Packet("GROUP_LIST_REQUEST", ""));
        ClientApplication.getChatClient().sendPacket(new Packet("GET_USER_INFO", ""));
        
        if (viewChatController != null) {
            viewChatController.requestConversationList();
        }
    }

    private void configureNavIcons() {
        setNavIcon(btnNavSearch, "/icon/search.svg", "nav-icon", 1.0);
        setNavIcon(btnNavChat, "/icon/chat.svg", "nav-icon", 1.0);
        setNavIcon(btnNavContacts, "/icon/contacts.svg", "nav-icon", 1.0);
        setNavIcon(btnNavGroups, "/icon/groups.svg", "nav-icon", 1.45);
        setNavIcon(btnNavProfile, "/icon/profile.svg", "nav-icon", 1.0);
        setNavIcon(btnAdmin, "/icon/tools.svg", "nav-icon", 1.0);
        setNavIcon(btnNavLogout, "/icon/logout.svg", "nav-danger-icon", 1.0);
    }

    private void setNavIcon(Button button, String resourcePath, String styleClass, double scale) {
        if (button == null) return;
        button.setText(null);
        button.setGraphic(IconUtil.createIcon(resourcePath, styleClass, scale));
        button.setContentDisplay(ContentDisplay.GRAPHIC_ONLY);
    }

    private void handleServerResponse(Packet packet) {
        Platform.runLater(() -> {
            switch (packet.getType()) {
                case "STATUS_UPDATE":
                    handleStatusUpdate(packet.getPayload());
                    break;
                case "LOAD_FRIENDS_SUCCESS":
                    if (viewContactsController != null) {
                        viewContactsController.handleLoadFriendsSuccess(packet.getPayload());
                    }
                    if (viewChatController != null) {
                        viewChatController.refreshConversationsFromLocalState();
                        if (viewChatController.recentConversationsEmpty()) {
                            viewChatController.requestConversationList();
                        }
                    }
                    break;
                case "SEARCH_ALL_USERS_SUCCESS":
                    if (viewSearchController != null) {
                        viewSearchController.handleSearchAllUsersSuccess(packet.getPayload());
                    }
                    break;
                case "GROUP_LIST":
                    if (viewGroupsController != null) {
                        viewGroupsController.handleGroupList(packet.getPayload());
                    }
                    if (viewChatController != null) {
                        viewChatController.refreshConversationsFromLocalState();
                        viewChatController.refreshActiveGroupControls();
                    }
                    break;
                case "CONVERSATION_LIST":
                    if (viewChatController != null) {
                        viewChatController.handleConversationList(packet.getPayload());
                    }
                    break;
                case "GROUP_LIST_UPDATED":
                    ClientApplication.getChatClient().sendPacket(new Packet("GROUP_LIST_REQUEST", ""));
                    if (viewChatController != null) {
                        viewChatController.requestConversationList();
                    }
                    break;
                case "GROUP_REMOVED":
                    if (viewChatController != null) {
                        viewChatController.handleGroupRemoved(packet.getPayload());
                        viewChatController.requestConversationList();
                    }
                    ClientApplication.getChatClient().sendPacket(new Packet("GROUP_LIST_REQUEST", ""));
                    break;
                case "GROUP_SUCCESS":
                    showAlert("Thông báo", packet.getPayload(), Alert.AlertType.INFORMATION);
                    ClientApplication.getChatClient().sendPacket(new Packet("GROUP_LIST_REQUEST", ""));
                    break;
                case "GROUP_ERROR":
                    showAlert("Lỗi nhóm", packet.getPayload(), Alert.AlertType.ERROR);
                    break;
                case "FRIEND_REQUEST":
                    if (viewContactsController != null) {
                        viewContactsController.handleNewFriendRequest(packet.getPayload());
                    }
                    break;
                case "FRIEND_SUCCESS":
                    showAlert("Thông báo", packet.getPayload(), Alert.AlertType.INFORMATION);
                    if (viewSearchController != null) {
                        viewSearchController.requestSearchAllUsers();
                    }
                    break;
                case "FRIEND_ERROR":
                    showAlert("Lỗi Kết Bạn", packet.getPayload(), Alert.AlertType.ERROR);
                    break;
                case "RELOAD_FRIENDS":
                    ClientApplication.getChatClient().sendPacket(new Packet("LOAD_FRIENDS_REQUEST", ""));
                    if (viewSearchController != null) {
                        viewSearchController.requestSearchAllUsers();
                    }
                    break;
                case "BLOCK_SUCCESS":
                case "MUTE_SUCCESS":
                    showAlert("Thành công", packet.getPayload(), Alert.AlertType.INFORMATION);
                    break;
                case "CHAT_HISTORY":
                    if (viewChatController != null) {
                        viewChatController.handleChatHistory(packet.getPayload());
                    }
                    break;
                case "GROUP_HISTORY":
                    if (viewChatController != null) {
                        viewChatController.handleGroupHistory(packet.getPayload());
                    }
                    break;
                case "CHAT_ACK":
                    if (viewChatController != null) {
                        viewChatController.handleChatAck(packet.getPayload());
                    }
                    break;
                case "GROUP_MESSAGE_ACK":
                    if (viewChatController != null) {
                        viewChatController.handleGroupMessageAck(packet.getPayload());
                    }
                    break;
                case "CHAT_MESSAGE":
                    if (viewChatController != null) {
                        viewChatController.handleIncomingMessage(packet.getPayload());
                    }
                    break;
                case "GROUP_MESSAGE":
                    if (viewChatController != null) {
                        viewChatController.handleIncomingGroupMessage(packet.getPayload());
                    }
                    break;
                case "MESSAGE_RECALLED":
                    if (viewChatController != null) {
                        viewChatController.handleMessageRecalled(packet.getPayload());
                    }
                    break;
                case "MESSAGE_EDITED":
                    if (viewChatController != null) {
                        viewChatController.handleMessageEdited(packet.getPayload());
                    }
                    break;
                case "REACTION_UPDATED":
                    if (viewChatController != null) {
                        viewChatController.handleReactionUpdated(packet.getPayload());
                    }
                    break;
                case "USER_INFO":
                    handleUserInfo(packet.getPayload());
                    break;
                case "UPDATE_PROFILE_RESPONSE":
                    handleUpdateProfileResponse(packet.getPayload());
                    break;
                case "ADMIN_USER_LIST":
                    if (viewAdminController != null) {
                        viewAdminController.handleAdminUserList(packet.getPayload());
                    }
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
                case "CHAT_ERROR":
                    showAlert("Lỗi Gửi Tin", packet.getPayload(), Alert.AlertType.ERROR);
                    break;
            }
        });
    }

    private void handleUpdateProfileResponse(String payload) {
        try {
            JsonObject json = JsonParser.parseString(payload).getAsJsonObject();
            String status = json.get("status").getAsString();
            String message = json.get("message").getAsString();
            String fullName = json.has("fullName") ? json.get("fullName").getAsString() : "";
            String avatar = json.has("avatar") ? json.get("avatar").getAsString() : "";

            if (viewProfileController != null) {
                viewProfileController.handleUpdateResponse(status, message, fullName, avatar);
            }

            if ("SUCCESS".equals(status)) {
                showAlert("Thông báo", message, Alert.AlertType.INFORMATION);
            } else {
                showAlert("Lỗi", message, Alert.AlertType.ERROR);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void handleStatusUpdate(String payload) {
        String[] parts = payload.split(":");
        if (parts.length == 2) {
            String user = parts[0];
            String status = parts[1];
            if (viewContactsController != null) {
                viewContactsController.handleStatusUpdate(user, status);
            }
        }
    }

    private void handleUserInfo(String payload) {
        try {
            JsonObject json = JsonParser.parseString(payload).getAsJsonObject();
            String role = json.get("role").getAsString();
            String username = json.has("username") ? json.get("username").getAsString() : "";
            String fullName = json.has("fullName") ? json.get("fullName").getAsString() : "";
            String avatar = json.has("avatar") ? json.get("avatar").getAsString() : "";

            this.currentUserRole = role;

            if (viewProfileController != null) {
                viewProfileController.loadUserData(username, fullName, avatar);
            }

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
                if (isFirstUserInfo) {
                    isFirstUserInfo = false;
                    if (isAdmin) {
                        handleShowAdminView(null);
                    } else {
                        switchView(viewChat);
                    }
                }
            });
        } catch (Exception e) {
            e.printStackTrace();
        }
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
                Parent loginRoot = loader.load();
                stage.setScene(new javafx.scene.Scene(loginRoot, 400, 400));
                stage.centerOnScreen();
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }

    // xử lý điều hướng sidebar
    @FXML
    public void handleShowSearchView(ActionEvent event) {
        switchView(viewSearch);
        if (viewSearchController != null) {
            viewSearchController.requestSearchAllUsers();
        }
    }

    @FXML
    public void handleShowChatView(ActionEvent event) {
        switchView(viewChat);
        if (viewChatController != null) {
            viewChatController.switchToRecentConversations();
            viewChatController.requestConversationList();
        }
    }

    @FXML
    public void handleShowContactsView(ActionEvent event) {
        switchView(viewContacts);
    }

    @FXML
    public void handleShowGroupsView(ActionEvent event) {
        switchView(viewGroups);
    }

    @FXML
    public void handleShowProfileView(ActionEvent event) {
        switchView(viewProfile);
        // tải lại thông tin hồ sơ từ server
        ClientApplication.getChatClient().sendPacket(new Packet("GET_USER_INFO", ""));
    }

    @FXML
    public void handleLogout(ActionEvent event) {
        try {
            // reset trạng thái phiên hiện tại
            isFirstUserInfo = true;

            // gửi yêu cầu logout lên server
            ClientApplication.getChatClient().sendPacket(new Packet("LOGOUT_REQUEST", ""));

            // ngắt socket cũ
            ClientApplication.getChatClient().disconnect();

            // kết nối lại cho lần đăng nhập tiếp theo
            boolean reconnected = ClientApplication.getChatClient().connect("localhost", 8888);
            if (!reconnected) {
                System.err.println("Không thể kết nối lại tới Server sau khi đăng xuất.");
            }

            // chuyển ngay về màn hình đăng nhập
            Stage stage = (Stage) btnNavLogout.getScene().getWindow();
            Parent loginRoot = FXMLLoader.load(getClass().getResource("/fxml/Login.fxml"));
            stage.setScene(new Scene(loginRoot, 400, 400));
            stage.centerOnScreen();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @FXML
    public void handleShowAdminView(ActionEvent event) {
        switchView(viewAdmin);
        if (viewAdminController != null) {
            viewAdminController.requestGetUsers();
        }
    }

    public void startPrivateChat(String username) {
        switchView(viewChat);
        if (viewChatController != null) {
            viewChatController.startPrivateChat(username);
        }
    }

    public void startGroupChat(String groupDisplay, Long groupId) {
        switchView(viewChat);
        if (viewChatController != null) {
            viewChatController.startGroupChat(groupDisplay, groupId);
        }
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
        if (viewProfile != null) {
            viewProfile.setVisible(viewProfile == activeView);
            viewProfile.setManaged(viewProfile == activeView);
        }
        if (viewAdmin != null) {
            viewAdmin.setVisible(viewAdmin == activeView);
            viewAdmin.setManaged(viewAdmin == activeView);
        }
    }

    // hàm hỗ trợ giao diện cuộc gọi
    private void openIncomingCallWindow(CallSession session) {
        try {
            javafx.fxml.FXMLLoader loader = new javafx.fxml.FXMLLoader(getClass().getResource("/fxml/IncomingCall.fxml"));
            Parent root = loader.load();
            incomingCallController = loader.getController();
            incomingCallController.initData(session);

            incomingCallStage = new javafx.stage.Stage();
            incomingCallStage.setTitle("Cuộc gọi đến");
            incomingCallStage.initStyle(javafx.stage.StageStyle.UTILITY);
            incomingCallStage.setAlwaysOnTop(true);
            incomingCallStage.setResizable(false);
            incomingCallStage.setScene(new javafx.scene.Scene(root));
            incomingCallStage.setOnCloseRequest(e -> {
                RingtonePlayer.getInstance().stop();
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
            Parent root = loader.load();
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

    // tiện ích dùng chung cho controller con
    public void showAlert(String title, String content, Alert.AlertType type) {
        Alert alert = new Alert(type);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(content);
        alert.showAndWait();
    }

    public void showPushNotification(String title, String text) {
        Notifications.create()
                .title("Tin nhắn mới từ " + title)
                .text(text)
                .hideAfter(Duration.seconds(5))
                .showInformation();
    }

    public ContactsViewController getContactsViewController() {
        return viewContactsController;
    }

    public GroupsViewController getGroupsViewController() {
        return viewGroupsController;
    }

    public SearchViewController getSearchViewController() {
        return viewSearchController;
    }

    public AdminViewController getAdminViewController() {
        return viewAdminController;
    }

    public ChatWorkspaceController getChatWorkspaceViewController() {
        return viewChatController;
    }

    public Map<String, Boolean> getIsBlockedByMeMap() {
        return isBlockedByMeMap;
    }

    public Map<String, Boolean> getIsMutedByMeMap() {
        return isMutedByMeMap;
    }

    public boolean isSuppressConversationSelection() {
        return suppressConversationSelection;
    }
}
