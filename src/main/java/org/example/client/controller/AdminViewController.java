package org.example.client.controller;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import org.example.client.ClientApplication;
import org.example.common.network.Packet;

import java.text.Normalizer;
import java.util.regex.Pattern;

public class AdminViewController {

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

    private ChatController parentController;
    private final ObservableList<AdminUserRow> allAdminUsers = FXCollections.observableArrayList();
    private final ObservableList<AdminUserRow> filteredAdminUsers = FXCollections.observableArrayList();
    private boolean isAdminCreateMode = false;

    public void setParentController(ChatController parentController) {
        this.parentController = parentController;
    }

    public void initialize() {
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
    }

    public void requestGetUsers() {
        ClientApplication.getChatClient().sendPacket(new Packet("ADMIN_GET_USERS", ""));
    }

    public void handleAdminUserList(String payload) {
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

    private static String removeAccents(String src) {
        if (src == null) return "";
        String nfdNormalizedString = Normalizer.normalize(src, Normalizer.Form.NFD);
        Pattern pattern = Pattern.compile("\\p{InCombiningDiacriticalMarks}+");
        return pattern.matcher(nfdNormalizedString).replaceAll("").replace('đ', 'd').replace('Đ', 'D');
    }
}
