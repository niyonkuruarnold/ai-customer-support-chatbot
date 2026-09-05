package com.codafriqa.ai_customer_support_chatbot.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * DTO for submitting an internal agent note.
 * Internal notes are broadcast only to agent-specific WebSocket channels
 * and never sent to public customer topics.
 */
public record InternalNoteDto(
    @NotBlank(message = "Note content is required")
    String content,

    String agentName
) {}
