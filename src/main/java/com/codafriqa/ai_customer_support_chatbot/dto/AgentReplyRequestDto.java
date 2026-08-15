package com.codafriqa.ai_customer_support_chatbot.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Agent reply to a customer, saved into the chat transcript.
 */
public record AgentReplyRequestDto(
        @NotBlank(message = "Message cannot be empty")
        @Size(max = 2000, message = "Message must be at most 2000 characters")
        String message) {
}
