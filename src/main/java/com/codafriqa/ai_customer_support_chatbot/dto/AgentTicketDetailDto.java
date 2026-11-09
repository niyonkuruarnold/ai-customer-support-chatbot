package com.codafriqa.ai_customer_support_chatbot.dto;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Full ticket detail for the agent workspace: ticket metadata, the complete
 * session transcript, and the internal (customer-hidden) agent notes.
 */
public record AgentTicketDetailDto(
        Long id,
        Long sessionId,
        Long userId,
        String userEmail,   // customer contact shown in the agent workspace
        String subject,
        String description,
        String status,
        String priority,
        String assignedAgent,
        String aiSummary,
        String sentiment,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        List<ChatMessageDto> messages,
        List<String> internalNotes) {
}
