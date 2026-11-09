package com.codafriqa.ai_customer_support_chatbot.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/**
 * DTO for updating ticket priority.
 */
public record TicketPriorityUpdateDto(
    @NotBlank(message = "Priority is required")
    @Pattern(regexp = "^(LOW|MEDIUM|HIGH|URGENT)$",
             message = "Invalid priority. Must be one of: LOW, MEDIUM, HIGH, URGENT")
    String priority
) {}
