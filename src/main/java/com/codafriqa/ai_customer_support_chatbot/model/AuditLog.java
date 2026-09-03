package com.codafriqa.ai_customer_support_chatbot.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * Immutable audit log entity for recording critical system actions.
 * Records authentication attempts, role updates, knowledge publications,
 * ticket reassignments, data exports, and other administrative actions.
 */
@Entity
@Table(name = "audit_logs")
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** User ID of the actor performing the action (null for system actions). */
    private Long actorId;

    /** Email or username of the actor for display purposes. */
    @Column(nullable = false)
    private String actorEmail;

    /** Type of action performed. */
    @Column(nullable = false)
    private String actionType; // LOGIN, LOGOUT, ROLE_UPDATE, TICKET_ASSIGN, DATA_EXPORT, etc.

    /** Detailed description of the action. */
    @Column(columnDefinition = "TEXT")
    private String description;

    /** IP address of the actor. */
    private String ipAddress;

    /** Request correlation ID for tracing. */
    private String correlationId;

    /** Target resource type (e.g., TICKET, USER, DOCUMENT). */
    private String resourceType;

    /** Target resource ID. */
    private Long resourceId;

    /** Additional metadata as JSON string. */
    @Column(columnDefinition = "TEXT")
    private String metadata;

    /** Whether the action was successful. */
    @Column(nullable = false)
    private boolean success = true;

    /** Timestamp when the action occurred (immutable once created). */
    @Column(nullable = false)
    private final LocalDateTime timestamp = LocalDateTime.now();

    public AuditLog() {}

    public AuditLog(String actorEmail, String actionType, String description) {
        this.actorEmail = actorEmail;
        this.actionType = actionType;
        this.description = description;
    }

    /** Static factory for login events. */
    public static AuditLog login(Long actorId, String actorEmail, String ipAddress, boolean success) {
        AuditLog log = new AuditLog(actorEmail, "LOGIN", "User login attempt");
        log.setActorId(actorId);
        log.setIpAddress(ipAddress);
        log.setSuccess(success);
        return log;
    }

    /** Static factory for logout events. */
    public static AuditLog logout(Long actorId, String actorEmail) {
        AuditLog log = new AuditLog(actorEmail, "LOGOUT", "User logout");
        log.setActorId(actorId);
        return log;
    }

    /** Static factory for role update events. */
    public static AuditLog roleUpdate(Long actorId, String actorEmail, Long targetUserId, 
                                       String oldRole, String newRole) {
        AuditLog log = new AuditLog(actorEmail, "ROLE_UPDATE", 
            "Role changed from " + oldRole + " to " + newRole);
        log.setActorId(actorId);
        log.setResourceType("USER");
        log.setResourceId(targetUserId);
        log.setMetadata("{\"oldRole\":\"" + oldRole + "\",\"newRole\":\"" + newRole + "\"}");
        return log;
    }

    /** Static factory for ticket assignment events. */
    public static AuditLog ticketAssign(Long actorId, String actorEmail, Long ticketId,
                                         String oldAssignee, String newAssignee) {
        AuditLog log = new AuditLog(actorEmail, "TICKET_ASSIGN", 
            "Ticket reassigned to " + (newAssignee != null ? newAssignee : "unassigned"));
        log.setActorId(actorId);
        log.setResourceType("TICKET");
        log.setResourceId(ticketId);
        log.setMetadata("{\"oldAssignee\":\"" + oldAssignee + "\",\"newAssignee\":\"" + newAssignee + "\"}");
        return log;
    }

    /** Static factory for data export events. */
    public static AuditLog dataExport(Long actorId, String actorEmail, String exportType, 
                                       String format, String filters) {
        AuditLog log = new AuditLog(actorEmail, "DATA_EXPORT", 
            "Exported " + exportType + " data in " + format + " format");
        log.setActorId(actorId);
        log.setResourceType("EXPORT");
        log.setMetadata("{\"exportType\":\"" + exportType + "\",\"format\":\"" + format + 
                        "\",\"filters\":\"" + filters + "\"}");
        return log;
    }

    /** Static factory for knowledge base publication events. */
    public static AuditLog knowledgePublish(Long actorId, String actorEmail, Long documentId,
                                             String action, String title) {
        AuditLog log = new AuditLog(actorEmail, "KNOWLEDGE_PUBLISH", 
            action + " document: " + title);
        log.setActorId(actorId);
        log.setResourceType("DOCUMENT");
        log.setResourceId(documentId);
        return log;
    }

    // Getters (no setters for timestamp - immutable)
    public Long getId() { return id; }
    public Long getActorId() { return actorId; }
    public String getActorEmail() { return actorEmail; }
    public String getActionType() { return actionType; }
    public String getDescription() { return description; }
    public String getIpAddress() { return ipAddress; }
    public String getCorrelationId() { return correlationId; }
    public String getResourceType() { return resourceType; }
    public Long getResourceId() { return resourceId; }
    public String getMetadata() { return metadata; }
    public boolean isSuccess() { return success; }
    public LocalDateTime getTimestamp() { return timestamp; }

    // Setters for mutable fields only
    public void setActorId(Long actorId) { this.actorId = actorId; }
    public void setActorEmail(String actorEmail) { this.actorEmail = actorEmail; }
    public void setActionType(String actionType) { this.actionType = actionType; }
    public void setDescription(String description) { this.description = description; }
    public void setIpAddress(String ipAddress) { this.ipAddress = ipAddress; }
    public void setCorrelationId(String correlationId) { this.correlationId = correlationId; }
    public void setResourceType(String resourceType) { this.resourceType = resourceType; }
    public void setResourceId(Long resourceId) { this.resourceId = resourceId; }
    public void setMetadata(String metadata) { this.metadata = metadata; }
    public void setSuccess(boolean success) { this.success = success; }
}
