package com.codafriqa.ai_customer_support_chatbot.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Maintenance log summary for the frontend dashboard.
 */
public record MaintenanceLogDto(
        Long id,
        Long toolId,
        LocalDate serviceDate,
        String description,
        BigDecimal cost,
        LocalDate nextServiceDue,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {
}
