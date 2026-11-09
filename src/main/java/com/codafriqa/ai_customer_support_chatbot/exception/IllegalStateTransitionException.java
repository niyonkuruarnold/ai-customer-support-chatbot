package com.codafriqa.ai_customer_support_chatbot.exception;

/**
 * Thrown when a ticket state machine transition is invalid.
 * Maps to HTTP 409 Conflict via GlobalExceptionHandler.
 */
public class IllegalStateTransitionException extends RuntimeException {

    private final String fromStatus;
    private final String toStatus;
    private final Long ticketId;

    public IllegalStateTransitionException(String fromStatus, String toStatus, Long ticketId) {
        super("Invalid ticket status transition: " + fromStatus + " → " + toStatus
              + " (ticket " + ticketId + ")");
        this.fromStatus = fromStatus;
        this.toStatus = toStatus;
        this.ticketId = ticketId;
    }

    public String getFromStatus() { return fromStatus; }
    public String getToStatus() { return toStatus; }
    public Long getTicketId() { return ticketId; }
}
