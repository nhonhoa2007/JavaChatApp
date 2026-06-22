package org.example.client.controller;

public class AdminUserRow {
    private final Long id;
    private final String username;
    private final String fullName;
    private final String role;
    private final String status;
    private final boolean locked;
    private final String lastSeen;

    public AdminUserRow(Long id, String username, String fullName, String role, String status, boolean locked, String lastSeen) {
        this.id = id;
        this.username = username;
        this.fullName = fullName;
        this.role = role;
        this.status = status;
        this.locked = locked;
        this.lastSeen = lastSeen;
    }

    public Long getId() { return id; }
    public String getUsername() { return username; }
    public String getFullName() { return fullName; }
    public String getRole() { return role; }
    public String getStatus() { return status; }
    public boolean isLocked() { return locked; }
    public String getLastSeen() { return lastSeen; }
}
