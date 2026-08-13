package com.codafriqa.ai_customer_support_chatbot.repository;

import com.codafriqa.ai_customer_support_chatbot.model.ChatMessage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {
    List<ChatMessage> findBySessionId(Long sessionId);
}