package com.codafriqa.ai_customer_support_chatbot.dto;

/**
 * Request payload for submitting a review.
 */
public record CreateReviewRequest(
        Long toolId,
        Long reviewerId,
        Long reservationId,
        Integer rating,
        String comment) {
}
