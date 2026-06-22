package org.example.client.controller;

public record MessageData(Long messageId, String type, String content, boolean isMe, String senderDisplay,
                          String senderUsername, String senderAvatar, String filename) {}
