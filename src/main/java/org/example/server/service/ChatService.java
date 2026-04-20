package org.example.server.service;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.example.common.model.Message;
import org.example.common.model.User;
import org.example.common.network.Packet;
import org.example.server.dao.FriendshipDAO;
import org.example.server.dao.MessageDAO;
import org.example.server.dao.UserDAO;
import org.example.server.network.ClientHandler;
import org.example.server.network.ServerManager;

import java.util.List;

public class ChatService {
    private final ServerManager serverManager;
    private final UserDAO userDAO = new UserDAO();
    private final MessageDAO messageDAO = new MessageDAO();
    private final FriendshipDAO friendshipDAO = new FriendshipDAO();

    public ChatService(ServerManager serverManager) {
        this.serverManager = serverManager;
    }

    public void handleLoadHistory(String payload, ClientHandler client) {
        try {
            JsonObject json = JsonParser.parseString(payload).getAsJsonObject();
            String otherUsername = json.get("otherUser").getAsString();
            int limit = json.has("limit") ? json.get("limit").getAsInt() : 50;

            User currentUser = userDAO.findByUsername(client.getCurrentUsername());
            User otherUser = userDAO.findByUsername(otherUsername);

            if (currentUser == null || otherUser == null) return;

            List<Message> history = messageDAO.getPrivateHistory(currentUser, otherUser, limit);
            
            JsonArray messagesArray = new JsonArray();
            if (history != null) {
                for (Message msg : history) {
                    JsonObject msgObj = new JsonObject();
                    msgObj.addProperty("id", msg.getId());
                    msgObj.addProperty("sender", msg.getSender().getUsername().equals(currentUser.getUsername()) ? "Bạn" : msg.getSender().getUsername());
                    
                    if (msg.isRecalled()) {
                        msgObj.addProperty("content", "[Tin nhắn đã bị thu hồi]");
                    } else {
                        String content = msg.getContent();
                        if (msg.isEdited()) {
                            content += " (Đã chỉnh sửa)";
                        }
                        msgObj.addProperty("content", content);
                    }
                    
                    msgObj.addProperty("type", msg.getMessageType());
                    msgObj.addProperty("timestamp", msg.getSentAt().toString());
                    msgObj.addProperty("isRecalled", msg.isRecalled());
                    messagesArray.add(msgObj);
                }
            }

            JsonObject responseJson = new JsonObject();
            responseJson.addProperty("otherUser", otherUsername);
            responseJson.add("messages", messagesArray);

            client.sendPacket(new Packet("CHAT_HISTORY", responseJson.toString()));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void handlePrivateMessage(String payload, ClientHandler senderClient) {
        try {
            JsonObject json = JsonParser.parseString(payload).getAsJsonObject();
            String receiverUsername = json.get("receiver").getAsString();
            String content = json.get("content").getAsString();
            String messageType = json.has("type") ? json.get("type").getAsString() : "TEXT";

            User sender = userDAO.findByUsername(senderClient.getCurrentUsername());
            User receiver = userDAO.findByUsername(receiverUsername);

            if (sender == null || receiver == null) return;

            // Kiểm tra Block (Chặn)
            if (friendshipDAO.isBlocked(sender, receiver)) {
                senderClient.sendPacket(new Packet("CHAT_ERROR", "Bạn đã bị người này chặn, không thể gửi tin nhắn."));
                return;
            }

            // Lưu tin nhắn vào DB
            Message message = new Message(sender, receiver, messageType, content);
            messageDAO.saveMessage(message);

            // Kiểm tra Mute (Tắt thông báo)
            boolean isMuted = friendshipDAO.isMuted(sender, receiver);

            // Gửi lại gói tin cho SENDER để họ có ID thực tế của tin nhắn (phục vụ Edit/Recall)
            JsonObject ackJson = new JsonObject();
            ackJson.addProperty("id", message.getId());
            ackJson.addProperty("sender", "Bạn");
            ackJson.addProperty("content", content);
            ackJson.addProperty("type", messageType);
            ackJson.addProperty("receiver", receiver.getUsername());
            senderClient.sendPacket(new Packet("CHAT_ACK", ackJson.toString()));

            // Tạo payload gửi cho RECEIVER
            JsonObject messageJson = new JsonObject();
            messageJson.addProperty("id", message.getId());
            messageJson.addProperty("sender", sender.getUsername());
            messageJson.addProperty("content", content);
            messageJson.addProperty("type", messageType);
            messageJson.addProperty("timestamp", message.getSentAt().toString());
            messageJson.addProperty("isMuted", isMuted);

            Packet messagePacket = new Packet("CHAT_MESSAGE", messageJson.toString());
            serverManager.sendToClient(receiver.getUsername(), messagePacket);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void handleBroadcastMessage(String payload, ClientHandler senderClient) {
        try {
            JsonObject json = JsonParser.parseString(payload).getAsJsonObject();
            String content = json.get("content").getAsString();

            JsonObject messageJson = new JsonObject();
            messageJson.addProperty("sender", senderClient.getCurrentUsername());
            messageJson.addProperty("content", content);
            messageJson.addProperty("type", "TEXT");

            Packet messagePacket = new Packet("CHAT_MESSAGE", messageJson.toString());
            serverManager.broadcastPacket(messagePacket, senderClient);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
