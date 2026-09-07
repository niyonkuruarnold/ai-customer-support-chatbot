package com.codafriqa.ai_customer_support_chatbot.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * Immutable audit log for ticket activity.
 * Records every change to a ticket with before/after state snapshots.
 *
 * Columns: id, ticketId, actorId, actorRole, actionType, oldValue, newValue, note,
 *          timestamp, customerVisible.
 *
 * This entity is strictly read-only — no update or delete operations are allowed.
 */
@Entity
@Table(name = "ticket_activity_logs")
public class TicketActivityLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long ticketId;

    /** The user/agent who performed the action (null for system actions). */
    @Column(nullable = false)
    private Long actorId;

    /** Role of the actor: CUSTOMER, AGENT, ADMIN, SYSTEM. */
    @Column(nullable = false)
    private String actorRole;

    /**
     * Type of action performed.
     * STATUS_CHANGE, PRIORITY_CHANGE, ASSIGNMENT, PUBLIC_REPLY, INTERNAL_NOTE
     */
    @Column(nullable = false)
    private String actionType;

    /** Previous value before the change (null for creation actions). */
    @Column(columnDefinition = "TEXT")
    private String oldValue;

    /** New value after the change (null for deletion actions). */
    @Column(columnDefinition = "TEXT")
    private String newValue;

    /** Optional note or comment about the action. */
    @Column(columnDefinition = "TEXT")
    private String note;

    /** Timestamp when the action occurred (immutable once created). */
    @Column(nullable = false)
    private final LocalDateTime timestamp = LocalDateTime.now();

    /** Whether this action is visible to the customer. */
    @Column(nullable = false)
    private boolean customerVisible = true;

    public TicketActivityLog() {}

    public TicketActivityLog(Long ticketId, Long actorId, String actorRole, String actionType) {
        this.ticketId = ticketId;
        this.actorId = actorId;
        this.actorRole = actorRole;
        this.actionType = actionType;
    }

    // ---------------------------------------------------------------
    // Static factories for common action types
    // ---------------------------------------------------------------

    /** Static factory for status change logging. */
    public static TicketActivityLog statusChange(Long ticketId, Long actorId, String actorRole,
                                                  String oldValue, String newValue) {
        TicketActivityLog log = new TicketActivityLog(ticketId, actorId, actorRole, "STATUS_CHANGE");
        log.setOldValue(oldValue);
        log.setNewValue(newValue);
        log.setNote("Status changed from " + oldValue + " to " + newValue);
        return log;
    }

    /** Static factory for priority change logging. */
    public static TicketActivityLog priorityChange(Long ticketId, Long actorId, String actorRole,
                                                    String oldValue, String newValue) {
        TicketActivityLog log = new TicketActivityLog(ticketId, actorId, actorRole, "PRIORITY_CHANGE");
        log.setOldValue(oldValue);
        log.setNewValue(newValue);
        log.setNote("Priority changed from " + oldValue + " to " + newValue);
        return log;
    }

    /** Static factory for assignment logging. */
    public static TicketActivityLog assignment(Long ticketId, Long actorId, String actorRole,
                                               String oldValue, String newValue) {
        TicketActivityLog log = new TicketActivityLog(ticketId, actorId, actorRole, "ASSIGNMENT");
        log.setOldValue(oldValue);
        log.setNewValue(newValue);
        log.setNote("Ticket assigned to " + (newValue != null ? newValue : "unassigned"));
        return log;
    }

    /** Static factory for public reply logging. */
    public static TicketActivityLog publicReply(Long ticketId, Long actorId, String actorRole,
                                                String content) {
        TicketActivityLog log = new TicketActivityLog(ticketId, actorId, actorRole, "PUBLIC_REPLY");
        log.setNewValue(content);
        log.setNote("Reply sent to customer");
        log.setCustomerVisible(true);
        return log;
    }

    /** Static factory for internal note logging. */
    public static TicketActivityLog internalNote(Long ticketId, Long actorId, String actorRole,
                                                  String content) {
        TicketActivityLog log = new TicketActivityLog(ticketId, actorId, actorRole, "INTERNAL_NOTE");
        log.setNewValue(content);
        log.setNote("Internal note added");
        log.setCustomerVisible(false);
        return log;
    }

    // ---------------------------------------------------------------
    // Getters and Setters
    // ---------------------------------------------------------------

    public Long getId() { return id; }

    public Long getTicketId() { return ticketId; }
    public void setTicketId(Long ticketId) { this.ticketId = ticketId; }

    public Long getActorId() { return actorId; }
    public void setActorId(Long actorId) { this.actorId = actorId; }

    public String getActorRole() { return actorRole; }
    public void setActorRole(String actorRole) { this.actorRole = actorRole; }

    public String getActionType() { return actionType; }
    public void setActionType(String actionType) { this.actionType = actionType; }

    public String getOldValue() { return oldValue; }
    public void setOldValue(String oldValue) { this.oldValue = oldValue; }

    public String getNewValue() { return newValue; }
    public void setNewValue(String newValue) { this.newValue = newValue; }

    public String getNote() { return note; }
    public void setNote(String note) { this.note = note; }

    /** Timestamp is immutable — no setter provided. */
    public LocalDateTime getTimestamp() { return timestamp; }

    public boolean isCustomerVisible() { return customerVisible; }
    public void setCustomerVisible(boolean customerVisible) { this.customerVisible = customerVisible; }
}
