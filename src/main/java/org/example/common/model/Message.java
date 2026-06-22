package org.example.common.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import org.example.common.util.MessageEncryptionConverter;

@Entity
@Table(name = "Messages")
public class Message {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;


    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "sender_id", nullable = false)
    private User sender;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "receiver_id")
    private User receiver;

    // nhóm nhận tin, null nếu là tin riêng
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "group_id")
    private GroupChat groupChat;

    // loại nội dung tin nhắn
    @Column(name = "message_type", length = 20, nullable = false)
    private String messageType;

    // lưu nội dung chữ hoặc dữ liệu file
    @Column(name = "content", columnDefinition = "NVARCHAR(MAX)", nullable = false)
    @Convert(converter = MessageEncryptionConverter.class)
    private String content;

    @Column(name = "file_name", length = 255)
    @Convert(converter = MessageEncryptionConverter.class)
    private String fileName;

    @Column(name = "is_read")
    private boolean isRead;

    @Column(name = "is_recalled")
    private boolean isRecalled;

    @Column(name = "is_edited")
    private boolean isEdited;

    @Column(name = "sent_at", nullable = false)
    private LocalDateTime sentAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    public Message() {
        this.sentAt = LocalDateTime.now();
        this.isRead = false;
        this.isRecalled = false;
        this.isEdited = false;
    }

    public Message(User sender, User receiver, String messageType, String content) {
        this.sender = sender;
        this.receiver = receiver;
        this.groupChat = null;
        this.messageType = messageType;
        this.content = content;
        this.sentAt = LocalDateTime.now();
        this.isRead = false;
        this.isRecalled = false;
        this.isEdited = false;
    }

    public Message(User sender, GroupChat groupChat, String messageType, String content) {
        this.sender = sender;
        this.receiver = null;
        this.groupChat = groupChat;
        this.messageType = messageType;
        this.content = content;
        this.sentAt = LocalDateTime.now();
        this.isRead = false;
        this.isRecalled = false;
        this.isEdited = false;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public User getSender() {
        return sender;
    }

    public void setSender(User sender) {
        this.sender = sender;
    }

    public User getReceiver() {
        return receiver;
    }

    public void setReceiver(User receiver) {
        this.receiver = receiver;
    }

    public GroupChat getGroupChat() {
        return groupChat;
    }

    public void setGroupChat(GroupChat groupChat) {
        this.groupChat = groupChat;
    }

    public String getMessageType() {
        return messageType;
    }

    public void setMessageType(String messageType) {
        this.messageType = messageType;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    public boolean isRead() {
        return isRead;
    }

    public void setRead(boolean read) {
        isRead = read;
    }

    public boolean isRecalled() {
        return isRecalled;
    }

    public void setRecalled(boolean recalled) {
        isRecalled = recalled;
    }

    public boolean isEdited() {
        return isEdited;
    }

    public void setEdited(boolean edited) {
        isEdited = edited;
    }

    public LocalDateTime getSentAt() {
        return sentAt;
    }

    public void setSentAt(LocalDateTime sentAt) {
        this.sentAt = sentAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}
