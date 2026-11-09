package com.codafriqa.ai_customer_support_chatbot.dto;

import java.time.LocalDate;

/**
 * Request body for creating a new tool reservation.
 */
public record CreateReservationRequest(
        Long toolId,
        Long borrowerId,
        LocalDate startDate,
        LocalDate endDate,
        String notes) {
}
