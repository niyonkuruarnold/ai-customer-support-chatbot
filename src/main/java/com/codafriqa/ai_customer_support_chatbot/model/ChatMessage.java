package com.codafriqa.ai_customer_support_chatbot.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "chat_messages")
public class ChatMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long sessionId;

    @Column(nullable = false)
    private String sender; // USER, AI, AGENT

    @Column(columnDefinition = "TEXT", nullable = false)
    private String content;

    /**
     * Internal notes are agent-only and must never appear in the customer-facing chat.
     * When true, this message is broadcast only to agent WebSocket subscribers.
     */
    @Column(nullable = false)
    private boolean isInternal = false;

    private LocalDateTime timestamp = LocalDateTime.now();

    public ChatMessage() {}

    public ChatMessage(Long sessionId, String sender, String content) {
        this.sessionId = sessionId;
        this.sender = sender;
        this.content = content;
    }

    public ChatMessage(Long sessionId, String sender, String content, boolean isInternal) {
        this.sessionId = sessionId;
        this.sender = sender;
        this.content = content;
        this.isInternal = isInternal;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getSessionId() { return sessionId; }
    public void setSessionId(Long sessionId) { this.sessionId = sessionId; }

    public String getSender() { return sender; }
    public void setSender(String sender) { this.sender = sender; }

    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }

    public LocalDateTime getTimestamp() { return timestamp; }
    public void setTimestamp(LocalDateTime timestamp) { this.timestamp = timestamp; }

    public boolean isInternal() { return isInternal; }
    public void setInternal(boolean internal) { isInternal = internal; }
}