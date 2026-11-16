package com.example.aiterminal.entity;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/** 持久化会话元数据，并级联管理属于该会话的消息。 */
@Entity
@Table(name = "chat_sessions")
public class ChatSession {

    @Id
    @Column(length = 36)
    private String id;

    /** 归属用户；早期（无账号体系）数据为 null，由首个注册用户接管。 */
    @Column(name = "owner_id", length = 36)
    private String ownerId;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(nullable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    @Version
    private long version;

    @OneToMany(mappedBy = "session", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("createdAt ASC, id ASC")
    private List<ChatMessage> messages = new ArrayList<>();

    protected ChatSession() {
    }

    public ChatSession(String id, String title, String ownerId, Instant now) {
        this.id = id;
        this.title = title;
        this.ownerId = ownerId;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public void rename(String title, Instant now) {
        this.title = title;
        this.updatedAt = now;
    }

    public void touch(Instant now) {
        this.updatedAt = now;
    }

    public void addMessage(ChatMessage message, Instant now) {
        messages.add(message);
        message.setSession(this);
        this.updatedAt = now;
    }

    public String getId() { return id; }
    public String getOwnerId() { return ownerId; }
    public String getTitle() { return title; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public List<ChatMessage> getMessages() { return messages; }
}
