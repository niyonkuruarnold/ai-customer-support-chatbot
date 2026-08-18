package com.codafriqa.ai_customer_support_chatbot.dto;

/**
 * Average rating summary for a tool or user.
 */
public record AverageRatingDto(
        Double averageRating,
        Long reviewCount) {
}
