package com.codafriqa.ai_customer_support_chatbot.dto;

import java.time.LocalDateTime;

/**
 * Ticket summary for the admin dashboard (filtering + pagination).
 * Does not include the transcript — use the agent endpoints for the full
 * conversation with internal notes.
 */
public record TicketDto(
        Long id,
        String ticketReference,  // Unique ticket reference (e.g., TICKET-ABC123)
        Long sessionId,
        Long userId,
        String userEmail,       // customer contact (email of the backing account)
        String subject,
        String description,
        String status,          // NEW, OPEN, PENDING_CUSTOMER, PENDING_INTERNAL, RESOLVED, CLOSED, REOPENED
        String priority,        // LOW, MEDIUM, HIGH, URGENT
        String category,        // BILLING, TECHNICAL, GENERAL
        String assignedAgent,   // agent name, or null while unassigned
        String sentiment,       // positive | neutral | negative
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {
}
