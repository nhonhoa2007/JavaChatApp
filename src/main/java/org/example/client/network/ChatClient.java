package org.example.client.network;

import org.example.common.network.Packet;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.function.Consumer;

public class ChatClient {
    private Socket socket;
    private PrintWriter out;
    private BufferedReader in;
    private Thread listenerThread;
    private Consumer<Packet> onPacketReceived;

    public boolean connect(String host, int port) {
        try {
            socket = new Socket(host, port);
            out = new PrintWriter(socket.getOutputStream(), true);
            in = new BufferedReader(new InputStreamReader(socket.getInputStream()));

            listenerThread = new Thread(this::listenForPackets);
            listenerThread.setDaemon(true);
            listenerThread.start();
            return true;
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }
    }

    private void listenForPackets() {
        try {
            String line;
            while ((line = in.readLine()) != null) {
                Packet packet = Packet.fromJson(line);
                if (packet != null && onPacketReceived != null) {
                    onPacketReceived.accept(packet);
                }
            }
        } catch (IOException e) {
            System.out.println("Disconnected from server.");
        }
    }

    public void setOnPacketReceived(Consumer<Packet> callback) {
        this.onPacketReceived = callback;
    }

    public void sendPacket(Packet packet) {
        if (out != null) {
            out.println(packet.toJson());
        }
    }

    public void disconnect() {
        try {
            if (socket != null) socket.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
