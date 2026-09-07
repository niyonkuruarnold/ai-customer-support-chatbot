package com.codafriqa.ai_customer_support_chatbot.controller;

import com.codafriqa.ai_customer_support_chatbot.config.AuditAction;
import com.codafriqa.ai_customer_support_chatbot.dto.AnalyticsMetricsDto;
import com.codafriqa.ai_customer_support_chatbot.service.AnalyticsService;
import com.codafriqa.ai_customer_support_chatbot.service.ExportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;

/**
 * REST endpoints for analytics data export (Sections 6.9 & 6.10).
 * Provides CSV and PDF download of the performance summary report.
 */
@RestController
@RequestMapping("/api/v1/analytics/export")
@Tag(name = "Analytics Export", description = "CSV and PDF export of analytics performance summary")
public class AnalyticsExportController {

    private final AnalyticsService analyticsService;
    private final ExportService exportService;

    public AnalyticsExportController(AnalyticsService analyticsService,
                                     ExportService exportService) {
        this.analyticsService = analyticsService;
        this.exportService = exportService;
    }

    /**
     * Export analytics performance summary as a downloadable CSV.
     * GET /api/v1/analytics/export/csv?startDate=...&endDate=...
     */
    @Operation(summary = "Export analytics CSV",
               description = "Download operational metrics (containment rate, escalation rate, CSAT, FRT) as CSV.")
    @AuditAction(action = "DATA_EXPORT", resourceType = "ANALYTICS", description = "Analytics CSV export")
    @GetMapping("/csv")
    public ResponseEntity<byte[]> exportCsv(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endDate) {

        if (startDate == null) startDate = LocalDateTime.now().minusDays(30);
        if (endDate == null) endDate = LocalDateTime.now();

        AnalyticsMetricsDto metrics = analyticsService.getOperationalMetrics(startDate, endDate);

        String csv = exportService.exportAnalyticsToCsv(
                metrics.aiContainmentRate(),
                metrics.humanEscalationRate(),
                metrics.averageCsatRating(),
                metrics.averageFirstResponseTimeMinutes(),
                metrics.totalConversations(),
                metrics.escalatedConversations()
        );

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"analytics_report_" + System.currentTimeMillis() + ".csv\"")
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(csv.getBytes());
    }

    /**
     * Export analytics performance summary as a styled PDF report.
     * GET /api/v1/analytics/export/pdf?startDate=...&endDate=...
     */
    @Operation(summary = "Export analytics PDF",
               description = "Download a styled PDF performance summary report.")
    @AuditAction(action = "DATA_EXPORT", resourceType = "ANALYTICS", description = "Analytics PDF export")
    @GetMapping("/pdf")
    public ResponseEntity<byte[]> exportPdf(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endDate) {

        if (startDate == null) startDate = LocalDateTime.now().minusDays(30);
        if (endDate == null) endDate = LocalDateTime.now();

        AnalyticsMetricsDto metrics = analyticsService.getOperationalMetrics(startDate, endDate);

        byte[] pdf = exportService.exportAnalyticsToPdf(metrics);

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"analytics_report_" + System.currentTimeMillis() + ".pdf\"")
                .contentType(MediaType.APPLICATION_PDF)
                .body(pdf);
    }
}
