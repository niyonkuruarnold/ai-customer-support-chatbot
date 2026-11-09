package com.codafriqa.ai_customer_support_chatbot.dto;

/**
 * Request payload for creating a new tool.
 */
public record CreateToolRequest(
        String name,
        String description,
        String category,
        Long ownerId) {
}
