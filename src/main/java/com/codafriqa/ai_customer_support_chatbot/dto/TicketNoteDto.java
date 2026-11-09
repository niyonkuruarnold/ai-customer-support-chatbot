package com.codafriqa.ai_customer_support_chatbot.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * DTO for adding notes to tickets.
 */
public record TicketNoteDto(
    @NotBlank(message = "Content is required")
    String content,
    
    boolean isInternal  // true for internal notes, false for public replies
) {}
