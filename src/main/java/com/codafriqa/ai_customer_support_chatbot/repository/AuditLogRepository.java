package com.codafriqa.ai_customer_support_chatbot.repository;

import com.codafriqa.ai_customer_support_chatbot.model.AuditLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Repository for AuditLog entity.
 * Intentionally read-only - no delete operations exposed.
 */
@Repository
public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {

    /**
     * Find all audit logs ordered by timestamp descending (newest first).
     */
    Page<AuditLog> findAllByOrderByTimestampDesc(Pageable pageable);

    /**
     * Find audit logs by action type.
     */
    Page<AuditLog> findByActionTypeOrderByTimestampDesc(String actionType, Pageable pageable);

    /**
     * Find audit logs by actor email.
     */
    Page<AuditLog> findByActorEmailOrderByTimestampDesc(String actorEmail, Pageable pageable);

    /**
     * Find audit logs within a date range.
     */
    Page<AuditLog> findByTimestampBetweenOrderByTimestampDesc(
            LocalDateTime startDate, LocalDateTime endDate, Pageable pageable);

    /**
     * Find audit logs by resource type.
     */
    Page<AuditLog> findByResourceTypeOrderByTimestampDesc(String resourceType, Pageable pageable);

    /**
     * Find audit logs by resource type and resource ID.
     */
    List<AuditLog> findByResourceTypeAndResourceIdOrderByTimestampDesc(String resourceType, Long resourceId);

    /**
     * Find audit logs by success status.
     */
    Page<AuditLog> findBySuccessOrderByTimestampDesc(boolean success, Pageable pageable);

    /**
     * Count audit logs by action type.
     */
    long countByActionType(String actionType);

    /**
     * Count audit logs within a date range.
     */
    long countByTimestampBetween(LocalDateTime startDate, LocalDateTime endDate);

    /**
     * Find recent audit logs (last N days).
     */
    @Query("SELECT a FROM AuditLog a WHERE a.timestamp >= :since ORDER BY a.timestamp DESC")
    List<AuditLog> findRecentLogs(@Param("since") LocalDateTime since);

    /**
     * Find audit logs with multiple filters.
     */
    @Query("SELECT a FROM AuditLog a WHERE " +
           "(:actionType IS NULL OR a.actionType = :actionType) AND " +
           "(:actorEmail IS NULL OR a.actorEmail = :actorEmail) AND " +
           "(:resourceType IS NULL OR a.resourceType = :resourceType) AND " +
           "(:startDate IS NULL OR a.timestamp >= :startDate) AND " +
           "(:endDate IS NULL OR a.timestamp <= :endDate) " +
           "ORDER BY a.timestamp DESC")
    Page<AuditLog> findFiltered(
            @Param("actionType") String actionType,
            @Param("actorEmail") String actorEmail,
            @Param("resourceType") String resourceType,
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate,
            Pageable pageable);
}
