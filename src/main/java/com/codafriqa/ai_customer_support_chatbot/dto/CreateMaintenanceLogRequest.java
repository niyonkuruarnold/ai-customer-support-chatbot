package com.codafriqa.ai_customer_support_chatbot.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Request payload for creating a maintenance log entry.
 */
public record CreateMaintenanceLogRequest(
        Long toolId,
        LocalDate serviceDate,
        String description,
        BigDecimal cost,
        LocalDate nextServiceDue) {
}
