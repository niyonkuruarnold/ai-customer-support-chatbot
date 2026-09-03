package com.codafriqa.ai_customer_support_chatbot.repository;

import com.codafriqa.ai_customer_support_chatbot.model.SupportTicket;
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
    List<SupportTicket> findByStatusInOrderByUpdatedAtDesc(List<String> statuses);

    List<SupportTicket> findBySessionId(Long sessionId);

    Optional<SupportTicket> findFirstBySessionIdOrderByUpdatedAtDesc(Long sessionId);

    /** Find ticket by unique reference. */
    Optional<SupportTicket> findByTicketReference(String ticketReference);

    /** Find tickets by assigned agent. */
    List<SupportTicket> findByAssignedAgentOrderByUpdatedAtDesc(String assignedAgent);

    /** Find tickets by status and priority. */
    List<SupportTicket> findByStatusAndPriorityOrderByUpdatedAtDesc(String status, String priority);

    /** Find tickets by category. */
    List<SupportTicket> findByCategoryOrderByUpdatedAtDesc(String category);

    /** Find tickets created after a specific date. */
    List<SupportTicket> findByCreatedAtAfterOrderByCreatedAtDesc(java.time.LocalDateTime date);

    /** Count tickets by status. */
    long countByStatus(String status);

    /** Count tickets by assigned agent. */
    long countByAssignedAgent(String assignedAgent);
}