package com.codafriqa.ai_customer_support_chatbot.controller;

import com.codafriqa.ai_customer_support_chatbot.model.AuditLog;
import com.codafriqa.ai_customer_support_chatbot.service.AuditLogService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * REST endpoints for the Audit Log admin interface.
 * Provides searchable, read-only access to audit logs.
 */
@RestController
@RequestMapping({"/api/audit", "/api/v1/audit"})
@Tag(name = "Audit Logs", description = "Audit log management endpoints")
public class AuditLogController {

    private final AuditLogService auditLogService;

    public AuditLogController(AuditLogService auditLogService) {
        this.auditLogService = auditLogService;
    }

    /**
     * Get all audit logs with pagination.
     * GET /api/audit?page=0&size=20
     */
    @Operation(summary = "Get audit logs", description = "Get paginated audit logs.")
    @GetMapping
    public ResponseEntity<Page<AuditLog>> getAuditLogs(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        
        Pageable pageable = PageRequest.of(page, size);
        return ResponseEntity.ok(auditLogService.getAllLogs(pageable));
    }

    /**
     * Get filtered audit logs.
     * GET /api/audit/filter?actionType=...&actorEmail=...&resourceType=...&startDate=...&endDate=...
     */
    @Operation(summary = "Get filtered audit logs", description = "Get audit logs with multiple filters.")
    @GetMapping("/filter")
    public ResponseEntity<Page<AuditLog>> getFilteredLogs(
            @RequestParam(required = false) String actionType,
            @RequestParam(required = false) String actorEmail,
            @RequestParam(required = false) String resourceType,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endDate,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        
        Pageable pageable = PageRequest.of(page, size);
        return ResponseEntity.ok(auditLogService.getFilteredLogs(
            actionType, actorEmail, resourceType, startDate, endDate, pageable));
    }

    /**
     * Get audit logs by action type.
     * GET /api/audit/action/{actionType}?page=0&size=20
     */
    @Operation(summary = "Get logs by action type", description = "Get audit logs filtered by action type.")
    @GetMapping("/action/{actionType}")
    public ResponseEntity<Page<AuditLog>> getLogsByActionType(
            @PathVariable String actionType,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        
        Pageable pageable = PageRequest.of(page, size);
        return ResponseEntity.ok(auditLogService.getLogsByActionType(actionType, pageable));
    }

    /**
     * Get audit logs by actor.
     * GET /api/audit/actor/{actorEmail}?page=0&size=20
     */
    @Operation(summary = "Get logs by actor", description = "Get audit logs filtered by actor email.")
    @GetMapping("/actor/{actorEmail}")
    public ResponseEntity<Page<AuditLog>> getLogsByActor(
            @PathVariable String actorEmail,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        
        Pageable pageable = PageRequest.of(page, size);
        return ResponseEntity.ok(auditLogService.getLogsByActor(actorEmail, pageable));
    }

    /**
     * Get audit logs for a specific resource.
     * GET /api/audit/resource/{resourceType}/{resourceId}
     */
    @Operation(summary = "Get logs for resource", description = "Get audit logs for a specific resource.")
    @GetMapping("/resource/{resourceType}/{resourceId}")
    public ResponseEntity<List<AuditLog>> getLogsForResource(
            @PathVariable String resourceType,
            @PathVariable Long resourceId) {
        
        return ResponseEntity.ok(auditLogService.getLogsForResource(resourceType, resourceId));
    }

    /**
     * Get recent audit logs.
     * GET /api/audit/recent?days=7
     */
    @Operation(summary = "Get recent logs", description = "Get audit logs from the last N days.")
    @GetMapping("/recent")
    public ResponseEntity<List<AuditLog>> getRecentLogs(
            @RequestParam(defaultValue = "7") int days) {
        
        return ResponseEntity.ok(auditLogService.getRecentLogs(days));
    }

    /**
     * Get audit log statistics.
     * GET /api/audit/stats
     */
    @Operation(summary = "Get audit log statistics", description = "Get statistics about audit log activity.")
    @GetMapping("/stats")
    public ResponseEntity<Map<String, Object>> getStats() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime last24h = now.minusHours(24);
        LocalDateTime last7d = now.minusDays(7);
        LocalDateTime last30d = now.minusDays(30);
        
        return ResponseEntity.ok(Map.of(
            "totalLogs", auditLogService.countByDateRange(LocalDateTime.of(2020, 1, 1, 0, 0), now),
            "last24Hours", auditLogService.countByDateRange(last24h, now),
            "last7Days", auditLogService.countByDateRange(last7d, now),
            "last30Days", auditLogService.countByDateRange(last30d, now),
            "loginEvents", auditLogService.countByActionType("LOGIN"),
            "exportEvents", auditLogService.countByActionType("DATA_EXPORT"),
            "ticketAssignments", auditLogService.countByActionType("TICKET_ASSIGN"),
            "roleUpdates", auditLogService.countByActionType("ROLE_UPDATE")
        ));
    }

    /**
     * Get available action types for filtering.
     * GET /api/audit/action-types
     */
    @Operation(summary = "Get action types", description = "Get list of available action types for filtering.")
    @GetMapping("/action-types")
    public ResponseEntity<List<String>> getActionTypes() {
        return ResponseEntity.ok(List.of(
            "LOGIN", "LOGOUT", "ROLE_UPDATE", "TICKET_ASSIGN", 
            "DATA_EXPORT", "KNOWLEDGE_PUBLISH", "CUSTOM"
        ));
    }
}
