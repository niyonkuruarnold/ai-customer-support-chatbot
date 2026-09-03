package com.codafriqa.ai_customer_support_chatbot.model;

import com.codafriqa.ai_customer_support_chatbot.service.SystemDataSyncListener;
import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "support_tickets")
@EntityListeners(SystemDataSyncListener.class)
public class SupportTicket {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Unique ticket reference for customer-facing display (e.g., TICKET-ABC123). */
    @Column(nullable = false, unique = true)
    private String ticketReference;

    @Column(nullable = false)
    private Long userId;

    @Column(nullable = false)
    private Long sessionId;

    @Column(nullable = false)
    private String subject;

    @Column(columnDefinition = "TEXT", nullable = false)
    private String description;

    /**
     * Ticket status lifecycle:
     * NEW -> OPEN -> PENDING_CUSTOMER/PENDING_INTERNAL -> RESOLVED -> CLOSED
     * Also supports REOPENED from CLOSED/RESOLVED.
     */
    @Column(nullable = false)
    private String status = "NEW"; // NEW, OPEN, PENDING_CUSTOMER, PENDING_INTERNAL, RESOLVED, CLOSED, REOPENED

    @Column(nullable = false)
    private String priority = "MEDIUM"; // LOW, MEDIUM, HIGH, URGENT

    /** Category for ticket classification (e.g., BILLING, TECHNICAL, GENERAL). */
    private String category = "GENERAL";

    /** Username of the agent who took over this ticket (null until takeover). */
    private String assignedAgent;

    /** AI-generated handoff summary (bullet points) produced on escalation. */
    @Column(columnDefinition = "TEXT")
    private String aiSummary;

    /** AI-inferred customer sentiment: positive | neutral | negative. */
    private String sentiment;

    /** Internal agent notes, never exposed to the customer-facing chat. */
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "support_ticket_notes", joinColumns = @JoinColumn(name = "ticket_id"))
    @OrderColumn(name = "note_order")
    @Column(name = "note")
    private List<String> internalNotes = new ArrayList<>();

    /** Timestamp when the ticket was last closed (for reopen tracking). */
    private LocalDateTime closedAt;

    /** Count of customer replies while in PENDING_CUSTOMER status. */
    @Column(nullable = false)
    private Integer customerReplyCount = 0;

    /** Timestamp when the ticket was last reopened. */
    private LocalDateTime reopenedAt;

    private LocalDateTime createdAt = LocalDateTime.now();
    private LocalDateTime updatedAt = LocalDateTime.now();

    public SupportTicket() {}

    public SupportTicket(Long userId, Long sessionId, String subject, String description) {
        this.userId = userId;
        this.sessionId = sessionId;
        this.subject = subject;
        this.description = description;
        this.ticketReference = generateTicketReference();
    }

    /** Generate a unique ticket reference like TICKET-ABC123. */
    private static String generateTicketReference() {
        return "TICKET-" + UUID.randomUUID().toString().substring(0, 6).toUpperCase();
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

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }

    public Long getSessionId() { return sessionId; }
    public void setSessionId(Long sessionId) { this.sessionId = sessionId; }

    public String getSubject() { return subject; }
    public void setSubject(String subject) { this.subject = subject; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getPriority() { return priority; }
    public void setPriority(String priority) { this.priority = priority; }

    public String getAssignedAgent() { return assignedAgent; }
    public void setAssignedAgent(String assignedAgent) { this.assignedAgent = assignedAgent; }

    public String getAiSummary() { return aiSummary; }
    public void setAiSummary(String aiSummary) { this.aiSummary = aiSummary; }

    public String getSentiment() { return sentiment; }
    public void setSentiment(String sentiment) { this.sentiment = sentiment; }

    public String getTicketReference() { return ticketReference; }
    public void setTicketReference(String ticketReference) { this.ticketReference = ticketReference; }

    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }

    public LocalDateTime getClosedAt() { return closedAt; }
    public void setClosedAt(LocalDateTime closedAt) { this.closedAt = closedAt; }

    public Integer getCustomerReplyCount() { return customerReplyCount; }
    public void setCustomerReplyCount(Integer customerReplyCount) { this.customerReplyCount = customerReplyCount; }

    public LocalDateTime getReopenedAt() { return reopenedAt; }
    public void setReopenedAt(LocalDateTime reopenedAt) { this.reopenedAt = reopenedAt; }

    public List<String> getInternalNotes() { return internalNotes; }
    public void setInternalNotes(List<String> internalNotes) { this.internalNotes = internalNotes; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}