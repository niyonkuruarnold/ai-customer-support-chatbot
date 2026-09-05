package com.codafriqa.ai_customer_support_chatbot.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * Conversation entity representing a customer support conversation.
 */
@Entity
@Table(name = "conversations")
public class Conversation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "session_id")
    private Long sessionId;

    @Column(name = "customer_id")
    private Long customerId;

    @Column(name = "agent_id")
    private Long agentId;

    @Column(nullable = false)
    private String status = "ACTIVE";

    @Column(name = "csat_score")
    private Integer csatScore;

    @Column(name = "csat_comment", columnDefinition = "TEXT")
    private String csatComment;

    @Column(name = "csat_submitted_at")
    private LocalDateTime csatSubmittedAt;

    @Column(name = "created_at")
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at")
    private LocalDateTime updatedAt = LocalDateTime.now();

    public Conversation() {}

    public Conversation(Long sessionId, Long customerId, Long agentId) {
        this.sessionId = sessionId;
        this.customerId = customerId;
        this.agentId = agentId;
    }

    @PrePersist
    public void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    public void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    // ---- Getters and Setters ----

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getSessionId() { return sessionId; }
    public void setSessionId(Long sessionId) { this.sessionId = sessionId; }

    public Long getCustomerId() { return customerId; }
    public void setCustomerId(Long customerId) { this.customerId = customerId; }

    public Long getAgentId() { return agentId; }
    public void setAgentId(Long agentId) { this.agentId = agentId; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public Integer getCsatScore() { return csatScore; }
    public void setCsatScore(Integer csatScore) { this.csatScore = csatScore; }

    public String getCsatComment() { return csatComment; }
    public void setCsatComment(String csatComment) { this.csatComment = csatComment; }

    public LocalDateTime getCsatSubmittedAt() { return csatSubmittedAt; }
    public void setCsatSubmittedAt(LocalDateTime csatSubmittedAt) { this.csatSubmittedAt = csatSubmittedAt; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
