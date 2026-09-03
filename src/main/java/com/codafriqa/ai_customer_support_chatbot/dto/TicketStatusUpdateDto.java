package com.codafriqa.ai_customer_support_chatbot.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/**
 * DTO for updating ticket status.
 */
public record TicketStatusUpdateDto(
    @NotBlank(message = "Status is required")
    @Pattern(regexp = "^(NEW|OPEN|PENDING_CUSTOMER|PENDING_INTERNAL|IN_PROGRESS|RESOLVED|CLOSED|REOPENED)$",
             message = "Invalid status. Must be one of: NEW, OPEN, PENDING_CUSTOMER, PENDING_INTERNAL, IN_PROGRESS, RESOLVED, CLOSED, REOPENED")
    String status,
    
    String reason  // Optional reason for the status change
) {}
