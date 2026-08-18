package com.codafriqa.ai_customer_support_chatbot.model;

/**
 * Lifecycle states for a tool reservation / borrow request.
 *
 * PENDING  → APPROVED → CHECKED_OUT → RETURNED
 *                ↓
 *            REJECTED (terminal)
 */
public enum ReservationStatus {
    PENDING,
    APPROVED,
    CHECKED_OUT,
    RETURNED,
    REJECTED
}
