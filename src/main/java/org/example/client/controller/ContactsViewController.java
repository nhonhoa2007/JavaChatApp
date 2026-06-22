package org.example.client.controller;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import org.example.client.ClientApplication;
import org.example.common.network.Packet;

public class ContactsViewController {

    @FXML
    private ListView<String> listUsers;

    @FXML
    private ListView<String> listRequests;

    private ChatController parentController;
    private final ObservableList<String> allFriendItems = FXCollections.observableArrayList();

    public void setParentController(ChatController parentController) {
        this.parentController = parentController;
    }

    public void initialize() {
        listUsers.getSelectionModel().selectedItemProperty().addListener((observable, oldValue, newValue) -> {
            if (parentController == null || parentController.isSuppressConversationSelection()) {
                return;
            }
            String selectedUser = extractUsername(newValue);
            if (selectedUser != null) {
                parentController.startPrivateChat(selectedUser);
                Platform.runLater(() -> listUsers.getSelectionModel().clearSelection());
            }
        });

        setupRequestContextMenu();
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

    private void handleAcceptFriend() {
        String selectedRequest = listRequests.getSelectionModel().getSelectedItem();
        if (selectedRequest != null) {
            JsonObject payload = new JsonObject();
            payload.addProperty("senderUsername", selectedRequest);
            ClientApplication.getChatClient().sendPacket(new Packet("ACCEPT_FRIEND_REQUEST", payload.toString()));
            listRequests.getItems().remove(selectedRequest);
        }
    }

    public void handleLoadFriendsSuccess(String payload) {
        JsonObject json = JsonParser.parseString(payload).getAsJsonObject();
        JsonArray friends = json.getAsJsonArray("friends");
        JsonArray requests = json.getAsJsonArray("requests");

        allFriendItems.clear();
        if (parentController != null) {
            parentController.getIsBlockedByMeMap().clear();
            parentController.getIsMutedByMeMap().clear();
        }

        for (int i = 0; i < friends.size(); i++) {
            JsonObject f = friends.get(i).getAsJsonObject();
            String username = f.get("username").getAsString();
            String status = f.get("status").getAsString();
            
            if (parentController != null) {
                if (f.has("isBlockedByMe")) {
                    parentController.getIsBlockedByMeMap().put(username, f.get("isBlockedByMe").getAsBoolean());
                }
                if (f.has("isMutedByMe")) {
                    parentController.getIsMutedByMeMap().put(username, f.get("isMutedByMe").getAsBoolean());
                }
            }

            allFriendItems.add(buildFriendDisplayText(username, status));
        }

        applyFriendFilter();

        listRequests.getItems().clear();
        for (int i = 0; i < requests.size(); i++) {
            listRequests.getItems().add(requests.get(i).getAsString());
        }
    }

    public void handleNewFriendRequest(String payload) {
        JsonObject json = JsonParser.parseString(payload).getAsJsonObject();
        String from = json.get("from").getAsString();
        if (!listRequests.getItems().contains(from)) {
            listRequests.getItems().add(from);
        }
        if (parentController != null) {
            parentController.showPushNotification("Hệ thống", from + " đã gửi lời mời kết bạn.");
        }
    }

    public void handleStatusUpdate(String user, String status) {
        // cập nhật trạng thái item
        boolean found = false;
        for (int i = 0; i < allFriendItems.size(); i++) {
            String item = allFriendItems.get(i);
            String actualUsername = extractUsername(item);
            if (user.equals(actualUsername)) {
                allFriendItems.set(i, buildFriendDisplayText(actualUsername, status));
                found = true;
                break;
            }
        }
        if (found) {
            applyFriendFilter();
        }
    }

    public void applyFriendFilter() {
        listUsers.getItems().setAll(allFriendItems);
    }

    public ObservableList<String> getAllFriendItems() {
        return allFriendItems;
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
}
