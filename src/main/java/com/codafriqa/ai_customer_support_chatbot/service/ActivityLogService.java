package com.codafriqa.ai_customer_support_chatbot.service;

import com.codafriqa.ai_customer_support_chatbot.model.TicketActivityLog;
import com.codafriqa.ai_customer_support_chatbot.repository.TicketActivityLogRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Service for automatic ticket activity logging.
 * Provides methods to log various ticket events with proper before/after state snapshots.
 *
 * All log entries are immutable once created — no update or delete operations are allowed.
 */
@Service
public class ActivityLogService {

    private static final Logger log = LoggerFactory.getLogger(ActivityLogService.class);

    private final TicketActivityLogRepository activityLogRepository;

    public ActivityLogService(TicketActivityLogRepository activityLogRepository) {
        this.activityLogRepository = activityLogRepository;
    }

    /**
     * Log a status change event.
     */
    @Transactional
    public void logStatusChange(Long ticketId, Long actorId, String actorRole,
                                 String oldStatus, String newStatus) {
        try {
            TicketActivityLog activityLog = TicketActivityLog.statusChange(
                ticketId, actorId, actorRole, oldStatus, newStatus);
            activityLogRepository.save(activityLog);
            log.debug("Logged status change for ticket {}: {} -> {}", ticketId, oldStatus, newStatus);
        } catch (Exception e) {
            log.error("Failed to log status change for ticket {}: {}", ticketId, e.getMessage());
        }
    }

    /**
     * Log a priority change event.
     */
    @Transactional
    public void logPriorityChange(Long ticketId, Long actorId, String actorRole,
                                   String oldPriority, String newPriority) {
        try {
            TicketActivityLog activityLog = TicketActivityLog.priorityChange(
                ticketId, actorId, actorRole, oldPriority, newPriority);
            activityLogRepository.save(activityLog);
            log.debug("Logged priority change for ticket {}: {} -> {}", ticketId, oldPriority, newPriority);
        } catch (Exception e) {
            log.error("Failed to log priority change for ticket {}: {}", ticketId, e.getMessage());
        }
    }

    /**
     * Log an assignment change event.
     */
    @Transactional
    public void logAssignment(Long ticketId, Long actorId, String actorRole,
                               String oldAssignee, String newAssignee) {
        try {
            TicketActivityLog activityLog = TicketActivityLog.assignment(
                ticketId, actorId, actorRole, oldAssignee, newAssignee);
            activityLogRepository.save(activityLog);
            log.debug("Logged assignment change for ticket {}: {} -> {}", ticketId, oldAssignee, newAssignee);
        } catch (Exception e) {
            log.error("Failed to log assignment change for ticket {}: {}", ticketId, e.getMessage());
        }
    }

    /**
     * Log a public reply event.
     */
    @Transactional
    public void logPublicReply(Long ticketId, Long actorId, String actorRole, String content) {
        try {
            TicketActivityLog activityLog = TicketActivityLog.publicReply(
                ticketId, actorId, actorRole, content);
            activityLogRepository.save(activityLog);
            log.debug("Logged public reply for ticket {}: {}", ticketId, actorRole);
        } catch (Exception e) {
            log.error("Failed to log public reply for ticket {}: {}", ticketId, e.getMessage());
        }
    }

    /**
     * Log an internal note event.
     */
    @Transactional
    public void logInternalNote(Long ticketId, Long actorId, String actorRole, String content) {
        try {
            TicketActivityLog activityLog = TicketActivityLog.internalNote(
                ticketId, actorId, actorRole, content);
            activityLogRepository.save(activityLog);
            log.debug("Logged internal note for ticket {}: {}", ticketId, actorRole);
        } catch (Exception e) {
            log.error("Failed to log internal note for ticket {}: {}", ticketId, e.getMessage());
        }
    }

    /**
     * Log a reply or internal note event (convenience method for backward compatibility).
     */
    @Transactional
    public void logReply(Long ticketId, Long actorId, String actorRole,
                          String content, boolean isInternal) {
        if (isInternal) {
            logInternalNote(ticketId, actorId, actorRole, content);
        } else {
            logPublicReply(ticketId, actorId, actorRole, content);
        }
    }

    /**
     * Log a ticket reopen event.
     */
    @Transactional
    public void logReopen(Long ticketId, Long actorId, String actorRole, String reason) {
        try {
            // Reopen is modeled as a STATUS_CHANGE from RESOLVED/CLOSED -> REOPENED
            TicketActivityLog activityLog = TicketActivityLog.statusChange(
                ticketId, actorId, actorRole, "RESOLVED", "REOPENED");
            activityLog.setNote("Ticket reopened" + (reason != null ? ": " + reason : ""));
            activityLogRepository.save(activityLog);
            log.debug("Logged reopen for ticket {}: {}", ticketId, actorRole);
        } catch (Exception e) {
            log.error("Failed to log reopen for ticket {}: {}", ticketId, e.getMessage());
        }
    }

    /**
     * Log a custom event.
     */
    @Transactional
    public void logCustom(Long ticketId, Long actorId, String actorRole, String actionType,
                           String note, boolean customerVisible) {
        try {
            TicketActivityLog activityLog = new TicketActivityLog(ticketId, actorId, actorRole, actionType);
            activityLog.setNote(note);
            activityLogRepository.save(activityLog);
            log.debug("Logged custom event for ticket {}: {}", ticketId, actionType);
        } catch (Exception e) {
            log.error("Failed to log custom event for ticket {}: {}", ticketId, e.getMessage());
        }
    }

    /**
     * Get all activity logs for a ticket (chronological order).
     */
    public List<TicketActivityLog> getTicketActivityLogs(Long ticketId) {
        return activityLogRepository.findByTicketIdOrderByTimestampAsc(ticketId);
    }

    /**
     * Get customer-visible activity logs for a ticket.
     */
    public List<TicketActivityLog> getCustomerVisibleLogs(Long ticketId) {
        return activityLogRepository.findByTicketIdAndCustomerVisibleTrueOrderByTimestampAsc(ticketId);
    }

    /**
     * Get activity logs by action type for a ticket.
     */
    public List<TicketActivityLog> getLogsByActionType(Long ticketId, String actionType) {
        return activityLogRepository.findByTicketIdAndActionTypeOrderByTimestampAsc(ticketId, actionType);
    }

    /**
     * Get the most recent activity log for a ticket.
     */
    public TicketActivityLog getMostRecentLog(Long ticketId) {
        return activityLogRepository.findFirstByTicketIdOrderByTimestampDesc(ticketId);
    }

    /**
     * Count activity logs for a ticket.
     */
    public long getActivityLogCount(Long ticketId) {
        return activityLogRepository.countByTicketId(ticketId);
    }
}
