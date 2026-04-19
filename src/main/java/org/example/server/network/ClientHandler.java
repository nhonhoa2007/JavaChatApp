package org.example.server.network;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.example.common.model.User;
import org.example.common.network.Packet;
import org.example.server.dao.UserDAO;
import org.example.server.util.PasswordUtil;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;

public class ClientHandler implements Runnable {
    private final Socket socket;
    private final ServerManager serverManager;
    private PrintWriter out;
    private BufferedReader in;
    private String currentUsername;

    private final UserDAO userDAO = new UserDAO();

    public ClientHandler(Socket socket, ServerManager serverManager) {
        this.socket = socket;
        this.serverManager = serverManager;
    }

    @Override
    public void run() {
        try {
            in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            out = new PrintWriter(socket.getOutputStream(), true);

            String inputLine;
            while ((inputLine = in.readLine()) != null) {
                Packet packet = Packet.fromJson(inputLine);
                if (packet != null) {
                    handlePacket(packet);
                }
            }
        } catch (Exception e) {
            System.out.println("Client disconnected or error: " + e.getMessage());
        } finally {
            disconnect();
        }
    }

    private void handlePacket(Packet packet) {
        switch (packet.getType()) {
            case "REGISTER_REQUEST":
                handleRegisterRequest(packet.getPayload());
                break;
                
            case "LOGIN_REQUEST":
                handleLoginRequest(packet.getPayload());
                break;
                
            case "LOGOUT_REQUEST":
                disconnect();
                break;
                
            default:
                System.out.println("Unknown packet type: " + packet.getType());
        }
    }

    private void handleRegisterRequest(String payload) {
        try {
            JsonObject json = JsonParser.parseString(payload).getAsJsonObject();
            String username = json.get("username").getAsString();
            String plainPassword = json.get("password").getAsString();
            String fullName = json.has("fullName") ? json.get("fullName").getAsString() : username;

            if (userDAO.findByUsername(username) != null) {
                sendPacket(new Packet("REGISTER_ERROR", "Username already exists!"));
                return;
            }

            String hashedPassword = PasswordUtil.hashPassword(plainPassword);

            User newUser = new User(username, hashedPassword, fullName);
            boolean isSaved = userDAO.saveUser(newUser);

            if (isSaved) {
                sendPacket(new Packet("REGISTER_SUCCESS", "Registration successful!"));
                System.out.println("New account created: " + username);
            } else {
                sendPacket(new Packet("REGISTER_ERROR", "Database error during registration."));
            }

        } catch (Exception e) {
            sendPacket(new Packet("REGISTER_ERROR", "Invalid request data."));
            e.printStackTrace();
        }
    }

    private void handleLoginRequest(String payload) {
        try {
            JsonObject json = JsonParser.parseString(payload).getAsJsonObject();
            String username = json.get("username").getAsString();
            String plainPassword = json.get("password").getAsString();

            User user = userDAO.findByUsername(username);

            if (user != null && PasswordUtil.checkPassword(plainPassword, user.getPasswordHash())) {
                
                this.currentUsername = username;
                System.out.println("User logged in successfully: " + currentUsername);

                userDAO.updateUserStatus(currentUsername, "ONLINE");

                sendPacket(new Packet("LOGIN_SUCCESS", "Login successful! Welcome " + user.getFullName()));
                
                serverManager.broadcastOnlineStatus(currentUsername, true);

            } else {
                sendPacket(new Packet("LOGIN_ERROR", "Incorrect username or password!"));
            }

        } catch (Exception e) {
            sendPacket(new Packet("LOGIN_ERROR", "Invalid request data."));
            e.printStackTrace();
        }
    }

    public void sendPacket(Packet packet) {
        if (out != null) {
            out.println(packet.toJson());
        }
    }

    private void disconnect() {
        if (currentUsername != null) {
            serverManager.broadcastOnlineStatus(currentUsername, false);
            userDAO.updateUserStatus(currentUsername, "OFFLINE");
            serverManager.removeClient(this);
            System.out.println("User logged out: " + currentUsername);
            currentUsername = null;
        }
        try {
            if (socket != null && !socket.isClosed()) {
                socket.close();
            }
        } catch (Exception ignored) {
        }
    }

    public String getCurrentUsername() {
        return currentUsername;
    }
}
