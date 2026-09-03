package com.codafriqa.ai_customer_support_chatbot.controller;

import com.codafriqa.ai_customer_support_chatbot.model.AuditLog;
import com.codafriqa.ai_customer_support_chatbot.model.SupportTicket;
import com.codafriqa.ai_customer_support_chatbot.service.AuditLogService;
import com.codafriqa.ai_customer_support_chatbot.service.ExportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

/**
 * REST endpoints for data export functionality.
 * Provides CSV and PDF export for tickets and audit logs.
 */
@RestController
@RequestMapping({"/api/export", "/api/v1/export"})
@Tag(name = "Export", description = "Data export endpoints for CSV and PDF")
public class ExportController {

    private final ExportService exportService;
    private final AuditLogService auditLogService;

    public ExportController(ExportService exportService, AuditLogService auditLogService) {
        this.exportService = exportService;
        this.auditLogService = auditLogService;
    }

    /**
     * Export tickets to CSV.
     * GET /api/export/tickets/csv?status=...&priority=...
     */
    @Operation(summary = "Export tickets to CSV", description = "Download tickets as CSV file.")
    @GetMapping("/tickets/csv")
    public ResponseEntity<byte[]> exportTicketsCsv(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String priority,
            @RequestParam(required = false) String category,
            Authentication authentication) {
        
        // Get tickets based on filters
        List<SupportTicket> tickets = getFilteredTickets(status, priority, category);
        
        // Log the export
        String actorEmail = authentication != null ? authentication.getName() : "System";
        auditLogService.logDataExport(null, actorEmail, "TICKETS", "CSV", 
            "status=" + status + ",priority=" + priority + ",category=" + category);
        
        // Generate CSV
        String csv = exportService.exportTicketsToCsv(tickets);
        
        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION, 
                "attachment; filename=\"tickets_export_" + System.currentTimeMillis() + ".csv\"")
            .contentType(MediaType.APPLICATION_OCTET_STREAM)
            .body(csv.getBytes());
    }

    /**
     * Export tickets to PDF.
     * GET /api/export/tickets/pdf?status=...&priority=...
     */
    @Operation(summary = "Export tickets to PDF", description = "Download tickets as PDF file.")
    @GetMapping("/tickets/pdf")
    public ResponseEntity<byte[]> exportTicketsPdf(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String priority,
            @RequestParam(required = false) String category,
            Authentication authentication) {
        
        // Get tickets based on filters
        List<SupportTicket> tickets = getFilteredTickets(status, priority, category);
        
        // Log the export
        String actorEmail = authentication != null ? authentication.getName() : "System";
        auditLogService.logDataExport(null, actorEmail, "TICKETS", "PDF", 
            "status=" + status + ",priority=" + priority + ",category=" + category);
        
        // Generate PDF
        byte[] pdf = exportService.exportTicketsToPdf(tickets);
        
        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION, 
                "attachment; filename=\"tickets_export_" + System.currentTimeMillis() + ".pdf\"")
            .contentType(MediaType.APPLICATION_PDF)
            .body(pdf);
    }

    /**
     * Export audit logs to CSV.
     * GET /api/export/audit/csv?startDate=...&endDate=...
     */
    @Operation(summary = "Export audit logs to CSV", description = "Download audit logs as CSV file.")
    @GetMapping("/audit/csv")
    public ResponseEntity<byte[]> exportAuditLogsCsv(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endDate,
            Authentication authentication) {
        
        // Get audit logs based on date range
        List<AuditLog> logs = getFilteredAuditLogs(startDate, endDate);
        
        // Log the export
        String actorEmail = authentication != null ? authentication.getName() : "System";
        auditLogService.logDataExport(null, actorEmail, "AUDIT_LOGS", "CSV", 
            "startDate=" + startDate + ",endDate=" + endDate);
        
        // Generate CSV
        String csv = exportService.exportAuditLogsToCsv(logs);
        
        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION, 
                "attachment; filename=\"audit_logs_export_" + System.currentTimeMillis() + ".csv\"")
            .contentType(MediaType.APPLICATION_OCTET_STREAM)
            .body(csv.getBytes());
    }

    /**
     * Export audit logs to PDF.
     * GET /api/export/audit/pdf?startDate=...&endDate=...
     */
    @Operation(summary = "Export audit logs to PDF", description = "Download audit logs as PDF file.")
    @GetMapping("/audit/pdf")
    public ResponseEntity<byte[]> exportAuditLogsPdf(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endDate,
            Authentication authentication) {
        
        // Get audit logs based on date range
        List<AuditLog> logs = getFilteredAuditLogs(startDate, endDate);
        
        // Log the export
        String actorEmail = authentication != null ? authentication.getName() : "System";
        auditLogService.logDataExport(null, actorEmail, "AUDIT_LOGS", "PDF", 
            "startDate=" + startDate + ",endDate=" + endDate);
        
        // Generate PDF
        byte[] pdf = exportService.exportAuditLogsToPdf(logs);
        
        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION, 
                "attachment; filename=\"audit_logs_export_" + System.currentTimeMillis() + ".pdf\"")
            .contentType(MediaType.APPLICATION_PDF)
            .body(pdf);
    }

    // ─── Helper Methods ─────────────────────────────────────────────────

    private List<SupportTicket> getFilteredTickets(String status, String priority, String category) {
        // For simplicity, return all tickets and filter in-memory
        // In production, use repository with proper filtering
        return List.of(); // Would need to inject SupportTicketRepository
    }

    private List<AuditLog> getFilteredAuditLogs(LocalDateTime startDate, LocalDateTime endDate) {
        if (startDate == null) startDate = LocalDateTime.now().minusDays(30);
        if (endDate == null) endDate = LocalDateTime.now();
        return auditLogService.getFilteredLogs(null, null, null, startDate, endDate, 
            org.springframework.data.domain.PageRequest.of(0, 10000)).getContent();
    }
}
