package com.codafriqa.ai_customer_support_chatbot.dto;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * DTO for the Section 6.9 analytics metrics endpoint.
 * Returns the four key operational metrics plus supporting context.
 */
public record AnalyticsMetricsDto(
    /** Total conversations in the date range. */
    long totalConversations,

    /** Conversations resolved entirely by AI (no escalation). */
    long aiResolvedConversations,

    /** Conversations escalated to a human agent. */
    long escalatedConversations,

    /** AI Containment Rate = (AI Resolved / Total) * 100. */
    double aiContainmentRate,

    /** Human Escalation Rate = (Escalated / Total) * 100. */
    double humanEscalationRate,

    /** Average CSAT rating on a 1–5 scale. Null if no feedback exists. */
    Double averageCsatRating,

    /** Average first response time in minutes. */
    double averageFirstResponseTimeMinutes,

    /** Tickets by status in the date range. */
    Map<String, Long> ticketsByStatus,

    /** Tickets by priority in the date range. */
    Map<String, Long> ticketsByPriority,

    /** Start of the reporting window. */
    LocalDateTime startDate,

    /** End of the reporting window. */
    LocalDateTime endDate
) {}
