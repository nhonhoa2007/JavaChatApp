package org.example.common.model;

import jakarta.persistence.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "GroupMembers",
		uniqueConstraints = @UniqueConstraint(columnNames = {"group_id", "user_id"}))
public class GroupMember {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "id")
	private Long id;

	@ManyToOne(fetch = FetchType.EAGER)
	@JoinColumn(name = "group_id", nullable = false)
	private GroupChat groupChat;

	@ManyToOne(fetch = FetchType.EAGER)
	@JoinColumn(name = "user_id", nullable = false)
	private User user;

	@Column(name = "joined_at", nullable = false)
	private LocalDateTime joinedAt;

	@Column(name = "is_muted")
	private Boolean muted;

	public GroupMember() {
		this.joinedAt = LocalDateTime.now();
		this.muted = false;
	}

	public GroupMember(GroupChat groupChat, User user) {
		this.groupChat = groupChat;
		this.user = user;
		this.joinedAt = LocalDateTime.now();
		this.muted = false;
	}

	public Long getId() {
		return id;
	}

	public void setId(Long id) {
		this.id = id;
	}

	public GroupChat getGroupChat() {
		return groupChat;
	}

	public void setGroupChat(GroupChat groupChat) {
		this.groupChat = groupChat;
	}

	public User getUser() {
		return user;
	}

	public void setUser(User user) {
		this.user = user;
	}

	public LocalDateTime getJoinedAt() {
		return joinedAt;
	}

	public void setJoinedAt(LocalDateTime joinedAt) {
		this.joinedAt = joinedAt;
	}

	public boolean isMuted() {
		return Boolean.TRUE.equals(muted);
	}

	public void setMuted(boolean muted) {
		this.muted = muted;
	}
}

