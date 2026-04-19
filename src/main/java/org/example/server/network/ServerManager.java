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
}
