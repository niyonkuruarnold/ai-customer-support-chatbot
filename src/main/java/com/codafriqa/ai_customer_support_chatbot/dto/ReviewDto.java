package com.codafriqa.ai_customer_support_chatbot.dto;

import java.time.LocalDateTime;

/**
 * Review summary for the frontend dashboard.
 */
public record ReviewDto(
        Long id,
        Long toolId,
        Long reviewerId,
        Long reservationId,
        Integer rating,
        String comment,
        LocalDateTime timestamp) {
}
