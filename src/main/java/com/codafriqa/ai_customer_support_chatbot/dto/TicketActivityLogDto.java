package com.codafriqa.ai_customer_support_chatbot.dto;

import java.time.LocalDateTime;

/**
 * DTO for ticket activity log entries.
 * Used to display the chronological timeline of ticket changes.
 */
public record TicketActivityLogDto(
    Long id,
    Long ticketId,
    Long actorId,
    String actorName,
    String actionType,      // STATUS_CHANGE, PRIORITY_CHANGE, ASSIGNMENT, REPLY, NOTE, REOPEN, CREATED
    String previousValue,   // Before state (null for creation)
    String newValue,        // After state
    String description,     // Human-readable description
    LocalDateTime timestamp,
    boolean customerVisible // Whether this log is visible to the customer
) {}
