package org.example.server.network;

import org.example.common.network.Packet;
import org.example.server.service.AuthService;
import org.example.server.service.CallService;
import org.example.server.service.ChatService;
import org.example.server.service.FriendService;
import org.example.server.service.MessageService;
import org.example.server.service.AdminService;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

public class ClientHandler implements Runnable {
    private final Socket socket;
    private PrintWriter out;
    private BufferedReader in;
    private String currentUsername;

    private final AuthService authService;
    private final ChatService chatService;
    private final MessageService messageService;
    private final FriendService friendService;
    private final CallService callService;
    private final AdminService adminService;

    public ClientHandler(Socket socket, ServerManager serverManager) {
        this.socket = socket;
        this.authService = new AuthService(serverManager);
        this.chatService = new ChatService(serverManager);
        this.messageService = new MessageService(serverManager);
        this.friendService = new FriendService(serverManager);
        this.callService = serverManager.getCallService();
        this.adminService = new AdminService(serverManager);
    }

    @Override
    public void run() {
        try {
            in = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
            out = new PrintWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8), true);

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
                // để client tự tải bạn bè sau khi chatcontroller sẵn sàng
                break;

            case "GET_USER_INFO":
                authService.handleGetUserInfo(this);
                break;

            case "UPDATE_PROFILE_REQUEST":
                authService.handleUpdateProfile(packet.getPayload(), this);
                break;

            case "LOAD_HISTORY_REQUEST":
                chatService.handleLoadHistory(packet.getPayload(), this);
                break;

            case "GROUP_HISTORY_REQUEST":
                chatService.handleLoadGroupHistory(packet.getPayload(), this);
                break;

            case "PRIVATE_MESSAGE":
                chatService.handlePrivateMessage(packet.getPayload(), this);
                break;

            case "GROUP_MESSAGE":
                chatService.handleGroupMessage(packet.getPayload(), this);
                break;

            case "CREATE_GROUP_REQUEST":
                chatService.handleCreateGroup(packet.getPayload(), this);
                break;

            case "GROUP_LIST_REQUEST":
                chatService.handleLoadGroups(this);
                break;

            case "GROUP_MUTE_REQUEST":
                chatService.handleMuteGroup(packet.getPayload(), this);
                break;

            case "GROUP_LEAVE_REQUEST":
                chatService.handleLeaveGroup(packet.getPayload(), this);
                break;

            case "GROUP_DELETE_REQUEST":
                chatService.handleDeleteGroup(packet.getPayload(), this);
                break;

            case "CONVERSATION_LIST_REQUEST":
                chatService.handleLoadConversations(this);
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

            case "REACTION_SET_REQUEST":
                messageService.handleSetReaction(packet.getPayload(), this);
                break;

            case "REACTION_REMOVE_REQUEST":
                messageService.handleRemoveReaction(packet.getPayload(), this);
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

            case "SEARCH_ALL_USERS_REQUEST":
                friendService.handleSearchAllUsers(this);
                break;

            case "BLOCK_USER_REQUEST":
                friendService.handleBlockUser(packet.getPayload(), this);
                break;

            case "MUTE_USER_REQUEST":
                friendService.handleMuteUser(packet.getPayload(), this);
                break;

            case "CALL_INVITE":
                callService.handleInvite(packet.getPayload(), this);
                break;
            case "CALL_ACCEPT":
                callService.handleAccept(packet.getPayload(), this);
                break;
            case "CALL_ACCEPT_ACK":
                callService.handleAcceptAck(packet.getPayload(), this);
                break;
            case "CALL_REJECT":
                callService.handleReject(packet.getPayload(), this);
                break;
            case "CALL_CANCEL":
                callService.handleCancel(packet.getPayload(), this);
                break;
            case "CALL_END":
                callService.handleEnd(packet.getPayload(), this);
                break;
            case "CALL_BUSY":
                // chuyển tiếp trạng thái bận của client
                callService.handleReject(packet.getPayload(), this);
                break;

            case "ADMIN_GET_USERS":
                adminService.handleGetUsers(this);
                break;
            case "ADMIN_CREATE_USER":
                adminService.handleCreateUser(packet.getPayload(), this);
                break;
            case "ADMIN_UPDATE_USER":
                adminService.handleUpdateUser(packet.getPayload(), this);
                break;
            case "ADMIN_RESET_PASSWORD":
                adminService.handleResetPassword(packet.getPayload(), this);
                break;
            case "ADMIN_TOGGLE_LOCK":
                adminService.handleToggleLock(packet.getPayload(), this);
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
        // dọn cuộc gọi đang diễn ra trước khi logout
        if (currentUsername != null) {
            callService.handleClientDisconnect(currentUsername);
        }
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
