package com.codafriqa.ai_customer_support_chatbot.dto;

import com.codafriqa.ai_customer_support_chatbot.model.UserRole;
import java.time.LocalDateTime;

/**
 * DTO for user response
 */
public record UserResponseDto(
        Long id,
        String email,
        UserRole role,
        LocalDateTime createdAt
) {
}
