package org.example.server.network;

import org.example.common.network.Packet;
<<<<<<< HEAD
import org.example.common.model.User;
import org.example.server.dao.UserDAO;
import org.example.server.util.PasswordUtil;
=======
>>>>>>> 67bf400d8ef98f36308a989e33fbbb4dfc6f2a3e
import org.example.server.service.CallService;

import java.net.ServerSocket;
import java.net.Socket;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ServerManager {
    private static final int PORT = 8888;
    private final ExecutorService threadPool = Executors.newFixedThreadPool(100);
    private final List<ClientHandler> activeClients = new CopyOnWriteArrayList<>();
    private final CallService callService = new CallService(this);
    private UdpRelayService udpRelayService;

    public CallService getCallService() {
        return callService;
    }

    public UdpRelayService getUdpRelayService() {
        return udpRelayService;
    }

    public void startServer() {
        // Start UDP relay trước TCP server
        try {
            udpRelayService = new UdpRelayService();
            udpRelayService.start();
        } catch (Exception e) {
            System.err.println("Failed to start UDP relay: " + e.getMessage());
            e.printStackTrace();
        }

<<<<<<< HEAD
        // Ensure Users.full_name is NVARCHAR to support Vietnamese characters
        try {
            try (org.hibernate.Session session = org.example.server.util.HibernateUtil.getSessionFactory().openSession()) {
                org.hibernate.Transaction tx = session.beginTransaction();
                session.createNativeQuery("ALTER TABLE Users ALTER COLUMN full_name NVARCHAR(100)", Object.class).executeUpdate();
                tx.commit();
                System.out.println("Users.full_name altered to NVARCHAR(100)");
            } catch (Exception ignored) {}
        } catch (Exception e) {
            e.printStackTrace();
        }

        // Auto create or force reset default admin account
        try {
            UserDAO userDAO = new UserDAO();
            User admin = userDAO.findByUsername("admin");
            if (admin == null) {
                String hashedPw = PasswordUtil.hashPassword("admin123");
                admin = new User("admin", hashedPw, "System Administrator");
                admin.setRole("ADMIN");
                userDAO.saveUser(admin);
                System.out.println("Default admin account created: admin / admin123");
            } else {
                // Đảm bảo mật khẩu và quyền là admin123 / ADMIN
                admin.setPasswordHash(PasswordUtil.hashPassword("admin123"));
                admin.setRole("ADMIN");
                userDAO.updateUser(admin);
                System.out.println("Default admin account reset to: admin / admin123");
            }
        } catch (Exception e) {
            System.err.println("Failed to create default admin account: " + e.getMessage());
            e.printStackTrace();
        }

=======
>>>>>>> 67bf400d8ef98f36308a989e33fbbb4dfc6f2a3e
        try (ServerSocket serverSocket = new ServerSocket(PORT)) {
            System.out.println("Chat Server is running on port " + PORT + "...");
            
            while (true) {
                Socket clientSocket = serverSocket.accept();
                System.out.println("New connection from: " + clientSocket.getInetAddress());
                
                ClientHandler clientHandler = new ClientHandler(clientSocket, this);
                activeClients.add(clientHandler);
                threadPool.execute(clientHandler); 
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public List<ClientHandler> getActiveClients() {
        return activeClients;
    }

    public void removeClient(ClientHandler clientHandler) {
        activeClients.remove(clientHandler);
    }

    /**
     * Broadcast trạng thái online/offline của một user.
     * Chỉ gửi cho những user có trong tập friendUsernames (bạn bè của user đó),
     * thay vì gửi cho TẤT CẢ người đang online — tránh broadcast O(n²).
     *
     * @param username        user vừa login/logout
     * @param isOnline        true = ONLINE, false = OFFLINE
     * @param friendUsernames tập username bạn bè cần được thông báo
     */
    public void broadcastOnlineStatus(String username, boolean isOnline, Set<String> friendUsernames) {
        String status = isOnline ? "ONLINE" : "OFFLINE";
        Packet statusPacket = new Packet("STATUS_UPDATE", username + ":" + status);

        for (ClientHandler client : activeClients) {
            String clientName = client.getCurrentUsername();
            if (clientName != null
                    && !clientName.equals(username)
                    && friendUsernames.contains(clientName)) {
                client.sendPacket(statusPacket);
            }
        }
    }

    /**
     * Overload không cần danh sách bạn bè — broadcast cho tất cả (legacy, dùng khi chưa có friend list).
     * Giữ lại để AuthService có thể gọi mà không cần inject FriendshipDAO.
     */
    public void broadcastOnlineStatus(String username, boolean isOnline) {
        String status = isOnline ? "ONLINE" : "OFFLINE";
        Packet statusPacket = new Packet("STATUS_UPDATE", username + ":" + status);

        for (ClientHandler client : activeClients) {
            if (client.getCurrentUsername() != null && !client.getCurrentUsername().equals(username)) {
                client.sendPacket(statusPacket);
            }
        }
    }

    public void sendToClient(String username, Packet packet) {
        for (ClientHandler client : activeClients) {
            if (username.equals(client.getCurrentUsername())) {
                client.sendPacket(packet);
                break;
            }
        }
    }

    /**
     * Lấy ClientHandler đang active của một username.
     * Dùng để gọi handleLoadFriends trực tiếp cho target user thay vì gửi RELOAD_FRIENDS rồi đợi client reply lại.
     */
    public ClientHandler getClientHandler(String username) {
        for (ClientHandler client : activeClients) {
            if (username.equals(client.getCurrentUsername())) {
                return client;
            }
        }
        return null;
    }

    public void broadcastPacket(Packet packet, ClientHandler excludeClient) {
        for (ClientHandler client : activeClients) {
            if (excludeClient == null || client != excludeClient) {
                if (client.getCurrentUsername() != null) {
                    client.sendPacket(packet);
                }
            }
        }
    }
}
