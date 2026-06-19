package org.example.server.service;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.example.common.model.User;
import org.example.common.network.Packet;
import org.example.server.dao.UserDAO;
import org.example.server.network.ClientHandler;
import org.example.server.network.ServerManager;
import org.example.server.util.PasswordUtil;

import java.util.List;

public class AdminService {
    private final UserDAO userDAO = new UserDAO();
    private final ServerManager serverManager;
    private final Gson gson = new Gson();

    public AdminService(ServerManager serverManager) {
        this.serverManager = serverManager;
    }

    public void handleGetUsers(ClientHandler client) {
        String currentUsername = client.getCurrentUsername();
        if (currentUsername == null) {
            client.sendPacket(new Packet("ADMIN_ERROR", "Chưa đăng nhập!"));
            return;
        }
        User currentUser = userDAO.findByUsername(currentUsername);
        if (currentUser == null || !"ADMIN".equals(currentUser.getRole())) {
            client.sendPacket(new Packet("ADMIN_ERROR", "Từ chối truy cập! Yêu cầu quyền Admin."));
            return;
        }

        try {
            List<User> users = userDAO.findAllUsers();
            JsonArray jsonArray = new JsonArray();
            for (User u : users) {
                JsonObject jsonUser = new JsonObject();
                jsonUser.addProperty("id", u.getId());
                jsonUser.addProperty("username", u.getUsername());
                jsonUser.addProperty("fullName", u.getFullName());
                jsonUser.addProperty("role", u.getRole());
                jsonUser.addProperty("status", u.getStatus());
                jsonUser.addProperty("locked", u.isLocked());
                jsonUser.addProperty("lastSeen", u.getLastSeen() != null ? u.getLastSeen().toString() : "");
                jsonArray.add(jsonUser);
            }
            client.sendPacket(new Packet("ADMIN_USER_LIST", jsonArray.toString()));
        } catch (Exception e) {
            e.printStackTrace();
            client.sendPacket(new Packet("ADMIN_ERROR", "Lấy danh sách thất bại: " + e.getMessage()));
        }
    }

    public void handleCreateUser(String payload, ClientHandler client) {
        String currentUsername = client.getCurrentUsername();
        User currentUser = userDAO.findByUsername(currentUsername);
        if (currentUser == null || !"ADMIN".equals(currentUser.getRole())) {
            client.sendPacket(new Packet("ADMIN_ERROR", "Từ chối truy cập!"));
            return;
        }

        try {
            JsonObject json = JsonParser.parseString(payload).getAsJsonObject();
            String username = json.get("username").getAsString().trim();
            String password = json.get("password").getAsString();
            String fullName = json.get("fullName").getAsString().trim();
            String role = json.get("role").getAsString();

            if (userDAO.findByUsername(username) != null) {
                client.sendPacket(new Packet("ADMIN_ERROR", "Tên tài khoản đã tồn tại!"));
                return;
            }

            String hashedPassword = PasswordUtil.hashPassword(password);
            User newUser = new User(username, hashedPassword, fullName);
            newUser.setRole(role);
            newUser.setLocked(false);

            if (userDAO.saveUser(newUser)) {
                client.sendPacket(new Packet("ADMIN_SUCCESS", "Tạo tài khoản thành công!"));
                handleGetUsers(client);
            } else {
                client.sendPacket(new Packet("ADMIN_ERROR", "Lỗi lưu tài khoản."));
            }
        } catch (Exception e) {
            e.printStackTrace();
            client.sendPacket(new Packet("ADMIN_ERROR", "Dữ liệu không hợp lệ: " + e.getMessage()));
        }
    }

    public void handleUpdateUser(String payload, ClientHandler client) {
        String currentUsername = client.getCurrentUsername();
        User currentUser = userDAO.findByUsername(currentUsername);
        if (currentUser == null || !"ADMIN".equals(currentUser.getRole())) {
            client.sendPacket(new Packet("ADMIN_ERROR", "Từ chối truy cập!"));
            return;
        }

        try {
            JsonObject json = JsonParser.parseString(payload).getAsJsonObject();
            Long id = json.get("id").getAsLong();
            String fullName = json.get("fullName").getAsString().trim();
            String role = json.get("role").getAsString();

            User userToUpdate = null;
            for (User u : userDAO.findAllUsers()) {
                if (u.getId().equals(id)) {
                    userToUpdate = u;
                    break;
                }
            }

            if (userToUpdate == null) {
                client.sendPacket(new Packet("ADMIN_ERROR", "Không tìm thấy tài khoản!"));
                return;
            }

            if ("USER".equals(role) && "ADMIN".equals(userToUpdate.getRole()) && "admin".equals(userToUpdate.getUsername())) {
                client.sendPacket(new Packet("ADMIN_ERROR", "Không thể hạ quyền tài khoản admin hệ thống chính!"));
                return;
            }

            userToUpdate.setFullName(fullName);
            userToUpdate.setRole(role);

            if (userDAO.updateUser(userToUpdate)) {
                client.sendPacket(new Packet("ADMIN_SUCCESS", "Cập nhật tài khoản thành công!"));
                handleGetUsers(client);
            } else {
                client.sendPacket(new Packet("ADMIN_ERROR", "Lỗi cập nhật tài khoản."));
            }
        } catch (Exception e) {
            e.printStackTrace();
            client.sendPacket(new Packet("ADMIN_ERROR", "Dữ liệu không hợp lệ: " + e.getMessage()));
        }
    }

