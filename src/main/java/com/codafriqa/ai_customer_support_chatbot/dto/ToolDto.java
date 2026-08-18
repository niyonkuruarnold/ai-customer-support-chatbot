package com.codafriqa.ai_customer_support_chatbot.dto;

import com.codafriqa.ai_customer_support_chatbot.model.ToolStatus;
import java.time.LocalDateTime;

/**
 * Tool summary for the frontend dashboard.
 */
public record ToolDto(
        Long id,
        String name,
        String description,
        String category,
        Long ownerId,
        ToolStatus status,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {
}
