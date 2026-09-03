package com.codafriqa.ai_customer_support_chatbot.service;

import com.codafriqa.ai_customer_support_chatbot.model.AuditLog;
import com.codafriqa.ai_customer_support_chatbot.repository.AuditLogRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Service for audit event logging.
 * Provides methods to log various system events with proper metadata.
 * All log entries are immutable once created.
 */
@Service
public class AuditLogService {

    private static final Logger log = LoggerFactory.getLogger(AuditLogService.class);

    private final AuditLogRepository auditLogRepository;

    public AuditLogService(AuditLogRepository auditLogRepository) {
        this.auditLogRepository = auditLogRepository;
    }

    /**
     * Log a user login event.
     */
    @Transactional
    public void logLogin(Long actorId, String actorEmail, String ipAddress, boolean success) {
        try {
            AuditLog auditLog = AuditLog.login(actorId, actorEmail, ipAddress, success);
            auditLogRepository.save(auditLog);
            log.debug("Logged login event for user: {}", actorEmail);
        } catch (Exception e) {
            log.error("Failed to log login event for user {}: {}", actorEmail, e.getMessage());
        }
    }

    /**
     * Log a user logout event.
     */
    @Transactional
    public void logLogout(Long actorId, String actorEmail) {
        try {
            AuditLog auditLog = AuditLog.logout(actorId, actorEmail);
            auditLogRepository.save(auditLog);
            log.debug("Logged logout event for user: {}", actorEmail);
        } catch (Exception e) {
            log.error("Failed to log logout event for user {}: {}", actorEmail, e.getMessage());
        }
    }

    /**
     * Log a role update event.
     */
    @Transactional
    public void logRoleUpdate(Long actorId, String actorEmail, Long targetUserId,
                               String oldRole, String newRole) {
        try {
            AuditLog auditLog = AuditLog.roleUpdate(actorId, actorEmail, targetUserId, oldRole, newRole);
            auditLogRepository.save(auditLog);
            log.debug("Logged role update for user {}: {} -> {}", targetUserId, oldRole, newRole);
        } catch (Exception e) {
            log.error("Failed to log role update: {}", e.getMessage());
        }
    }

    /**
     * Log a ticket assignment event.
     */
    @Transactional
    public void logTicketAssign(Long actorId, String actorEmail, Long ticketId,
                                 String oldAssignee, String newAssignee) {
        try {
            AuditLog auditLog = AuditLog.ticketAssign(actorId, actorEmail, ticketId, oldAssignee, newAssignee);
            auditLogRepository.save(auditLog);
            log.debug("Logged ticket assignment for ticket {}: {}", ticketId, newAssignee);
        } catch (Exception e) {
            log.error("Failed to log ticket assignment: {}", e.getMessage());
        }
    }

    /**
     * Log a data export event.
     */
    @Transactional
    public void logDataExport(Long actorId, String actorEmail, String exportType,
                               String format, String filters) {
        try {
            AuditLog auditLog = AuditLog.dataExport(actorId, actorEmail, exportType, format, filters);
            auditLogRepository.save(auditLog);
            log.debug("Logged data export: {} in {} format", exportType, format);
        } catch (Exception e) {
            log.error("Failed to log data export: {}", e.getMessage());
        }
    }

    /**
     * Log a knowledge base publication event.
     */
    @Transactional
    public void logKnowledgePublish(Long actorId, String actorEmail, Long documentId,
                                     String action, String title) {
        try {
            AuditLog auditLog = AuditLog.knowledgePublish(actorId, actorEmail, documentId, action, title);
            auditLogRepository.save(auditLog);
            log.debug("Logged knowledge publish: {} - {}", action, title);
        } catch (Exception e) {
            log.error("Failed to log knowledge publish: {}", e.getMessage());
        }
    }

    /**
     * Log a custom audit event.
     */
    @Transactional
    public void logCustom(Long actorId, String actorEmail, String actionType,
                           String description, String resourceType, Long resourceId) {
        try {
            AuditLog auditLog = new AuditLog(actorEmail, actionType, description);
            auditLog.setActorId(actorId);
            auditLog.setResourceType(resourceType);
            auditLog.setResourceId(resourceId);
            auditLogRepository.save(auditLog);
            log.debug("Logged custom event: {}", actionType);
        } catch (Exception e) {
            log.error("Failed to log custom event: {}", e.getMessage());
        }
    }

    /**
     * Get all audit logs with pagination.
     */
    public Page<AuditLog> getAllLogs(Pageable pageable) {
        return auditLogRepository.findAllByOrderByTimestampDesc(pageable);
    }

    /**
     * Get audit logs by action type.
     */
    public Page<AuditLog> getLogsByActionType(String actionType, Pageable pageable) {
        return auditLogRepository.findByActionTypeOrderByTimestampDesc(actionType, pageable);
    }

    /**
     * Get audit logs by actor email.
     */
    public Page<AuditLog> getLogsByActor(String actorEmail, Pageable pageable) {
        return auditLogRepository.findByActorEmailOrderByTimestampDesc(actorEmail, pageable);
    }

    /**
     * Get audit logs within a date range.
     */
    public Page<AuditLog> getLogsByDateRange(LocalDateTime startDate, LocalDateTime endDate, Pageable pageable) {
        return auditLogRepository.findByTimestampBetweenOrderByTimestampDesc(startDate, endDate, pageable);
    }

    /**
     * Get filtered audit logs.
     */
    public Page<AuditLog> getFilteredLogs(String actionType, String actorEmail, String resourceType,
                                           LocalDateTime startDate, LocalDateTime endDate, Pageable pageable) {
        return auditLogRepository.findFiltered(actionType, actorEmail, resourceType, startDate, endDate, pageable);
    }

    /**
     * Get recent audit logs (last N days).
     */
    public List<AuditLog> getRecentLogs(int days) {
        return auditLogRepository.findRecentLogs(LocalDateTime.now().minusDays(days));
    }

    /**
     * Get audit logs for a specific resource.
     */
    public List<AuditLog> getLogsForResource(String resourceType, Long resourceId) {
        return auditLogRepository.findByResourceTypeAndResourceIdOrderByTimestampDesc(resourceType, resourceId);
    }

    /**
     * Count audit logs by action type.
     */
    public long countByActionType(String actionType) {
        return auditLogRepository.countByActionType(actionType);
    }

    /**
     * Count audit logs within a date range.
     */
    public long countByDateRange(LocalDateTime startDate, LocalDateTime endDate) {
        return auditLogRepository.countByTimestampBetween(startDate, endDate);
    }
}
