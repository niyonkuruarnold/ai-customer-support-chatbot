package com.codafriqa.ai_customer_support_chatbot.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * DTO for submitting CSAT feedback on a customer conversation.
 */
public record CustomerFeedbackRequest(
    @NotNull(message = "CSAT score is required")
    @Min(value = 1, message = "CSAT score must be between 1 and 5")
    @Max(value = 5, message = "CSAT score must be between 1 and 5")
    Integer csatScore,

    String csatComment
) {}
