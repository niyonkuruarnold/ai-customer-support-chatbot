package com.codafriqa.ai_customer_support_chatbot.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Internal agent note. Stored on the ticket only and never sent to the
 * customer-facing chat.
 */
public record AgentNoteRequestDto(
        @NotBlank(message = "Note cannot be empty")
        @Size(max = 2000, message = "Note must be at most 2000 characters")
        String content) {
}
