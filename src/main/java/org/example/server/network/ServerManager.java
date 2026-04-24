package org.example.server.network;

import org.example.common.network.Packet;

import java.net.ServerSocket;
import java.net.Socket;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ServerManager {
    private static final int PORT = 8888;
    private final ExecutorService threadPool = Executors.newFixedThreadPool(100);
    private final List<ClientHandler> activeClients = new CopyOnWriteArrayList<>();

    public void startServer() {
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
