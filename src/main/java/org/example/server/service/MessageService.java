package org.example.server.service;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.example.common.model.Message;
import org.example.common.network.Packet;
import org.example.server.dao.MessageDAO;
import org.example.server.network.ClientHandler;
import org.example.server.network.ServerManager;

import java.time.LocalDateTime;

public class MessageService {
    private final ServerManager serverManager;
    private final MessageDAO messageDAO = new MessageDAO();

    public MessageService(ServerManager serverManager) {
        this.serverManager = serverManager;
    }

    public void handleRecallMessage(String payload, ClientHandler senderClient) {
        try {
            JsonObject json = JsonParser.parseString(payload).getAsJsonObject();
            Long messageId = json.get("messageId").getAsLong();

            Message message = messageDAO.findById(messageId);
            
            // Check if message exists and belongs to the sender
            if (message != null && message.getSender().getUsername().equals(senderClient.getCurrentUsername())) {
                message.setRecalled(true);
                message.setUpdatedAt(LocalDateTime.now());
                messageDAO.updateMessage(message);

                // Notify receiver to hide the message
                JsonObject recallJson = new JsonObject();
                recallJson.addProperty("messageId", messageId);
                Packet recallPacket = new Packet("MESSAGE_RECALLED", recallJson.toString());

                if (message.getReceiver() != null) {
                    serverManager.sendToClient(message.getReceiver().getUsername(), recallPacket);
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

            // Check if message exists, belongs to the sender, and is not recalled
            if (message != null && !message.isRecalled() && message.getSender().getUsername().equals(senderClient.getCurrentUsername())) {
                message.setContent(newContent);
                message.setEdited(true);
                message.setUpdatedAt(LocalDateTime.now());
                messageDAO.updateMessage(message);

                // Notify receiver to update the message UI
                JsonObject editJson = new JsonObject();
                editJson.addProperty("messageId", messageId);
                editJson.addProperty("newContent", newContent);
                Packet editPacket = new Packet("MESSAGE_EDITED", editJson.toString());

                if (message.getReceiver() != null) {
                    serverManager.sendToClient(message.getReceiver().getUsername(), editPacket);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
