package com.codafriqa.ai_customer_support_chatbot.repository;

import com.codafriqa.ai_customer_support_chatbot.model.SupportTicket;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface SupportTicketRepository extends JpaRepository<SupportTicket, Long> {
    List<SupportTicket> findByUserId(Long userId);

    /** All tickets an agent should see: open, escalated, or in progress. */
    List<SupportTicket> findByStatusInOrderByUpdatedAtDesc(List<String> statuses);

    List<SupportTicket> findBySessionId(Long sessionId);

    Optional<SupportTicket> findFirstBySessionIdOrderByUpdatedAtDesc(Long sessionId);
}