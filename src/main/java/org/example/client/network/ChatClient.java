package org.example.client.network;

import org.example.common.network.Packet;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public class ChatClient {
    private Socket socket;
    private PrintWriter out;
    private BufferedReader in;
    private Thread listenerThread;
    private final List<Consumer<Packet>> listeners = new ArrayList<>();
    private String serverHost; // lưu host server cho relay

    public boolean connect(String host, int port) {
        try {
            this.serverHost = host;
            socket = new Socket(host, port);
            out = new PrintWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8), true);
            in = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));

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
                if(packet==null) continue;
                List<Consumer<Packet>> snapshot;
                synchronized (this){
                    snapshot = new ArrayList<>(listeners);
                }
                for(Consumer<Packet> listener: snapshot){
                    listener.accept(packet);
                }
            }
        } catch (IOException e) {
            System.out.println("Disconnected from server.");
        }
    }
    public synchronized void addListener(Consumer<Packet> listener) {
        if(!listeners.contains(listener)) listeners.add(listener);
    }
    public synchronized void removeListener(Consumer<Packet> listener) {
        listeners.remove(listener);
    }



    public synchronized void setOnPacketReceived(Consumer<Packet> callback) {
        listeners.clear();
        if(callback!=null) listeners.add(callback);
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

    public String getServerHost() {
        return serverHost;
    }
}
