package org.example.client.controller;

public class SearchUserResult {
    private final String username;
    private final String relation; // quan hệ với user hiện tại
    private final String status; // trạng thái online

    public SearchUserResult(String username, String relation, String status) {
        this.username = username;
        this.relation = relation;
        this.status = status;
    }

    public String getUsername() { return username; }
    public String getRelation() { return relation; }
    public String getStatus() { return status; }
}
