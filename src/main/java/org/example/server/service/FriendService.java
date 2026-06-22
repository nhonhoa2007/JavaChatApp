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

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.Map;
import java.util.HashMap;

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

            // tạo tập username đang online để tra cứu nhanh
            Set<String> onlineUsernames = new HashSet<>();
            for (org.example.server.network.ClientHandler ch : serverManager.getActiveClients()) {
                if (ch.getCurrentUsername() != null) {
                    onlineUsernames.add(ch.getCurrentUsername());
                }
            }

            JsonObject responseJson = new JsonObject();

            JsonArray friendsArray = new JsonArray();
            if (friends != null) {
                for (Friendship f : friends) {
                    User friendUser = f.getUser().getUsername().equals(user.getUsername()) ? f.getFriend() : f.getUser();

                    String blockedBy = f.getBlockedBy();

                    JsonObject friendObj = new JsonObject();
                    friendObj.addProperty("username", friendUser.getUsername());
                    // tra cứu trạng thái online trong o(1)
                    friendObj.addProperty("status", onlineUsernames.contains(friendUser.getUsername()) ? "ONLINE" : "OFFLINE");

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

                // tải lại danh sách bạn bè cho cả hai phía
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
                    // mở chặn người dùng
                    blockedBy = blockedBy.replace(myUsername, "").replace(",,", ",").replaceAll("^,|,$", "");
                    if (blockedBy.isEmpty()) blockedBy = null;
                    client.sendPacket(new Packet("BLOCK_SUCCESS", "Đã BỎ CHẶN người dùng " + targetUsername));
                } else {
                    // chặn người dùng
                    if (blockedBy == null || blockedBy.isEmpty()) {
                        blockedBy = myUsername;
                    } else {
                        blockedBy += "," + myUsername;
                    }
                    client.sendPacket(new Packet("BLOCK_SUCCESS", "Đã CHẶN người dùng " + targetUsername + ". Họ sẽ không thể nhắn tin cho bạn."));
                }

                // cập nhật trạng thái chặn trong db
                friendshipDAO.updateBlockStatus(user, target, blockedBy);

                // tải lại danh sách bạn bè cho người thao tác
                handleLoadFriends(client);

                // đẩy trực tiếp danh sách mới cho target nếu đang online
                // gọi handleloadfriends sau khi cập nhật db để tránh race condition
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
                    // bật lại thông báo
                    mutedBy = mutedBy.replace(myUsername, "").replace(",,", ",").replaceAll("^,|,$", "");
                    if (mutedBy.isEmpty()) mutedBy = null;
                    client.sendPacket(new Packet("MUTE_SUCCESS", "Đã BẬT LẠI thông báo từ " + targetUsername));
                } else {
                    // tắt thông báo
                    if (mutedBy == null || mutedBy.isEmpty()) {
                        mutedBy = myUsername;
                    } else {
                        mutedBy += "," + myUsername;
                    }
                    client.sendPacket(new Packet("MUTE_SUCCESS", "Đã TẮT thông báo từ " + targetUsername));
                }

                // cập nhật bằng hql để tránh merge entity
                friendshipDAO.updateMuteStatus(user, target, mutedBy);

                handleLoadFriends(client);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void handleSearchAllUsers(ClientHandler client) {
        try {
            User currentUser = userDAO.findByUsername(client.getCurrentUsername());
            if (currentUser == null) return;

            List<User> allUsers = userDAO.findAllUsers();
            List<Friendship> friendships = friendshipDAO.getAllFriendships(currentUser);
            
            // tạo map friendship theo username để tra cứu nhanh
            Map<String, Friendship> friendshipMap = new HashMap<>();
            for (Friendship f : friendships) {
                String other = f.getUser().getUsername().equals(currentUser.getUsername()) 
                        ? f.getFriend().getUsername() 
                        : f.getUser().getUsername();
                friendshipMap.put(other, f);
            }

            // lấy danh sách client đang online
            Set<String> onlineUsernames = new HashSet<>();
            for (ClientHandler ch : serverManager.getActiveClients()) {
                if (ch.getCurrentUsername() != null) {
                    onlineUsernames.add(ch.getCurrentUsername());
                }
            }

            JsonObject responseJson = new JsonObject();
            JsonArray usersArray = new JsonArray();

            for (User u : allUsers) {
                if (u.getUsername().equals(currentUser.getUsername())) {
                    continue; // bỏ qua chính mình
                }

                JsonObject userObj = new JsonObject();
                userObj.addProperty("username", u.getUsername());
                userObj.addProperty("status", onlineUsernames.contains(u.getUsername()) ? "ONLINE" : "OFFLINE");

                String relation = "NONE";
                Friendship f = friendshipMap.get(u.getUsername());
                if (f != null) {
                    if ("ACCEPTED".equals(f.getStatus())) {
                        relation = "FRIEND";
                    } else if ("PENDING".equals(f.getStatus())) {
                        if (f.getUser().getUsername().equals(currentUser.getUsername())) {
                            relation = "PENDING_SENT";
                        } else {
                            relation = "PENDING_RECEIVED";
                        }
                    }
                }
                userObj.addProperty("relation", relation);
                usersArray.add(userObj);
            }

            responseJson.add("users", usersArray);
            client.sendPacket(new Packet("SEARCH_ALL_USERS_SUCCESS", responseJson.toString()));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
