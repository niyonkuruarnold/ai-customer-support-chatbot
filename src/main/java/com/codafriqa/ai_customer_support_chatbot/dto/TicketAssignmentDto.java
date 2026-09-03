package com.codafriqa.ai_customer_support_chatbot.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * DTO for updating ticket assignment.
 */
public record TicketAssignmentDto(
    @NotBlank(message = "Agent name is required")
    String assignedAgent
) {}
