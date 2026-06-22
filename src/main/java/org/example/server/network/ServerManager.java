package org.example.server.network;

import org.example.common.network.Packet;
import org.example.common.model.User;
import org.example.server.dao.UserDAO;
import org.example.server.util.PasswordUtil;
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
    private UdpRelayService audioRelayService;
    private UdpRelayService videoRelayService;

    public CallService getCallService() {
        return callService;
    }

    public UdpRelayService getAudioRelayService() {
        return audioRelayService;
    }

    public UdpRelayService getVideoRelayService() {
        return videoRelayService;
    }

    // giữ relay cũ để tương thích tạm thời
    public UdpRelayService getUdpRelayService() {
        return audioRelayService;
    }

    public void startServer() {
        // khởi động relay udp trước tcp server
        try {
            audioRelayService = new UdpRelayService(8889, 2048);
            audioRelayService.start();
            videoRelayService = new UdpRelayService(8890, 65535);
            videoRelayService.start();
        } catch (Exception e) {
            System.err.println("Failed to start UDP relays: " + e.getMessage());
            e.printStackTrace();
        }


        // đảm bảo cột tên và avatar hỗ trợ tiếng việt và base64
        try {
            try (org.hibernate.Session session = org.example.server.util.HibernateUtil.getSessionFactory().openSession()) {
                org.hibernate.Transaction tx = session.beginTransaction();
                session.createNativeQuery("ALTER TABLE Users ALTER COLUMN full_name NVARCHAR(100)").executeUpdate();
                session.createNativeQuery("ALTER TABLE Users ALTER COLUMN avatar VARCHAR(MAX)").executeUpdate();
                tx.commit();
                System.out.println("Users.full_name altered to NVARCHAR(100) and avatar altered to VARCHAR(MAX)");
            } catch (Exception e) {
                System.err.println("Failed to alter Users table columns: " + e.getMessage());
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        // tạo hoặc reset tài khoản admin mặc định
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
                // đảm bảo mật khẩu và quyền admin mặc định
                admin.setPasswordHash(PasswordUtil.hashPassword("admin123"));
                admin.setRole("ADMIN");
                userDAO.updateUser(admin);
                System.out.println("Default admin account reset to: admin / admin123");
            }
        } catch (Exception e) {
            System.err.println("Failed to create default admin account: " + e.getMessage());
            e.printStackTrace();
        }

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

    // gửi trạng thái online/offline cho đúng danh sách bạn bè
    // tránh broadcast toàn bộ client đang online
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

    // gửi trạng thái cho tất cả client khi chưa có danh sách bạn bè
    // giữ lại để authservice dùng trong luồng cũ
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

    // lấy clienthandler đang hoạt động theo username
    // dùng để cập nhật bạn bè trực tiếp cho target user
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
