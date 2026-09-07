package com.codafriqa.ai_customer_support_chatbot.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * DTO for creating a new ticket from a conversation.
 */
public record CreateTicketRequest(
    @NotBlank(message = "Subject is required")
    String subject,

    @NotBlank(message = "Description is required")
    String description,

    Long userId,
    Long sessionId,
    Long conversationId,
    String category,
    String priority
) {}
