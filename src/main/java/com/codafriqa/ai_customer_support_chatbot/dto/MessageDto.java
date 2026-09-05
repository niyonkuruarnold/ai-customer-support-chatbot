package com.codafriqa.ai_customer_support_chatbot.dto;

import java.time.LocalDateTime;

/**
 * DTO for a single message in the conversation history.
 * sender is one of CUSTOMER, AI, AGENT.
 */
public record MessageDto(
    Long id,
    Long sessionId,
    String sender,
    String content,
    LocalDateTime timestamp
) {}
