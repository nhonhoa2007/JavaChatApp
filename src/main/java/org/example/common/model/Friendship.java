package org.example.common.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "Friendships")
public class Friendship {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "friend_id", nullable = false)
    private User friend;

    // trạng thái quan hệ bạn bè
    @Column(name = "status", length = 20, nullable = false)
    private String status;

    // lưu username của người đã chặn
    @Column(name = "blocked_by", length = 150)
    private String blockedBy;

    // lưu username của người đã tắt thông báo
    @Column(name = "muted_by", length = 150)
    private String mutedBy;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    public Friendship() {
        this.createdAt = LocalDateTime.now();
    }

    public Friendship(User user, User friend, String status) {
        this.user = user;
        this.friend = friend;
        this.status = status;
        this.createdAt = LocalDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public User getUser() {
        return user;
    }

    public void setUser(User user) {
        this.user = user;
    }

    public User getFriend() {
        return friend;
    }

    public void setFriend(User friend) {
        this.friend = friend;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getBlockedBy() {
        return blockedBy;
    }

    public void setBlockedBy(String blockedBy) {
        this.blockedBy = blockedBy;
    }

    public String getMutedBy() {
        return mutedBy;
    }

    public void setMutedBy(String mutedBy) {
        this.mutedBy = mutedBy;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
