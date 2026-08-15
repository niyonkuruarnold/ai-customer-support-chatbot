package com.codafriqa.ai_customer_support_chatbot.dto;

import java.time.LocalDateTime;

/**
 * A single persisted chat message exposed via the API.
 * sender is one of USER, AI, AGENT.
 */
public record ChatMessageDto(Long id, String sender, String content, LocalDateTime timestamp) {
}
