package org.example.server.service;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.example.common.model.Friendship;
import org.example.common.model.User;
import org.example.common.network.Packet;
import org.example.server.dao.FriendshipDAO;
import org.example.server.dao.UserDAO;
import org.example.server.network.ClientHandler;
import org.example.server.network.ServerManager;

import java.util.List;

public class FriendService {
    private final ServerManager serverManager;
    private final UserDAO userDAO = new UserDAO();
    private final FriendshipDAO friendshipDAO = new FriendshipDAO();

    public FriendService(ServerManager serverManager) {
        this.serverManager = serverManager;
    }

    public void handleLoadFriends(ClientHandler client) {
        try {
            User user = userDAO.findByUsername(client.getCurrentUsername());
            if (user == null) return;

            List<Friendship> friends = friendshipDAO.getAcceptedFriends(user);
            List<Friendship> pendingRequests = friendshipDAO.getPendingRequests(user);

            JsonObject responseJson = new JsonObject();

            JsonArray friendsArray = new JsonArray();
            if (friends != null) {
                for (Friendship f : friends) {
                    User friendUser = f.getUser().getUsername().equals(user.getUsername()) ? f.getFriend() : f.getUser();
                    
                    String blockedBy = f.getBlockedBy();
                    
                    // Bây giờ cho phép hiển thị bạn bè ngay cả khi họ chặn mình
                    // (chỉ không cho nhắn tin, để UI vẫn giữ được danh sách bạn bè).
                    JsonObject friendObj = new JsonObject();
                    friendObj.addProperty("username", friendUser.getUsername());
                    
                    boolean isOnline = false;
                    for(ClientHandler ch : serverManager.getActiveClients()) {
                        if(friendUser.getUsername().equals(ch.getCurrentUsername())) {
                            isOnline = true;
                            break;
                        }
                    }
                    
                    friendObj.addProperty("status", isOnline ? "ONLINE" : "OFFLINE");
                    
                    // Cung cấp thêm cờ cho UI biết MÌNH CÓ ĐANG CHẶN HỌ KHÔNG (để UI đổi nút thành "Bỏ chặn")
                    boolean amIBlocking = blockedBy != null && blockedBy.contains(user.getUsername());
                    friendObj.addProperty("isBlockedByMe", amIBlocking);
                    
                    String mutedBy = f.getMutedBy();
                    boolean amIMuting = mutedBy != null && mutedBy.contains(user.getUsername());
                    friendObj.addProperty("isMutedByMe", amIMuting);
                    
                    friendsArray.add(friendObj);
                }
            }
            responseJson.add("friends", friendsArray);

            JsonArray requestsArray = new JsonArray();
            if (pendingRequests != null) {
                for (Friendship f : pendingRequests) {
                    requestsArray.add(f.getUser().getUsername());
                }
            }
            responseJson.add("requests", requestsArray);

            client.sendPacket(new Packet("LOAD_FRIENDS_SUCCESS", responseJson.toString()));

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void handleAddFriend(String payload, ClientHandler senderClient) {
        try {
            JsonObject json = JsonParser.parseString(payload).getAsJsonObject();
            String friendUsername = json.get("friendUsername").getAsString();

            User user = userDAO.findByUsername(senderClient.getCurrentUsername());
            User friend = userDAO.findByUsername(friendUsername);

            if (user == null || friend == null || user.equals(friend)) {
                senderClient.sendPacket(new Packet("FRIEND_ERROR", "Người dùng không tồn tại hoặc không hợp lệ."));
                return;
            }

            Friendship existing = friendshipDAO.findFriendship(user, friend);
            if (existing != null) {
                senderClient.sendPacket(new Packet("FRIEND_ERROR", "Đã gửi lời mời, hoặc đã là bạn bè."));
                return;
            }

            Friendship newFriendship = new Friendship(user, friend, "PENDING");
            friendshipDAO.saveFriendship(newFriendship);

            JsonObject notiJson = new JsonObject();
            notiJson.addProperty("from", user.getUsername());
            serverManager.sendToClient(friend.getUsername(), new Packet("FRIEND_REQUEST", notiJson.toString()));
            
            senderClient.sendPacket(new Packet("FRIEND_SUCCESS", "Đã gửi lời mời kết bạn tới " + friendUsername));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void handleAcceptFriend(String payload, ClientHandler receiverClient) {
        try {
            JsonObject json = JsonParser.parseString(payload).getAsJsonObject();
            String senderUsername = json.get("senderUsername").getAsString();

            User receiver = userDAO.findByUsername(receiverClient.getCurrentUsername());
            User sender = userDAO.findByUsername(senderUsername);

            if (receiver == null || sender == null) return;

            Friendship request = friendshipDAO.findFriendship(sender, receiver);
            if (request != null && request.getStatus().equals("PENDING") && request.getFriend().getUsername().equals(receiver.getUsername())) {
                request.setStatus("ACCEPTED");
                friendshipDAO.updateFriendship(request);

                // Reload cho cả hai phía trực tiếp
                handleLoadFriends(receiverClient);
                ClientHandler senderHandler = serverManager.getClientHandler(senderUsername);
                if (senderHandler != null) {
                    handleLoadFriends(senderHandler);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void handleBlockUser(String payload, ClientHandler client) {
        try {
            JsonObject json = JsonParser.parseString(payload).getAsJsonObject();
            String targetUsername = json.get("targetUsername").getAsString();

            User user = userDAO.findByUsername(client.getCurrentUsername());
            User target = userDAO.findByUsername(targetUsername);

            if (user == null || target == null) return;

            Friendship friendship = friendshipDAO.findFriendship(user, target);
            if (friendship != null) {
                String blockedBy = friendship.getBlockedBy();
                String myUsername = user.getUsername();
                boolean isCurrentlyBlocked = blockedBy != null && blockedBy.contains(myUsername);

                if (isCurrentlyBlocked) {
                    // Mở chặn
                    blockedBy = blockedBy.replace(myUsername, "").replace(",,", ",").replaceAll("^,|,$", "");
                    if (blockedBy.isEmpty()) blockedBy = null;
                    client.sendPacket(new Packet("BLOCK_SUCCESS", "Đã BỎ CHẶN người dùng " + targetUsername));
                } else {
                    // Chặn
                    if (blockedBy == null || blockedBy.isEmpty()) {
                        blockedBy = myUsername;
                    } else {
                        blockedBy += "," + myUsername;
                    }
                    client.sendPacket(new Packet("BLOCK_SUCCESS", "Đã CHẶN người dùng " + targetUsername + ". Họ sẽ không thể nhắn tin cho bạn."));
                }

                // Cập nhật DB
                friendshipDAO.updateBlockStatus(user, target, blockedBy);

                // Reload danh sách bạn bè cho NGƯỜI THỰC HIỆN CHẶN/BỎ CHẶN
                handleLoadFriends(client);

                // Push trực tiếp danh sách bạn bè mới cho TARGET nếu họ đang online.
                // Dùng getClientHandler + handleLoadFriends thay vì RELOAD_FRIENDS để tránh
                // race condition: client gửi LOAD_FRIENDS_REQUEST ngay sau khi nhận RELOAD_FRIENDS,
                // server có thể chưa commit xong DB update ở thời điểm đó.
                ClientHandler targetHandler = serverManager.getClientHandler(target.getUsername());
                if (targetHandler != null) {
                    handleLoadFriends(targetHandler);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void handleMuteUser(String payload, ClientHandler client) {
        try {
            JsonObject json = JsonParser.parseString(payload).getAsJsonObject();
            String targetUsername = json.get("targetUsername").getAsString();

            User user = userDAO.findByUsername(client.getCurrentUsername());
            User target = userDAO.findByUsername(targetUsername);

            if (user == null || target == null) return;

            Friendship friendship = friendshipDAO.findFriendship(user, target);
            if (friendship != null) {
                String mutedBy = friendship.getMutedBy();
                String myUsername = user.getUsername();
                boolean isCurrentlyMuted = mutedBy != null && mutedBy.contains(myUsername);

                if (isCurrentlyMuted) {
                    // Bật lại TB
                    mutedBy = mutedBy.replace(myUsername, "").replace(",,", ",").replaceAll("^,|,$", "");
                    if (mutedBy.isEmpty()) mutedBy = null;
                    client.sendPacket(new Packet("MUTE_SUCCESS", "Đã BẬT LẠI thông báo từ " + targetUsername));
                } else {
                    // Tắt TB
                    if (mutedBy == null || mutedBy.isEmpty()) {
                        mutedBy = myUsername;
                    } else {
                        mutedBy += "," + myUsername;
                    }
                    client.sendPacket(new Packet("MUTE_SUCCESS", "Đã TẮT thông báo từ " + targetUsername));
                }

                // Sử dụng hàm HQL thay vì merge
                friendshipDAO.updateMuteStatus(user, target, mutedBy);

                handleLoadFriends(client);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
