package org.example.server.service;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.example.common.model.CallLog;
import org.example.common.model.Friendship;
import org.example.common.model.GroupChat;
import org.example.common.model.GroupMember;
import org.example.common.model.Message;
import org.example.common.model.User;
import org.example.common.network.Packet;
import org.example.server.dao.CallLogDAO;
import org.example.server.dao.FriendshipDAO;
import org.example.server.dao.GroupDAO;
import org.example.server.dao.GroupMemberDAO;
import org.example.server.dao.MessageDAO;
import org.example.server.dao.MessageReactionDAO;
import org.example.server.dao.UserDAO;
import org.example.server.network.ClientHandler;
import org.example.server.network.ServerManager;

import java.util.ArrayList;
import java.util.HashMap;
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
    private final CallLogDAO callLogDAO = new CallLogDAO();

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

            // tải reaction theo lô
            Map<Long, JsonArray> reactionBatch = Map.of();
            if (history != null && !history.isEmpty()) {
                List<Long> ids = history.stream().map(Message::getId).toList();
                reactionBatch = messageReactionDAO.getReactionSummaryBatch(ids);
            }

            // tải lịch sử cuộc gọi giữa hai user
            List<CallLog> callLogs = callLogDAO.getCallHistoryBetween(currentUser, otherUser, limit);

            // gộp tin nhắn và cuộc gọi theo thời gian
            JsonArray messagesArray = new JsonArray();
            int mi = 0, ci = 0;
            List<Message> msgs = history != null ? history : List.of();

            while (mi < msgs.size() || ci < callLogs.size()) {
                boolean pickMessage;
                if (mi >= msgs.size()) {
                    pickMessage = false;
                } else if (ci >= callLogs.size()) {
                    pickMessage = true;
                } else {
                    // so sánh thời gian gửi tin nhắn và bắt đầu cuộc gọi
                    pickMessage = msgs.get(mi).getSentAt().compareTo(callLogs.get(ci).getStartedAt()) <= 0;
                }

                if (pickMessage) {
                    Message msg = msgs.get(mi++);
                    JsonArray reactions = reactionBatch.getOrDefault(msg.getId(), new JsonArray());
                    messagesArray.add(toMessageJson(msg, currentUser, reactions));
                } else {
                    CallLog log = callLogs.get(ci++);
                    messagesArray.add(toCallLogJson(log, currentUser));
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

            // tải reaction bằng một query theo lô
            Map<Long, JsonArray> reactionBatch = Map.of();
            if (history != null && !history.isEmpty()) {
                List<Long> ids = history.stream().map(Message::getId).toList();
                reactionBatch = messageReactionDAO.getReactionSummaryBatch(ids);
            }

            JsonArray messagesArray = new JsonArray();
            if (history != null) {
                for (Message msg : history) {
                    JsonArray reactions = reactionBatch.getOrDefault(msg.getId(), new JsonArray());
                    messagesArray.add(toMessageJson(msg, currentUser, reactions));
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
            if (groups.isEmpty()) {
                JsonObject response = new JsonObject();
                response.add("groups", new JsonArray());
                client.sendPacket(new Packet("GROUP_LIST", response.toString()));
                return;
            }

            // đếm thành viên theo lô để tránh nhiều query
            List<Long> groupIds = groups.stream().map(GroupChat::getId).toList();
            Map<Long, Integer> memberCounts = groupMemberDAO.getMemberCountBatch(groupIds);

            JsonArray groupsArray = new JsonArray();
            for (GroupChat g : groups) {
                JsonObject groupObj = new JsonObject();
                groupObj.addProperty("groupId", g.getId());
                groupObj.addProperty("groupName", g.getName());
                groupObj.addProperty("memberCount", memberCounts.getOrDefault(g.getId(), 0));
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

            // tải trước số thành viên nhóm để tránh n+1
            List<GroupChat> userGroups = groupDAO.getGroupsOfUser(currentUser);
            Map<Long, Integer> memberCounts = Map.of();
            if (userGroups != null && !userGroups.isEmpty()) {
                List<Long> groupIds = userGroups.stream().map(GroupChat::getId).toList();
                memberCounts = groupMemberDAO.getMemberCountBatch(groupIds);
            }
            // tạo bản sao final để dùng trong lambda
            final Map<Long, Integer> memberCountsRef = memberCounts;

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
                            displayName(otherUser),
                            previewMessage(message),
                            message.getSentAt().toString(),
                            otherUser
                    ));
                } else if (message.getGroupChat() != null) {
                    GroupChat groupChat = message.getGroupChat();
                    String key = "GROUP:" + groupChat.getId();
                    // dùng kết quả đếm theo lô cho từng nhóm
                    int count = memberCountsRef.getOrDefault(groupChat.getId(), 0);
                    String title = groupChat.getName() + " (" + count + ")";
                    conversations.putIfAbsent(key, toConversationJson(
                            key,
                            "GROUP",
                            title,
                            message.getSender().getUsername() + ": " + previewMessage(message),
                            message.getSentAt().toString()
                    ));
                }
            }

            List<Friendship> friends = friendshipDAO.getAcceptedFriends(currentUser);
            if (friends != null) {
                for (Friendship friendship : friends) {
                    User friend = friendship.getUser().getId().equals(currentUser.getId())
                            ? friendship.getFriend()
                            : friendship.getUser();
                    String key = "PRIVATE:" + friend.getUsername();
                    conversations.putIfAbsent(key, toConversationJson(key, "PRIVATE", displayName(friend), "", "", friend));
                }
            }

            if (userGroups != null) {
                for (GroupChat group : userGroups) {
                    String key = "GROUP:" + group.getId();
                    int count = memberCountsRef.getOrDefault(group.getId(), 0);
                    String title = group.getName() + " (" + count + ")";
                    conversations.putIfAbsent(key, toConversationJson(key, "GROUP", title, "", ""));
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
            String filename = json.has("filename") ? json.get("filename").getAsString() : null;
            String messageType = json.has("type") ? json.get("type").getAsString() : "TEXT";

            User sender = userDAO.findByUsername(senderClient.getCurrentUsername());
            GroupChat groupChat = groupDAO.findById(groupId);

            if (sender == null || groupChat == null || !groupMemberDAO.isMember(groupChat, sender)) {
                senderClient.sendPacket(new Packet("GROUP_ERROR", "Bạn không thuộc nhóm này."));
                return;
            }

            Message message = new Message(sender, groupChat, messageType, content);
            message.setFileName(filename);
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
            String filename = json.has("filename") ? json.get("filename").getAsString() : null;
            String messageType = json.has("type") ? json.get("type").getAsString() : "TEXT";

            User sender = userDAO.findByUsername(senderClient.getCurrentUsername());
            User receiver = userDAO.findByUsername(receiverUsername);

            if (sender == null || receiver == null) return;

            // kiểm tra trạng thái chặn
            if (friendshipDAO.isBlocked(sender, receiver)) {
                senderClient.sendPacket(new Packet("CHAT_ERROR", "Bạn đã bị người này chặn, không thể gửi tin nhắn."));
                return;
            }

            // lưu tin nhắn vào db
            Message message = new Message(sender, receiver, messageType, content);
            message.setFileName(filename);
            messageDAO.saveMessage(message);

            // kiểm tra trạng thái tắt thông báo
            boolean isMuted = friendshipDAO.isMuted(sender, receiver);

            // gửi ack cho người gửi với id tin nhắn thật
            JsonObject ackJson = toMessageJson(message, sender);
            ackJson.addProperty("receiver", receiver.getUsername());
            senderClient.sendPacket(new Packet("CHAT_ACK", ackJson.toString()));

            // tạo payload gửi cho người nhận
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
        // tải reaction cho một tin nhắn đơn lẻ
        return toMessageJson(msg, currentUser, messageReactionDAO.getReactionSummary(msg));
    }

    private JsonObject toMessageJson(Message msg, User currentUser, JsonArray reactions) {
        JsonObject msgObj = new JsonObject();
        msgObj.addProperty("id", msg.getId());
        msgObj.addProperty("sender", msg.getSender().getUsername().equals(currentUser.getUsername()) ? "Bạn" : msg.getSender().getUsername());
        msgObj.addProperty("senderUsername", msg.getSender().getUsername());
        msgObj.addProperty("senderDisplayName", displayName(msg.getSender()));
        msgObj.addProperty("senderAvatar", safeAvatar(msg.getSender()));

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
        if (msg.getFileName() != null && !msg.getFileName().isBlank()) {
            msgObj.addProperty("filename", msg.getFileName());
        }
        msgObj.addProperty("timestamp", msg.getSentAt().toString());
        msgObj.addProperty("isRecalled", msg.isRecalled());
        msgObj.add("reactions", reactions);
        return msgObj;
    }

    private JsonObject toConversationJson(String key, String type, String title, String preview, String timestamp) {
        JsonObject obj = new JsonObject();
        obj.addProperty("key", key);
        obj.addProperty("type", type);
        obj.addProperty("title", title);
        obj.addProperty("displayName", title);
        obj.addProperty("avatar", "");
        obj.addProperty("preview", preview);
        obj.addProperty("timestamp", timestamp);
        return obj;
    }

    private JsonObject toConversationJson(String key, String type, String title, String preview, String timestamp, User user) {
        JsonObject obj = toConversationJson(key, type, title, preview, timestamp);
        obj.addProperty("displayName", displayName(user));
        obj.addProperty("avatar", safeAvatar(user));
        return obj;
    }

    private JsonObject toCallLogJson(CallLog log, User currentUser) {
        JsonObject obj = new JsonObject();
        obj.addProperty("id", log.getId());
        obj.addProperty("type", "CALL_LOG");

        boolean isCaller = log.getCaller().getId().equals(currentUser.getId());
        obj.addProperty("sender", isCaller ? "Bạn" : log.getCaller().getUsername());
        obj.addProperty("callType", log.getCallType());
        obj.addProperty("status", log.getStatus());
        obj.addProperty("timestamp", log.getStartedAt().toString());

        // tạo nội dung hiển thị theo trạng thái cuộc gọi
        String icon;
        String text;
        switch (log.getStatus()) {
            case "COMPLETED" -> {
                icon = "📞";
                int dur = log.getDurationSec() != null ? log.getDurationSec() : 0;
                text = "Cuộc gọi thoại — " + String.format("%d:%02d", dur / 60, dur % 60);
            }
            case "MISSED" -> {
                icon = "📵";
                text = isCaller ? "Cuộc gọi nhỡ (không nhấc máy)" : "Cuộc gọi nhỡ";
            }
            case "REJECTED" -> {
                icon = "❌";
                text = isCaller ? "Đã bị từ chối" : "Bạn đã từ chối";
            }
            case "CANCELED" -> {
                icon = "↩️";
                text = isCaller ? "Bạn đã hủy cuộc gọi" : "Cuộc gọi bị hủy";
            }
            default -> {
                icon = "📞";
                text = "Cuộc gọi";
            }
        }
        obj.addProperty("content", icon + " " + text);

        return obj;
    }

    private String previewMessage(Message message) {
        if (message.isRecalled()) {
            return "[Tin nhắn đã bị thu hồi]";
        }
        String type = message.getMessageType();
        if ("FILE".equals(type)) {
            return "[File]";
        }
        if ("IMAGE".equals(type)) {
            return "[Hình ảnh]";
        }
        if ("VOICE".equals(type)) {
            return "[Voice]";
        }
        if ("VIDEO".equals(type)) {
            return "[Video]";
        }
        String content = message.getContent();
        return content.length() > 80 ? content.substring(0, 80) + "..." : content;
    }

    private String displayName(User user) {
        if (user == null) {
            return "";
        }
        String fullName = user.getFullName();
        if (fullName != null && !fullName.isBlank()) {
            return fullName;
        }
        return user.getUsername();
    }

    private String safeAvatar(User user) {
        if (user == null || user.getAvatar() == null) {
            return "";
        }
        return user.getAvatar();
    }

    private void notifyGroupListUpdated(GroupChat groupChat) {
        for (User member : groupMemberDAO.getMembers(groupChat)) {
            serverManager.sendToClient(member.getUsername(), new Packet("GROUP_LIST_UPDATED", ""));
        }
    }
}
