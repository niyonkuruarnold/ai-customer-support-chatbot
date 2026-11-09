package com.codafriqa.ai_customer_support_chatbot.dto;

import java.time.LocalDateTime;

/**
 * Support ticket summary shown in the agent workspace ticket list.
 */
public record AgentTicketDto(
        Long id,
        Long sessionId,
        Long userId,
        String userEmail,   // customer contact shown in the agent workspace
        String subject,
        String description,
        String status,          // OPEN, IN_PROGRESS, ESCALATED, RESOLVED, CLOSED
        String priority,        // LOW, MEDIUM, HIGH, URGENT
        String assignedAgent,
        String aiSummary,
        String sentiment,       // positive | neutral | negative
        String lastMessage,     // truncated preview of the most recent message
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {
}
