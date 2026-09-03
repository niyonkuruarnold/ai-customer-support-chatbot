package com.codafriqa.ai_customer_support_chatbot.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * Immutable audit log for ticket activity.
 * Records every change to a ticket with before/after state snapshots.
 * 
 * This entity is strictly read-only - no update or delete operations are allowed.
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

    /** Username or identifier of the actor for display purposes. */
    @Column(nullable = false)
    private String actorName;

    /** Type of action performed. */
    @Column(nullable = false)
    private String actionType; // STATUS_CHANGE, PRIORITY_CHANGE, ASSIGNMENT, REPLY, NOTE, REOPEN

    /** Previous value before the change (null for creation actions). */
    @Column(columnDefinition = "TEXT")
    private String previousValue;

    /** New value after the change (null for deletion actions). */
    @Column(columnDefinition = "TEXT")
    private String newValue;

    /** Optional description or comment about the action. */
    @Column(columnDefinition = "TEXT")
    private String description;

    /** Timestamp when the action occurred (immutable once created). */
    @Column(nullable = false)
    private final LocalDateTime timestamp = LocalDateTime.now();

    /** Whether this action is visible to the customer. */
    @Column(nullable = false)
    private boolean customerVisible = true;

    public TicketActivityLog() {}

    public TicketActivityLog(Long ticketId, Long actorId, String actorName, String actionType) {
        this.ticketId = ticketId;
        this.actorId = actorId;
        this.actorName = actorName;
        this.actionType = actionType;
    }

    /** Static factory for status change logging. */
    public static TicketActivityLog statusChange(Long ticketId, Long actorId, String actorName, 
                                                  String oldStatus, String newStatus) {
        TicketActivityLog log = new TicketActivityLog(ticketId, actorId, actorName, "STATUS_CHANGE");
        log.setPreviousValue(oldStatus);
        log.setNewValue(newStatus);
        log.setDescription("Status changed from " + oldStatus + " to " + newStatus);
        return log;
    }

    /** Static factory for priority change logging. */
    public static TicketActivityLog priorityChange(Long ticketId, Long actorId, String actorName,
                                                    String oldPriority, String newPriority) {
        TicketActivityLog log = new TicketActivityLog(ticketId, actorId, actorName, "PRIORITY_CHANGE");
        log.setPreviousValue(oldPriority);
        log.setNewValue(newPriority);
        log.setDescription("Priority changed from " + oldPriority + " to " + newPriority);
        return log;
    }

    /** Static factory for assignment logging. */
    public static TicketActivityLog assignment(Long ticketId, Long actorId, String actorName,
                                               String oldAssignee, String newAssignee) {
        TicketActivityLog log = new TicketActivityLog(ticketId, actorId, actorName, "ASSIGNMENT");
        log.setPreviousValue(oldAssignee);
        log.setNewValue(newAssignee);
        log.setDescription("Ticket assigned to " + (newAssignee != null ? newAssignee : "unassigned"));
        return log;
    }

    /** Static factory for reply logging. */
    public static TicketActivityLog reply(Long ticketId, Long actorId, String actorName, 
                                          String content, boolean isInternal) {
        TicketActivityLog log = new TicketActivityLog(ticketId, actorId, actorName, 
                                                       isInternal ? "NOTE" : "REPLY");
        log.setNewValue(content);
        log.setCustomerVisible(!isInternal);
        log.setDescription(isInternal ? "Internal note added" : "Reply sent to customer");
        return log;
    }

    /** Static factory for reopen logging. */
    public static TicketActivityLog reopen(Long ticketId, Long actorId, String actorName, String reason) {
        TicketActivityLog log = new TicketActivityLog(ticketId, actorId, actorName, "REOPEN");
        log.setNewValue(reason);
        log.setDescription("Ticket reopened: " + (reason != null ? reason : "No reason provided"));
        return log;
    }

    // Getters (no setters for timestamp - immutable)
    public Long getId() { return id; }
    public Long getTicketId() { return ticketId; }
    public Long getActorId() { return actorId; }
    public String getActorName() { return actorName; }
    public String getActionType() { return actionType; }
    public String getPreviousValue() { return previousValue; }
    public String getNewValue() { return newValue; }
    public String getDescription() { return description; }
    public LocalDateTime getTimestamp() { return timestamp; }
    public boolean isCustomerVisible() { return customerVisible; }

    // Setters for mutable fields only
    public void setTicketId(Long ticketId) { this.ticketId = ticketId; }
    public void setActorId(Long actorId) { this.actorId = actorId; }
    public void setActorName(String actorName) { this.actorName = actorName; }
    public void setActionType(String actionType) { this.actionType = actionType; }
    public void setPreviousValue(String previousValue) { this.previousValue = previousValue; }
    public void setNewValue(String newValue) { this.newValue = newValue; }
    public void setDescription(String description) { this.description = description; }
    public void setCustomerVisible(boolean customerVisible) { this.customerVisible = customerVisible; }
}
