package com.codafriqa.ai_customer_support_chatbot.dto;

import com.codafriqa.ai_customer_support_chatbot.model.ReservationStatus;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Reservation summary for the frontend dashboard.
 */
public record ReservationDto(
        Long id,
        Long toolId,
        Long borrowerId,
        LocalDate startDate,
        LocalDate endDate,
        ReservationStatus status,
        String notes,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {
}
