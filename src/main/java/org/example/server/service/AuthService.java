package org.example.server.service;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.example.common.model.Friendship;
import org.example.common.model.User;
import org.example.common.network.Packet;
import org.example.server.dao.FriendshipDAO;
import org.example.server.dao.UserDAO;
import org.example.server.network.ClientHandler;
import org.example.server.network.ServerManager;
import org.example.server.util.PasswordUtil;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class AuthService {
    private final UserDAO userDAO = new UserDAO();
    private final FriendshipDAO friendshipDAO = new FriendshipDAO();
    private final ServerManager serverManager;

    public AuthService(ServerManager serverManager) {
        this.serverManager = serverManager;
    }

    public void handleRegister(String payload, ClientHandler client) {
        try {
            JsonObject json = JsonParser.parseString(payload).getAsJsonObject();
            String username = json.get("username").getAsString();
            String plainPassword = json.get("password").getAsString();
            String fullName = json.has("fullName") ? json.get("fullName").getAsString() : username;

            if (userDAO.findByUsername(username) != null) {
                client.sendPacket(new Packet("REGISTER_ERROR", "Username already exists!"));
                return;
            }

            String hashedPassword = PasswordUtil.hashPassword(plainPassword);
            User newUser = new User(username, hashedPassword, fullName);
            boolean isSaved = userDAO.saveUser(newUser);

            if (isSaved) {
                client.sendPacket(new Packet("REGISTER_SUCCESS", "Registration successful!"));
                System.out.println("New account created: " + username);
            } else {
                client.sendPacket(new Packet("REGISTER_ERROR", "Database error."));
            }
        } catch (Exception e) {
            client.sendPacket(new Packet("REGISTER_ERROR", "Invalid request data."));
            e.printStackTrace();
        }
    }

    public void handleLogin(String payload, ClientHandler client) {
        try {
            JsonObject json = JsonParser.parseString(payload).getAsJsonObject();
            String username = json.get("username").getAsString();
            String plainPassword = json.get("password").getAsString();

            User user = userDAO.findByUsername(username);

            if (user != null && PasswordUtil.checkPassword(plainPassword, user.getPasswordHash())) {
                if (user.isLocked()) {
                    client.sendPacket(new Packet("LOGIN_ERROR", "Tài khoản của bạn đã bị khóa bởi Admin!"));
                    return;
                }
                client.setCurrentUsername(username);
                userDAO.updateUserStatus(username, "ONLINE");

                System.out.println("User logged in successfully: " + username);
                client.sendPacket(new Packet("LOGIN_SUCCESS", "Login successful! Welcome " + user.getFullName()));

                // chỉ gửi cập nhật trạng thái cho bạn bè
                Set<String> friendNames = getFriendUsernames(user);
                serverManager.broadcastOnlineStatus(username, true, friendNames);
            } else {
                client.sendPacket(new Packet("LOGIN_ERROR", "Incorrect username or password!"));
            }
        } catch (Exception e) {
            client.sendPacket(new Packet("LOGIN_ERROR", "Invalid request data."));
            e.printStackTrace();
        }
    }

    public void handleLogout(ClientHandler client) {
        String username = client.getCurrentUsername();
        if (username != null) {
            // gửi trạng thái offline cho bạn bè trước khi xóa client
            try {
                User user = userDAO.findByUsername(username);
                if (user != null) {
                    Set<String> friendNames = getFriendUsernames(user);
                    serverManager.broadcastOnlineStatus(username, false, friendNames);
                } else {
                    serverManager.broadcastOnlineStatus(username, false);
                }
            } catch (Exception e) {
                // gửi toàn bộ nếu không lấy được danh sách bạn bè
                serverManager.broadcastOnlineStatus(username, false);
            }
            userDAO.updateUserStatus(username, "OFFLINE");
            serverManager.removeClient(client);
            client.setCurrentUsername(null);
            System.out.println("User logged out: " + username);
        }
    }

    // lấy tập username bạn bè đã chấp nhận
    // dùng để gửi trạng thái cho đúng người cần biết
    private Set<String> getFriendUsernames(User user) {
        List<Friendship> friendships = friendshipDAO.getAcceptedFriends(user);
        if (friendships == null || friendships.isEmpty()) return Set.of();
        Set<String> names = new HashSet<>();
        for (Friendship f : friendships) {
            String friendName = f.getUser().getUsername().equals(user.getUsername())
                    ? f.getFriend().getUsername()
                    : f.getUser().getUsername();
            names.add(friendName);
        }
        return names;
    }

    public void handleGetUserInfo(ClientHandler client) {
        String username = client.getCurrentUsername();
        if (username != null) {
            User user = userDAO.findByUsername(username);
            if (user != null) {
                JsonObject userInfo = new JsonObject();
                userInfo.addProperty("username", user.getUsername());
                userInfo.addProperty("fullName", user.getFullName());
                userInfo.addProperty("avatar", user.getAvatar() != null ? user.getAvatar() : "");
                userInfo.addProperty("role", user.getRole());
                client.sendPacket(new Packet("USER_INFO", userInfo.toString()));
            }
        }
    }

    public void handleUpdateProfile(String payload, ClientHandler client) {
        String username = client.getCurrentUsername();
        if (username != null) {
            try {
                JsonObject json = JsonParser.parseString(payload).getAsJsonObject();
                String fullName = json.has("fullName") ? json.get("fullName").getAsString().trim() : null;
                String avatar = json.has("avatar") ? json.get("avatar").getAsString().trim() : null;

                User user = userDAO.findByUsername(username);
                if (user != null) {
                    if (fullName != null && !fullName.isEmpty()) {
                        user.setFullName(fullName);
                    }
                    if (avatar != null) {
                        user.setAvatar(avatar);
                    }
                    boolean success = userDAO.updateUser(user);

                    JsonObject response = new JsonObject();
                    if (success) {
                        response.addProperty("status", "SUCCESS");
                        response.addProperty("fullName", user.getFullName());
                        response.addProperty("avatar", user.getAvatar() != null ? user.getAvatar() : "");
                        response.addProperty("message", "Cập nhật hồ sơ thành công!");
                    } else {
                        response.addProperty("status", "ERROR");
                        response.addProperty("message", "Lỗi khi cập nhật thông tin trong cơ sở dữ liệu.");
                    }
                    client.sendPacket(new Packet("UPDATE_PROFILE_RESPONSE", response.toString()));
                }
            } catch (Exception e) {
                e.printStackTrace();
                JsonObject errorResponse = new JsonObject();
                errorResponse.addProperty("status", "ERROR");
                errorResponse.addProperty("message", "Lỗi xử lý yêu cầu cập nhật hồ sơ: " + e.getMessage());
                client.sendPacket(new Packet("UPDATE_PROFILE_RESPONSE", errorResponse.toString()));
            }
        }
    }
}
