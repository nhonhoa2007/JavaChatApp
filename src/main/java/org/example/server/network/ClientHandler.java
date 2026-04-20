package org.example.server.network;

import org.example.common.network.Packet;
import org.example.server.service.AuthService;
import org.example.server.service.ChatService;
import org.example.server.service.FriendService;
import org.example.server.service.MessageService;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;

public class ClientHandler implements Runnable {
    private final Socket socket;
    private PrintWriter out;
    private BufferedReader in;
    private String currentUsername;

    private final AuthService authService;
    private final ChatService chatService;
    private final MessageService messageService;
    private final FriendService friendService;

    public ClientHandler(Socket socket, ServerManager serverManager) {
        this.socket = socket;
        this.authService = new AuthService(serverManager);
        this.chatService = new ChatService(serverManager);
        this.messageService = new MessageService(serverManager);
        this.friendService = new FriendService(serverManager);
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
                authService.handleRegister(packet.getPayload(), this);
                break;
                
            case "LOGIN_REQUEST":
                authService.handleLogin(packet.getPayload(), this);
                // Sau khi login xong, load bạn bè luôn
                friendService.handleLoadFriends(this);
                break;

            case "LOAD_HISTORY_REQUEST":
                chatService.handleLoadHistory(packet.getPayload(), this);
                break;
                
            case "PRIVATE_MESSAGE":
                chatService.handlePrivateMessage(packet.getPayload(), this);
                break;
                
            case "BROADCAST_MESSAGE":
                chatService.handleBroadcastMessage(packet.getPayload(), this);
                break;

            case "RECALL_MESSAGE":
                messageService.handleRecallMessage(packet.getPayload(), this);
                break;

            case "EDIT_MESSAGE":
                messageService.handleEditMessage(packet.getPayload(), this);
                break;

            case "ADD_FRIEND_REQUEST":
                friendService.handleAddFriend(packet.getPayload(), this);
                break;

            case "ACCEPT_FRIEND_REQUEST":
                friendService.handleAcceptFriend(packet.getPayload(), this);
                break;

            case "LOAD_FRIENDS_REQUEST":
                friendService.handleLoadFriends(this);
                break;

            case "BLOCK_USER_REQUEST":
                friendService.handleBlockUser(packet.getPayload(), this);
                break;

            case "MUTE_USER_REQUEST":
                friendService.handleMuteUser(packet.getPayload(), this);
                break;

            case "LOGOUT_REQUEST":
                disconnect();
                break;
                
            default:
                System.out.println("Unknown packet type: " + packet.getType());
        }
    }

    public void sendPacket(Packet packet) {
        if (out != null) {
            out.println(packet.toJson());
        }
    }

    private void disconnect() {
        authService.handleLogout(this);
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

    public void setCurrentUsername(String currentUsername) {
        this.currentUsername = currentUsername;
    }
}