    public void handleResetPassword(String payload, ClientHandler client) {
        String currentUsername = client.getCurrentUsername();
        User currentUser = userDAO.findByUsername(currentUsername);
        if (currentUser == null || !"ADMIN".equals(currentUser.getRole())) {
            client.sendPacket(new Packet("ADMIN_ERROR", "Từ chối truy cập!"));
            return;
        }

        try {
            JsonObject json = JsonParser.parseString(payload).getAsJsonObject();
            Long id = json.get("id").getAsLong();
            String newPassword = json.get("newPassword").getAsString();

            User userToUpdate = null;
            for (User u : userDAO.findAllUsers()) {
                if (u.getId().equals(id)) {
                    userToUpdate = u;
                    break;
                }
            }

            if (userToUpdate == null) {
                client.sendPacket(new Packet("ADMIN_ERROR", "Không tìm thấy tài khoản!"));
                return;
            }

            userToUpdate.setPasswordHash(PasswordUtil.hashPassword(newPassword));

            if (userDAO.updateUser(userToUpdate)) {
                client.sendPacket(new Packet("ADMIN_SUCCESS", "Đặt lại mật khẩu thành công!"));
            } else {
                client.sendPacket(new Packet("ADMIN_ERROR", "Lỗi đổi mật khẩu."));
            }
        } catch (Exception e) {
            e.printStackTrace();
            client.sendPacket(new Packet("ADMIN_ERROR", "Dữ liệu không hợp lệ: " + e.getMessage()));
        }
    }

    public void handleToggleLock(String payload, ClientHandler client) {
        String currentUsername = client.getCurrentUsername();
        User currentUser = userDAO.findByUsername(currentUsername);
        if (currentUser == null || !"ADMIN".equals(currentUser.getRole())) {
            client.sendPacket(new Packet("ADMIN_ERROR", "Từ chối truy cập!"));
            return;
        }

        try {
            JsonObject json = JsonParser.parseString(payload).getAsJsonObject();
            Long id = json.get("id").getAsLong();

            User userToUpdate = null;
            for (User u : userDAO.findAllUsers()) {
                if (u.getId().equals(id)) {
                    userToUpdate = u;
                    break;
                }
            }

            if (userToUpdate == null) {
                client.sendPacket(new Packet("ADMIN_ERROR", "Không tìm thấy tài khoản!"));
                return;
            }

            if ("admin".equals(userToUpdate.getUsername()) && !userToUpdate.isLocked()) {
                client.sendPacket(new Packet("ADMIN_ERROR", "Không thể khóa tài khoản admin hệ thống chính!"));
                return;
            }

            userToUpdate.setLocked(!userToUpdate.isLocked());

            if (userDAO.updateUser(userToUpdate)) {
                String action = userToUpdate.isLocked() ? "Khóa" : "Mở khóa";
                client.sendPacket(new Packet("ADMIN_SUCCESS", action + " tài khoản thành công!"));
                
                if (userToUpdate.isLocked()) {
                    forceDisconnectUser(userToUpdate.getUsername());
                }
                
                handleGetUsers(client);
            } else {
                client.sendPacket(new Packet("ADMIN_ERROR", "Lỗi thay đổi trạng thái khóa."));
            }
        } catch (Exception e) {
            e.printStackTrace();
            client.sendPacket(new Packet("ADMIN_ERROR", "Dữ liệu không hợp lệ: " + e.getMessage()));
        }
    }

    private void forceDisconnectUser(String username) {
        for (ClientHandler ch : serverManager.getActiveClients()) {
            if (username.equals(ch.getCurrentUsername())) {
                try {
                    ch.sendPacket(new Packet("FORCE_LOGOUT", "Tài khoản của bạn đã bị khóa bởi Admin!"));
                } catch (Exception ignored) {}
            }
        }
    }
}
