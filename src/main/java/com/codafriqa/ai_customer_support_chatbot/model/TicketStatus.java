package com.codafriqa.ai_customer_support_chatbot.model;

/**
 * Ticket lifecycle states enforced by the state machine.
 *
 * State Machine:
 *   NEW → OPEN
 *   OPEN → PENDING_CUSTOMER / PENDING_INTERNAL / RESOLVED
 *   PENDING_CUSTOMER / PENDING_INTERNAL → OPEN / RESOLVED
 *   RESOLVED → CLOSED / REOPENED
 *   REOPENED → OPEN
 */
public enum TicketStatus {
    NEW,
    OPEN,
    PENDING_CUSTOMER,
    PENDING_INTERNAL,
    RESOLVED,
    CLOSED,
    REOPENED
}
