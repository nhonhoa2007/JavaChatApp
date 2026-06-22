package org.example.server.service;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.example.common.model.GroupChat;
import org.example.common.model.Message;
import org.example.common.model.MessageReaction;
import org.example.common.model.User;
import org.example.common.network.Packet;
import org.example.server.dao.GroupMemberDAO;
import org.example.server.dao.MessageDAO;
import org.example.server.dao.MessageReactionDAO;
import org.example.server.dao.UserDAO;
import org.example.server.network.ClientHandler;
import org.example.server.network.ServerManager;

import java.time.LocalDateTime;

public class MessageService {
    private final ServerManager serverManager;
    private final MessageDAO messageDAO = new MessageDAO();
    private final UserDAO userDAO = new UserDAO();
    private final GroupMemberDAO groupMemberDAO = new GroupMemberDAO();
    private final MessageReactionDAO messageReactionDAO = new MessageReactionDAO();

    public MessageService(ServerManager serverManager) {
        this.serverManager = serverManager;
    }

    public void handleRecallMessage(String payload, ClientHandler senderClient) {
        try {
            JsonObject json = JsonParser.parseString(payload).getAsJsonObject();
            Long messageId = json.get("messageId").getAsLong();

            Message message = messageDAO.findById(messageId);
            
            // kiểm tra tin nhắn tồn tại và thuộc người gửi
            if (message != null && message.getSender().getUsername().equals(senderClient.getCurrentUsername())) {
                message.setRecalled(true);
                message.setUpdatedAt(LocalDateTime.now());
                messageDAO.updateMessage(message);

                // thông báo người nhận ẩn tin nhắn
                JsonObject recallJson = new JsonObject();
                recallJson.addProperty("messageId", messageId);
                Packet recallPacket = new Packet("MESSAGE_RECALLED", recallJson.toString());

                if (message.getReceiver() != null) {
                    serverManager.sendToClient(message.getReceiver().getUsername(), recallPacket);
                } else if (message.getGroupChat() != null) {
                    broadcastToGroup(message.getGroupChat(), senderClient.getCurrentUsername(), recallPacket);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void handleEditMessage(String payload, ClientHandler senderClient) {
        try {
            JsonObject json = JsonParser.parseString(payload).getAsJsonObject();
            Long messageId = json.get("messageId").getAsLong();
            String newContent = json.get("newContent").getAsString();

            Message message = messageDAO.findById(messageId);

            // kiểm tra tin nhắn hợp lệ để chỉnh sửa
            if (message != null && !message.isRecalled() && message.getSender().getUsername().equals(senderClient.getCurrentUsername())) {
                message.setContent(newContent);
                message.setEdited(true);
                message.setUpdatedAt(LocalDateTime.now());
                messageDAO.updateMessage(message);

                // thông báo người nhận cập nhật giao diện tin nhắn
                JsonObject editJson = new JsonObject();
                editJson.addProperty("messageId", messageId);
                editJson.addProperty("newContent", newContent);
                Packet editPacket = new Packet("MESSAGE_EDITED", editJson.toString());

                if (message.getReceiver() != null) {
                    serverManager.sendToClient(message.getReceiver().getUsername(), editPacket);
                } else if (message.getGroupChat() != null) {
                    broadcastToGroup(message.getGroupChat(), senderClient.getCurrentUsername(), editPacket);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void handleSetReaction(String payload, ClientHandler client) {
        try {
            JsonObject json = JsonParser.parseString(payload).getAsJsonObject();
            long messageId = json.get("messageId").getAsLong();
            String emoji = json.get("emoji").getAsString();

            Message message = messageDAO.findById(messageId);
            User user = userDAO.findByUsername(client.getCurrentUsername());
            if (message == null || user == null || emoji == null || emoji.isBlank()) {
                return;
            }

            if (!canInteractWithMessage(message, user)) {
                return;
            }

            MessageReaction existing = messageReactionDAO.findByMessageAndUser(message, user);
            if (existing == null) {
                existing = new MessageReaction(message, user, emoji);
            } else {
                existing.setEmoji(emoji);
                existing.setReactedAt(LocalDateTime.now());
            }
            messageReactionDAO.saveOrUpdate(existing);
            pushReactionUpdate(message);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void handleRemoveReaction(String payload, ClientHandler client) {
        try {
            JsonObject json = JsonParser.parseString(payload).getAsJsonObject();
            long messageId = json.get("messageId").getAsLong();

            Message message = messageDAO.findById(messageId);
            User user = userDAO.findByUsername(client.getCurrentUsername());
            if (message == null || user == null || !canInteractWithMessage(message, user)) {
                return;
            }

            MessageReaction existing = messageReactionDAO.findByMessageAndUser(message, user);
            if (existing != null) {
                messageReactionDAO.delete(existing);
                pushReactionUpdate(message);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void pushReactionUpdate(Message message) {
        JsonObject event = new JsonObject();
        event.addProperty("messageId", message.getId());
        event.add("reactions", messageReactionDAO.getReactionSummary(message));
        if (message.getGroupChat() != null) {
            event.addProperty("groupId", message.getGroupChat().getId());
        }

        Packet packet = new Packet("REACTION_UPDATED", event.toString());
        if (message.getReceiver() != null) {
            serverManager.sendToClient(message.getSender().getUsername(), packet);
            serverManager.sendToClient(message.getReceiver().getUsername(), packet);
        } else if (message.getGroupChat() != null) {
            for (User member : groupMemberDAO.getMembers(message.getGroupChat())) {
                serverManager.sendToClient(member.getUsername(), packet);
            }
        }
    }

    private boolean canInteractWithMessage(Message message, User user) {
        if (message.getReceiver() != null) {
            return message.getSender().getId().equals(user.getId()) || message.getReceiver().getId().equals(user.getId());
        }
        if (message.getGroupChat() != null) {
            return groupMemberDAO.isMember(message.getGroupChat(), user);
        }
        return false;
    }

    private void broadcastToGroup(GroupChat groupChat, String excludeUsername, Packet packet) {
        for (User member : groupMemberDAO.getMembers(groupChat)) {
            if (!member.getUsername().equals(excludeUsername)) {
                serverManager.sendToClient(member.getUsername(), packet);
            }
        }
    }
}
