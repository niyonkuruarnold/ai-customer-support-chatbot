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
    String actorRole,       // CUSTOMER, AGENT, ADMIN, SYSTEM
    String actionType,      // STATUS_CHANGE, PRIORITY_CHANGE, ASSIGNMENT, PUBLIC_REPLY, INTERNAL_NOTE
    String oldValue,        // Before state (null for creation)
    String newValue,        // After state
    String note,            // Human-readable description
    LocalDateTime timestamp
) {}
