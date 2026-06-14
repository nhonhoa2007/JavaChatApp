package org.example.server;

import org.example.server.network.ServerManager;
import org.example.server.util.HibernateUtil;

public class ServerApplication {
    public static void main(String[] args) {

        ServerManager server = new ServerManager();
        server.startServer();
    }
}
