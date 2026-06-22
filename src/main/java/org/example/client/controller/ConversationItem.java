package org.example.client.controller;

public record ConversationItem(String key, String title, String type, String preview, String timestamp,
                               String avatar, String displayName) {
    @Override
    public String toString() {
        String prefix = "GROUP".equals(type) ? "[Nhóm] " : "";
        if (preview == null || preview.isBlank()) {
            return prefix + title;
        }
        return prefix + title + System.lineSeparator() + preview;
    }
}
