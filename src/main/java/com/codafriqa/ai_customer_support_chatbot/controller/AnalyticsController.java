package com.codafriqa.ai_customer_support_chatbot.controller;

import com.codafriqa.ai_customer_support_chatbot.dto.AnalyticsMetricsDto;
import com.codafriqa.ai_customer_support_chatbot.service.AnalyticsService;
import com.codafriqa.ai_customer_support_chatbot.util.DateTimeParams;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * REST endpoints for analytics and reporting.
 * Provides metrics aggregation for the Support Manager dashboard.
 *
 * Date range params are parsed leniently (yyyy-MM-dd or ISO date-time) and
 * default to the last 30 days, so an absent or partial range still returns
 * HTTP 200 with zeroed metrics instead of a binding error.
 */
@RestController
@RequestMapping({"/api/analytics", "/api/v1/analytics"})
@Tag(name = "Analytics", description = "Analytics and reporting endpoints")
public class AnalyticsController {

    private final AnalyticsService analyticsService;

    public AnalyticsController(AnalyticsService analyticsService) {
        this.analyticsService = analyticsService;
    }

    /**
     * Get Section 6.9 operational metrics.
     * GET /api/v1/analytics/metrics?startDate=...&endDate=...
     */
    @Operation(summary = "Get operational metrics",
               description = "Returns AI Containment Rate, Human Escalation Rate, Average CSAT, and Average FRT.")
    @GetMapping("/metrics")
    public ResponseEntity<AnalyticsMetricsDto> getMetrics(
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate) {

        LocalDateTime start = DateTimeParams.parseStart(startDate, LocalDateTime.now().minusDays(30));
        LocalDateTime end = DateTimeParams.parseEnd(endDate, LocalDateTime.now());

        return ResponseEntity.ok(analyticsService.getOperationalMetrics(start, end));
    }

    /**
     * Get comprehensive dashboard metrics.
     * GET /api/analytics/dashboard?startDate=...&endDate=...
     */
    @Operation(summary = "Get dashboard metrics", description = "Get comprehensive analytics metrics for the dashboard.")
    @GetMapping("/dashboard")
    public ResponseEntity<AnalyticsService.DashboardMetrics> getDashboardMetrics(
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate) {

        LocalDateTime start = DateTimeParams.parseStart(startDate, LocalDateTime.now().minusDays(30));
        LocalDateTime end = DateTimeParams.parseEnd(endDate, LocalDateTime.now());

        return ResponseEntity.ok(analyticsService.getDashboardMetrics(start, end));
    }

    /**
     * Get metrics filtered by category.
     * GET /api/analytics/category/{category}?startDate=...&endDate=...
     */
    @Operation(summary = "Get metrics by category", description = "Get analytics metrics filtered by ticket category.")
    @GetMapping("/category/{category}")
    public ResponseEntity<AnalyticsService.DashboardMetrics> getMetricsByCategory(
            @PathVariable String category,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate) {

        LocalDateTime start = DateTimeParams.parseStart(startDate, LocalDateTime.now().minusDays(30));
        LocalDateTime end = DateTimeParams.parseEnd(endDate, LocalDateTime.now());

        return ResponseEntity.ok(analyticsService.getMetricsByCategory(category, start, end));
    }

    /**
     * Get metrics filtered by agent.
     * GET /api/analytics/agent/{agent}?startDate=...&endDate=...
     */
    @Operation(summary = "Get metrics by agent", description = "Get analytics metrics filtered by assigned agent.")
    @GetMapping("/agent/{agent}")
    public ResponseEntity<AnalyticsService.DashboardMetrics> getMetricsByAgent(
            @PathVariable String agent,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate) {

        LocalDateTime start = DateTimeParams.parseStart(startDate, LocalDateTime.now().minusDays(30));
        LocalDateTime end = DateTimeParams.parseEnd(endDate, LocalDateTime.now());

        return ResponseEntity.ok(analyticsService.getMetricsByAgent(agent, start, end));
    }

    /**
     * Get daily trend data for charts.
     * GET /api/analytics/trend?startDate=...&endDate=...
     */
    @Operation(summary = "Get daily trend", description = "Get daily metric trends for chart visualization.")
    @GetMapping("/trend")
    public ResponseEntity<List<AnalyticsService.DailyMetric>> getDailyTrend(
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate) {

        LocalDateTime start = DateTimeParams.parseStart(startDate, LocalDateTime.now().minusDays(30));
        LocalDateTime end = DateTimeParams.parseEnd(endDate, LocalDateTime.now());

        return ResponseEntity.ok(analyticsService.getDailyTrend(start, end));
    }

    /**
     * Get summary statistics.
     * GET /api/analytics/summary
     */
    @Operation(summary = "Get summary statistics", description = "Get key summary statistics for quick overview.")
    @GetMapping("/summary")
    public ResponseEntity<Map<String, Object>> getSummary() {
        var metrics = analyticsService.getDashboardMetrics(
            LocalDateTime.now().minusDays(30), LocalDateTime.now());

        return ResponseEntity.ok(Map.of(
            "totalSessions", metrics.totalSessions(),
            "aiContainmentRate", Math.round(metrics.aiContainmentRate() * 100.0) / 100.0,
            "humanEscalationRate", Math.round(metrics.humanEscalationRate() * 100.0) / 100.0,
            "csatScore", Math.round(metrics.csatScore() * 100.0) / 100.0,
            "avgFirstResponseTimeSeconds", Math.round(metrics.avgFirstResponseTimeSeconds()),
            "totalTickets", metrics.totalTickets(),
            "openTickets", metrics.openTickets()
        ));
    }
}
