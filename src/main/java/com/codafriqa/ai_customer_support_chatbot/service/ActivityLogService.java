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
 * All log entries are immutable once created - no update or delete operations are allowed.
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
    public void logStatusChange(Long ticketId, Long actorId, String actorName, 
                                 String oldStatus, String newStatus) {
        try {
            TicketActivityLog activityLog = TicketActivityLog.statusChange(
                ticketId, actorId, actorName, oldStatus, newStatus);
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
    public void logPriorityChange(Long ticketId, Long actorId, String actorName,
                                   String oldPriority, String newPriority) {
        try {
            TicketActivityLog activityLog = TicketActivityLog.priorityChange(
                ticketId, actorId, actorName, oldPriority, newPriority);
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
    public void logAssignment(Long ticketId, Long actorId, String actorName,
                               String oldAssignee, String newAssignee) {
        try {
            TicketActivityLog activityLog = TicketActivityLog.assignment(
                ticketId, actorId, actorName, oldAssignee, newAssignee);
            activityLogRepository.save(activityLog);
            log.debug("Logged assignment change for ticket {}: {} -> {}", ticketId, oldAssignee, newAssignee);
        } catch (Exception e) {
            log.error("Failed to log assignment change for ticket {}: {}", ticketId, e.getMessage());
        }
    }

    /**
     * Log a reply or internal note event.
     */
    @Transactional
    public void logReply(Long ticketId, Long actorId, String actorName, 
                          String content, boolean isInternal) {
        try {
            TicketActivityLog activityLog = TicketActivityLog.reply(
                ticketId, actorId, actorName, content, isInternal);
            activityLogRepository.save(activityLog);
            log.debug("Logged {} for ticket {}: {}", isInternal ? "note" : "reply", ticketId, actorName);
        } catch (Exception e) {
            log.error("Failed to log reply for ticket {}: {}", ticketId, e.getMessage());
        }
    }

    /**
     * Log a ticket reopen event.
     */
    @Transactional
    public void logReopen(Long ticketId, Long actorId, String actorName, String reason) {
        try {
            TicketActivityLog activityLog = TicketActivityLog.reopen(
                ticketId, actorId, actorName, reason);
            activityLogRepository.save(activityLog);
            log.debug("Logged reopen for ticket {}: {}", ticketId, actorName);
        } catch (Exception e) {
            log.error("Failed to log reopen for ticket {}: {}", ticketId, e.getMessage());
        }
    }

    /**
     * Log a custom event.
     */
    @Transactional
    public void logCustom(Long ticketId, Long actorId, String actorName, String actionType,
                           String description, boolean customerVisible) {
        try {
            TicketActivityLog activityLog = new TicketActivityLog(ticketId, actorId, actorName, actionType);
            activityLog.setDescription(description);
            activityLog.setCustomerVisible(customerVisible);
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
