package com.codafriqa.ai_customer_support_chatbot.repository;

import com.codafriqa.ai_customer_support_chatbot.model.SupportTicket;
import com.codafriqa.ai_customer_support_chatbot.model.TicketPriority;
import com.codafriqa.ai_customer_support_chatbot.model.TicketStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface SupportTicketRepository extends JpaRepository<SupportTicket, Long>,
        JpaSpecificationExecutor<SupportTicket> {
    List<SupportTicket> findByUserId(Long userId);

    /** All tickets an agent should see: open, escalated, or in progress. */
    List<SupportTicket> findByStatusInOrderByUpdatedAtDesc(List<TicketStatus> statuses);

    List<SupportTicket> findBySessionId(Long sessionId);

    Optional<SupportTicket> findFirstBySessionIdOrderByUpdatedAtDesc(Long sessionId);

    /** Find ticket by unique reference. */
    Optional<SupportTicket> findByTicketReference(String ticketReference);

    /** Find tickets by assigned agent. */
    List<SupportTicket> findByAssignedAgentOrderByUpdatedAtDesc(String assignedAgent);

    /** Find tickets by status and priority. */
    List<SupportTicket> findByStatusAndPriorityOrderByUpdatedAtDesc(TicketStatus status, TicketPriority priority);

    /** Find tickets by category. */
    List<SupportTicket> findByCategoryOrderByUpdatedAtDesc(String category);

    /** Find tickets created after a specific date. */
    List<SupportTicket> findByCreatedAtAfterOrderByCreatedAtDesc(java.time.LocalDateTime date);

    /**
     * Count tickets by status.
     *
     * <p>Typed as the {@link TicketStatus} enum to match the entity column —
     * binding a plain String here made every analytics aggregation fail with
     * "Argument [X] of type [java.lang.String] did not match parameter type
     * [TicketStatus]" → HTTP 500.
     */
    long countByStatus(TicketStatus status);

    /** Count tickets by priority. */
    long countByPriority(TicketPriority priority);

    /** Count tickets by assigned agent. */
    long countByAssignedAgent(String assignedAgent);
}