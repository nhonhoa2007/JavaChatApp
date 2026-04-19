package org.example.common.network;

import com.google.gson.Gson;

public class Packet {
    private String type;
    private String payload;

    public Packet(String type, String payload) {
        this.type = type;
        this.payload = payload;
    }

    public String getType() { return type; }
    public String getPayload() { return payload; }

    public String toJson() {
        return new Gson().toJson(this);
    }

    public static Packet fromJson(String json) {
        return new Gson().fromJson(json, Packet.class);
    }
}
