package com.codafriqa.ai_customer_support_chatbot.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * DTO for submitting chat feedback (CSAT score).
 */
public record ChatFeedbackDto(
    @NotNull(message = "Session ID is required")
    Long sessionId,

    @NotNull(message = "Rating is required")
    @Min(value = 1, message = "Rating must be between 1 and 5")
    @Max(value = 5, message = "Rating must be between 1 and 5")
    Integer rating,

    String comment
) {}
