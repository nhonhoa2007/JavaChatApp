package org.example.client.controller;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.util.Duration;
import org.example.client.ClientApplication;
import org.example.common.network.Packet;

import java.text.Normalizer;
import java.util.List;
import java.util.regex.Pattern;

public class SearchViewController {

    @FXML
    private TextField txtSearchFriend;

    @FXML
    private ListView<SearchUserResult> listSearchResults;

    private ChatController parentController;
    private final ObservableList<SearchUserResult> allSystemUsers = FXCollections.observableArrayList();
    private boolean suppressSelection = false;

    public void setParentController(ChatController parentController) {
        this.parentController = parentController;
    }

    public void initialize() {
        // debounce 200ms khi nhập tìm kiếm
        PauseTransition searchDebounce = new PauseTransition(Duration.millis(200));
        searchDebounce.setOnFinished(e -> applySearchUserFilter());
        txtSearchFriend.textProperty().addListener((obs, oldValue, newValue) -> {
            searchDebounce.stop();
            searchDebounce.playFromStart();
        });

        // cấu hình cell cho danh sách kết quả
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
                    if ("ONLINE".equals(item.getStatus())) {
                        lblStatus.setStyle("-fx-text-fill: #22c55e; -fx-font-size: 14px;");
                    } else {
                        lblStatus.setStyle("-fx-text-fill: #94a3b8; -fx-font-size: 14px;");
                    }

                    lblName.setText(item.getUsername());
                    lblName.setStyle("-fx-font-weight: bold; -fx-font-size: 13px; -fx-text-fill: #1e293b;");

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

        // xử lý chọn item bằng chuột
        listSearchResults.getSelectionModel().selectedItemProperty().addListener((observable, oldValue, newValue) -> {
            if (suppressSelection || newValue == null) {
                return;
            }
            if ("FRIEND".equals(newValue.getRelation())) {
                String selectedUser = newValue.getUsername();
                if (parentController != null) {
                    parentController.startPrivateChat(selectedUser);
                }
            }
            
            Platform.runLater(() -> {
                suppressSelection = true;
                try {
                    listSearchResults.getSelectionModel().clearSelection();
                } finally {
                    suppressSelection = false;
                }
            });
        });
    }

    public void requestSearchAllUsers() {
        ClientApplication.getChatClient().sendPacket(new Packet("SEARCH_ALL_USERS_REQUEST", ""));
    }

    public void handleSearchAllUsersSuccess(String payload) {
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

        applySearchUserFilter();
    }

    private void applySearchUserFilter() {
        String keyword = txtSearchFriend.getText().trim();
        if (keyword.isEmpty()) {
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
}
