package org.example.server;

import org.example.server.network.ServerManager;
import org.example.server.util.HibernateUtil;

public class ServerApplication {
    public static void main(String[] args) {
        System.out.println("Initializing Database connection...");
        HibernateUtil.getSessionFactory();
        System.out.println("Database initialized successfully.");
        ServerManager server = new ServerManager();
        server.startServer();
    }
}
