package org.example.server.service;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.example.common.model.User;
import org.example.common.network.Packet;
import org.example.server.dao.UserDAO;
import org.example.server.network.ClientHandler;
import org.example.server.network.ServerManager;
import org.example.server.util.PasswordUtil;

public class AuthService {
    private final UserDAO userDAO = new UserDAO();
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
                client.setCurrentUsername(username);
                userDAO.updateUserStatus(username, "ONLINE");

                System.out.println("User logged in successfully: " + username);
                client.sendPacket(new Packet("LOGIN_SUCCESS", "Login successful! Welcome " + user.getFullName()));
                
                serverManager.broadcastOnlineStatus(username, true);
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
            serverManager.broadcastOnlineStatus(username, false);
            userDAO.updateUserStatus(username, "OFFLINE");
            serverManager.removeClient(client);
            client.setCurrentUsername(null);
            System.out.println("User logged out: " + username);
        }
    }
}
