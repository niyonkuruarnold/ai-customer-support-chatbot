package com.codafriqa.ai_customer_support_chatbot.repository;

import com.codafriqa.ai_customer_support_chatbot.model.ChatMessage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface MessageRepository extends JpaRepository<ChatMessage, Long> {

    /**
     * Fetch all messages for a given session in chronological order.
     */
    List<ChatMessage> findBySessionIdOrderByTimestampAsc(Long sessionId);
}
