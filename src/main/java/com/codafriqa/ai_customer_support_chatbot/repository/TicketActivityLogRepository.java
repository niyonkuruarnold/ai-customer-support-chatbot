package com.codafriqa.ai_customer_support_chatbot.repository;

import com.codafriqa.ai_customer_support_chatbot.model.TicketActivityLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Repository for TicketActivityLog entity.
 * 
 * This repository is intentionally limited to read operations and save (for inserts only).
 * Update and delete operations are not exposed to ensure immutability of the audit trail.
 */
@Repository
public interface TicketActivityLogRepository extends JpaRepository<TicketActivityLog, Long> {

    /**
     * Get all activity logs for a specific ticket, ordered by timestamp ascending.
     * This provides the chronological timeline view.
     */
    List<TicketActivityLog> findByTicketIdOrderByTimestampAsc(Long ticketId);

    /**
     * Get all activity logs for a specific ticket that are customer-visible.
     * Internal notes are filtered out for the customer-facing view.
     */
    List<TicketActivityLog> findByTicketIdAndCustomerVisibleTrueOrderByTimestampAsc(Long ticketId);

    /**
     * Get all activity logs for a specific ticket by action type.
     */
    List<TicketActivityLog> findByTicketIdAndActionTypeOrderByTimestampAsc(Long ticketId, String actionType);

    /**
     * Get the most recent activity log for a ticket.
     */
    TicketActivityLog findFirstByTicketIdOrderByTimestampDesc(Long ticketId);

    /**
     * Count activity logs for a specific ticket.
     */
    long countByTicketId(Long ticketId);

    // Note: No deleteBy methods are exposed to ensure immutability
    // The inherited deleteById and delete methods from JpaRepository should not be used
    // for this entity to maintain audit trail integrity
}
