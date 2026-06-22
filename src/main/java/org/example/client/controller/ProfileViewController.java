package org.example.client.controller;

import com.google.gson.JsonObject;
import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.stage.FileChooser;
import org.example.client.ClientApplication;
import org.example.common.network.Packet;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.nio.file.Files;
import java.util.Base64;

public class ProfileViewController {

    @FXML
    private Label lblUsername;

    @FXML
    private TextField txtFullName;

    @FXML
    private ComboBox<String> cmbAvatar;

    @FXML
    private Label lblAvatarEmoji;

    @FXML
    private ImageView imgAvatar;

    @FXML
    private Label lblStatus;

    private ChatController parentController;
    private String base64Avatar = "";
    private boolean suppressAvatarSelection;

    public void initialize() {
        // nạp danh sách avatar mẫu
        cmbAvatar.getItems().addAll("🐶 Chó", "🐱 Mèo", "🦊 Cáo", "🦁 Sư tử", "🐸 Ếch", "🐼 Gấu trúc", "🐨 Koala");        
        // cập nhật preview khi chọn avatar mẫu
        cmbAvatar.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            if (!suppressAvatarSelection && newVal != null) {
                String emoji = newVal.split(" ")[0];
                lblAvatarEmoji.setText(emoji);
                
                // xóa ảnh tùy chỉnh khi chọn emoji mẫu
                imgAvatar.setVisible(false);
                lblAvatarEmoji.setVisible(true);
                this.base64Avatar = "";
            }
        });
    }

    public void setParentController(ChatController parentController) {
        this.parentController = parentController;
    }

    public void loadUserData(String username, String fullName, String avatar) {
        Platform.runLater(() -> {
            lblUsername.setText(username);
            txtFullName.setText(fullName != null ? fullName : "");
            lblStatus.setText("");
            
            if (avatar != null && !avatar.isEmpty()) {
                // xử lý avatar dạng base64
                if (avatar.length() > 10) {
                    try {
                        byte[] bytes = Base64.getDecoder().decode(avatar);
                        Image img = new Image(new ByteArrayInputStream(bytes));
                        imgAvatar.setImage(img);
                        imgAvatar.setVisible(true);
                        lblAvatarEmoji.setVisible(false);
                        this.base64Avatar = avatar;
                    } catch (Exception e) {
                        e.printStackTrace();
                        // dùng emoji nếu giải mã lỗi
                        imgAvatar.setVisible(false);
                        lblAvatarEmoji.setVisible(true);
                        lblAvatarEmoji.setText("👤");
                    }
                } else {
                    // xử lý avatar dạng emoji
                    imgAvatar.setVisible(false);
                    lblAvatarEmoji.setVisible(true);
                    lblAvatarEmoji.setText(avatar);
                    this.base64Avatar = "";
                    
                    // chọn emoji tương ứng trong combobox
                    for (String item : cmbAvatar.getItems()) {
                        if (item.startsWith(avatar)) {
                            cmbAvatar.getSelectionModel().select(item);
                            break;
                        }
                    }
                }
            } else {
                // dùng emoji mặc định
                imgAvatar.setVisible(false);
                lblAvatarEmoji.setVisible(true);
                lblAvatarEmoji.setText(getInitial(fullName != null && !fullName.isBlank() ? fullName : username));
                suppressAvatarSelection = true;
                try {
                    cmbAvatar.getSelectionModel().clearSelection();
                } finally {
                    suppressAvatarSelection = false;
                }
                this.base64Avatar = "";
            }
        });
    }

    @FXML
    public void handleUploadAvatar(ActionEvent event) {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Chọn hình ảnh đại diện");
        fileChooser.getExtensionFilters().add(
            new FileChooser.ExtensionFilter("Image Files", "*.png", "*.jpg", "*.jpeg", "*.gif")
        );
        
        File selectedFile = fileChooser.showOpenDialog(txtFullName.getScene().getWindow());
        if (selectedFile != null) {
            try {
                // giới hạn ảnh tối đa 2mb
                if (selectedFile.length() > 2 * 1024 * 1024) {
                    lblStatus.setStyle("-fx-text-fill: red;");
                    lblStatus.setText("Lỗi: Vui lòng chọn ảnh đại diện nhỏ hơn 2MB!");
                    return;
                }
                
                byte[] fileContent = Files.readAllBytes(selectedFile.toPath());
                String encodedString = Base64.getEncoder().encodeToString(fileContent);
                
                this.base64Avatar = encodedString;
                
                // hiển thị ảnh preview
                Image img = new Image(new ByteArrayInputStream(fileContent));
                imgAvatar.setImage(img);
                imgAvatar.setVisible(true);
                lblAvatarEmoji.setVisible(false);
                
                lblStatus.setStyle("-fx-text-fill: green;");
                lblStatus.setText("Đã nạp hình ảnh mới. Hãy nhấn 'Lưu thay đổi'.");
            } catch (Exception e) {
                lblStatus.setStyle("-fx-text-fill: red;");
                lblStatus.setText("Lỗi nạp file: " + e.getMessage());
                e.printStackTrace();
            }
        }
    }

    @FXML
    public void handleSaveProfile(ActionEvent event) {
        String fullName = txtFullName.getText().trim();
        String avatarToSend;

        if (fullName.isEmpty()) {
            lblStatus.setStyle("-fx-text-fill: red;");
            lblStatus.setText("Họ và tên không được để trống!");
            return;
        }

        // dùng ảnh base64 nếu đã tải lên
        if (this.base64Avatar != null && !this.base64Avatar.isEmpty()) {
            avatarToSend = this.base64Avatar;
        } else {
            // dùng emoji đã chọn nếu không có ảnh
            String selectedAvatarItem = cmbAvatar.getSelectionModel().getSelectedItem();
            avatarToSend = selectedAvatarItem != null ? selectedAvatarItem.split(" ")[0] : "";
        }

        JsonObject payload = new JsonObject();
        payload.addProperty("fullName", fullName);
        payload.addProperty("avatar", avatarToSend);

        Packet updatePacket = new Packet("UPDATE_PROFILE_REQUEST", payload.toString());
        ClientApplication.getChatClient().sendPacket(updatePacket);
        
        lblStatus.setStyle("-fx-text-fill: blue;");
        lblStatus.setText("Đang gửi yêu cầu lưu...");
    }

    public void handleUpdateResponse(String status, String message, String fullName, String avatar) {
        Platform.runLater(() -> {
            if ("SUCCESS".equals(status)) {
                lblStatus.setStyle("-fx-text-fill: green;");
                lblStatus.setText(message);
                loadUserData(lblUsername.getText(), fullName, avatar);
            } else {
                lblStatus.setStyle("-fx-text-fill: red;");
                lblStatus.setText(message);
            }
        });
    }

    private String getInitial(String value) {
        if (value == null || value.isBlank()) {
            return "?";
        }
        return value.trim().substring(0, 1).toUpperCase();
    }
}
