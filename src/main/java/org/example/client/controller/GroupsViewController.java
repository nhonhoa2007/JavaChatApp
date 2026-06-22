package org.example.client.controller;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import org.example.client.ClientApplication;
import org.example.common.network.Packet;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class GroupsViewController {

    @FXML
    private ListView<String> listGroups;

    private ChatController parentController;
    private final Map<String, Long> groupDisplayToId = new HashMap<>();

    public void setParentController(ChatController parentController) {
        this.parentController = parentController;
    }

    public void initialize() {
        listGroups.getSelectionModel().selectedItemProperty().addListener((obs, oldValue, newValue) -> {
            if (parentController == null || parentController.isSuppressConversationSelection()) {
                return;
            }
            if (newValue != null) {
                Long groupId = groupDisplayToId.get(newValue);
                if (groupId != null) {
                    parentController.startGroupChat(newValue, groupId);
                    Platform.runLater(() -> listGroups.getSelectionModel().clearSelection());
                }
            }
        });
    }

    @FXML
    public void handleCreateGroup(ActionEvent event) {
        if (parentController == null || parentController.getContactsViewController() == null) {
            return;
        }

        List<String> friendUsernames = parentController.getContactsViewController().getAllFriendItems().stream()
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

    public void handleGroupList(String payload) {
        com.google.gson.JsonObject root = com.google.gson.JsonParser.parseString(payload).getAsJsonObject();
        com.google.gson.JsonArray groups = root.getAsJsonArray("groups");
        listGroups.getItems().clear();
        groupDisplayToId.clear();

        for (int i = 0; i < groups.size(); i++) {
            com.google.gson.JsonObject g = groups.get(i).getAsJsonObject();
            String name = g.get("groupName").getAsString();
            Long id = g.get("groupId").getAsLong();
            String displayText = name + " (ID: " + id + ")";
            listGroups.getItems().add(displayText);
            groupDisplayToId.put(displayText, id);
        }
    }


    public Map<String, Long> getGroupDisplayToId() {
        return groupDisplayToId;
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
