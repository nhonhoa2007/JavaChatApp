package org.example.server.service;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.example.common.model.GroupChat;
import org.example.common.model.GroupMember;
import org.example.common.model.Message;
import org.example.common.model.User;
import org.example.common.network.Packet;
import org.example.server.dao.FriendshipDAO;
import org.example.server.dao.GroupDAO;
import org.example.server.dao.GroupMemberDAO;
import org.example.server.dao.MessageDAO;
import org.example.server.dao.MessageReactionDAO;
import org.example.server.dao.UserDAO;
import org.example.server.network.ClientHandler;
import org.example.server.network.ServerManager;

import java.util.List;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

public class ChatService {
    private final ServerManager serverManager;
    private final UserDAO userDAO = new UserDAO();
    private final MessageDAO messageDAO = new MessageDAO();
    private final FriendshipDAO friendshipDAO = new FriendshipDAO();
    private final GroupDAO groupDAO = new GroupDAO();
    private final GroupMemberDAO groupMemberDAO = new GroupMemberDAO();
    private final MessageReactionDAO messageReactionDAO = new MessageReactionDAO();

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
                    messagesArray.add(toMessageJson(msg, currentUser));
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

    public void handleLoadGroupHistory(String payload, ClientHandler client) {
        try {
            JsonObject json = JsonParser.parseString(payload).getAsJsonObject();
            long groupId = json.get("groupId").getAsLong();
            int limit = json.has("limit") ? json.get("limit").getAsInt() : 50;

            User currentUser = userDAO.findByUsername(client.getCurrentUsername());
            GroupChat groupChat = groupDAO.findById(groupId);

            if (currentUser == null || groupChat == null || !groupMemberDAO.isMember(groupChat, currentUser)) {
                return;
            }

            List<Message> history = messageDAO.getGroupHistory(groupChat, limit);
            JsonArray messagesArray = new JsonArray();
            if (history != null) {
                for (Message msg : history) {
                    messagesArray.add(toMessageJson(msg, currentUser));
                }
            }

            JsonObject responseJson = new JsonObject();
            responseJson.addProperty("groupId", groupId);
            responseJson.addProperty("groupName", groupChat.getName());
            responseJson.add("messages", messagesArray);

            client.sendPacket(new Packet("GROUP_HISTORY", responseJson.toString()));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void handleCreateGroup(String payload, ClientHandler client) {
        try {
            JsonObject json = JsonParser.parseString(payload).getAsJsonObject();
            String groupName = json.get("groupName").getAsString().trim();
            JsonArray membersArray = json.getAsJsonArray("members");

            if (groupName.isEmpty()) {
                client.sendPacket(new Packet("GROUP_ERROR", "Tên nhóm không được để trống."));
                return;
            }

            User creator = userDAO.findByUsername(client.getCurrentUsername());
            if (creator == null) {
                client.sendPacket(new Packet("GROUP_ERROR", "Không xác định được người tạo nhóm."));
                return;
            }

            Set<String> uniqueMembers = new LinkedHashSet<>();
            uniqueMembers.add(creator.getUsername());
            for (int i = 0; i < membersArray.size(); i++) {
                String username = membersArray.get(i).getAsString().trim();
                if (!username.isEmpty()) {
                    uniqueMembers.add(username);
                }
            }

            if (uniqueMembers.size() < 3) {
                client.sendPacket(new Packet("GROUP_ERROR", "Nhóm phải có ít nhất 3 thành viên."));
                return;
            }

            Set<User> resolvedMembers = new LinkedHashSet<>();
            for (String username : uniqueMembers) {
                User member = userDAO.findByUsername(username);
                if (member != null) {
                    resolvedMembers.add(member);
                }
            }

            if (resolvedMembers.size() < 3) {
                client.sendPacket(new Packet("GROUP_ERROR", "Không đủ thành viên hợp lệ để tạo nhóm (cần >= 3)."));
                return;
            }

            GroupChat groupChat = new GroupChat(groupName, creator);
            if (!groupDAO.save(groupChat)) {
                client.sendPacket(new Packet("GROUP_ERROR", "Không thể tạo nhóm."));
                return;
            }

            for (User member : resolvedMembers) {
                groupMemberDAO.save(new GroupMember(groupChat, member));
            }

            client.sendPacket(new Packet("GROUP_SUCCESS", "Tạo nhóm thành công: " + groupName));
            notifyGroupListUpdated(groupChat);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void handleLoadGroups(ClientHandler client) {
        try {
            User user = userDAO.findByUsername(client.getCurrentUsername());
            if (user == null) {
                return;
            }

            List<GroupChat> groups = groupDAO.getGroupsOfUser(user);
            JsonArray groupsArray = new JsonArray();
            for (GroupChat g : groups) {
                JsonObject groupObj = new JsonObject();
                groupObj.addProperty("groupId", g.getId());
                groupObj.addProperty("groupName", g.getName());
                groupObj.addProperty("memberCount", groupMemberDAO.getMembers(g).size());
                groupsArray.add(groupObj);
            }

            JsonObject response = new JsonObject();
            response.add("groups", groupsArray);
            client.sendPacket(new Packet("GROUP_LIST", response.toString()));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void handleLoadConversations(ClientHandler client) {
        try {
            User currentUser = userDAO.findByUsername(client.getCurrentUsername());
            if (currentUser == null) {
                return;
            }

            Map<String, JsonObject> conversations = new LinkedHashMap<>();
            for (Message message : messageDAO.getConversationMessages(currentUser)) {
                if (message.getReceiver() != null) {
                    User otherUser = message.getSender().getId().equals(currentUser.getId())
                            ? message.getReceiver()
                            : message.getSender();
                    String key = "PRIVATE:" + otherUser.getUsername();
                    conversations.putIfAbsent(key, toConversationJson(
                            key,
                            "PRIVATE",
                            otherUser.getUsername(),
                            previewMessage(message),
                            message.getSentAt().toString()
                    ));
                } else if (message.getGroupChat() != null) {
                    GroupChat groupChat = message.getGroupChat();
                    String key = "GROUP:" + groupChat.getId();
                    String title = groupChat.getName() + " (" + groupMemberDAO.getMembers(groupChat).size() + ")";
                    conversations.putIfAbsent(key, toConversationJson(
                            key,
                            "GROUP",
                            title,
                            message.getSender().getUsername() + ": " + previewMessage(message),
                            message.getSentAt().toString()
                    ));
                }
            }

            JsonArray conversationsArray = new JsonArray();
            for (JsonObject conversation : conversations.values()) {
                conversationsArray.add(conversation);
            }

            JsonObject response = new JsonObject();
            response.add("conversations", conversationsArray);
            client.sendPacket(new Packet("CONVERSATION_LIST", response.toString()));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void handleGroupMessage(String payload, ClientHandler senderClient) {
        try {
            JsonObject json = JsonParser.parseString(payload).getAsJsonObject();
            long groupId = json.get("groupId").getAsLong();
            String content = json.get("content").getAsString();
            String messageType = json.has("type") ? json.get("type").getAsString() : "TEXT";

            User sender = userDAO.findByUsername(senderClient.getCurrentUsername());
            GroupChat groupChat = groupDAO.findById(groupId);

            if (sender == null || groupChat == null || !groupMemberDAO.isMember(groupChat, sender)) {
                senderClient.sendPacket(new Packet("GROUP_ERROR", "Bạn không thuộc nhóm này."));
                return;
            }

            Message message = new Message(sender, groupChat, messageType, content);
            messageDAO.saveMessage(message);

            JsonObject ackJson = toMessageJson(message, sender);
            ackJson.addProperty("groupId", groupId);
            senderClient.sendPacket(new Packet("GROUP_MESSAGE_ACK", ackJson.toString()));

            JsonObject outgoing = toMessageJson(message, sender);
            outgoing.addProperty("sender", sender.getUsername());
            outgoing.addProperty("groupId", groupId);

            Packet groupPacket = new Packet("GROUP_MESSAGE", outgoing.toString());
            for (User member : groupMemberDAO.getMembers(groupChat)) {
                if (!member.getUsername().equals(sender.getUsername())) {
                    serverManager.sendToClient(member.getUsername(), groupPacket);
                }
            }
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

            // Gửi lại gói tin cho SENDER để họ có ID thực tế của tin nhắn (phục vụ Edit/Recall/Reaction)
            JsonObject ackJson = toMessageJson(message, sender);
            ackJson.addProperty("receiver", receiver.getUsername());
            senderClient.sendPacket(new Packet("CHAT_ACK", ackJson.toString()));

            // Tạo payload gửi cho RECEIVER
            JsonObject messageJson = toMessageJson(message, sender);
            messageJson.addProperty("sender", sender.getUsername());
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

    private JsonObject toMessageJson(Message msg, User currentUser) {
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
        msgObj.add("reactions", messageReactionDAO.getReactionSummary(msg));
        return msgObj;
    }

    private JsonObject toConversationJson(String key, String type, String title, String preview, String timestamp) {
        JsonObject obj = new JsonObject();
        obj.addProperty("key", key);
        obj.addProperty("type", type);
        obj.addProperty("title", title);
        obj.addProperty("preview", preview);
        obj.addProperty("timestamp", timestamp);
        return obj;
    }

    private String previewMessage(Message message) {
        if (message.isRecalled()) {
            return "[Tin nhắn đã bị thu hồi]";
        }
        String type = message.getMessageType();
        if ("IMAGE".equals(type)) {
            return "[Hình ảnh]";
        }
        if ("VOICE".equals(type)) {
            return "[Voice]";
        }
        String content = message.getContent();
        return content.length() > 80 ? content.substring(0, 80) + "..." : content;
    }

    private void notifyGroupListUpdated(GroupChat groupChat) {
        for (User member : groupMemberDAO.getMembers(groupChat)) {
            serverManager.sendToClient(member.getUsername(), new Packet("GROUP_LIST_UPDATED", ""));
        }
    }
}
