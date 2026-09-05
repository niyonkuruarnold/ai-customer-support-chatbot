package com.codafriqa.ai_customer_support_chatbot.service;

import com.codafriqa.ai_customer_support_chatbot.dto.CustomerFeedbackRequest;
import com.codafriqa.ai_customer_support_chatbot.dto.MessageDto;
import com.codafriqa.ai_customer_support_chatbot.exception.ResourceNotFoundException;
import com.codafriqa.ai_customer_support_chatbot.model.ChatMessage;
import com.codafriqa.ai_customer_support_chatbot.model.Conversation;
import com.codafriqa.ai_customer_support_chatbot.model.ConversationStatus;
import com.codafriqa.ai_customer_support_chatbot.repository.ConversationRepository;
import com.codafriqa.ai_customer_support_chatbot.repository.MessageRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Service for customer-facing chat operations: message history retrieval
 * and CSAT feedback submission on conversations.
 */
@Service
public class CustomerChatService {

    private static final Logger log = LoggerFactory.getLogger(CustomerChatService.class);

    private final ConversationRepository conversationRepository;
    private final MessageRepository messageRepository;

    public CustomerChatService(ConversationRepository conversationRepository,
                               MessageRepository messageRepository) {
        this.conversationRepository = conversationRepository;
        this.messageRepository = messageRepository;
    }

    /**
     * Retrieve the full chronological message history for a conversation.
     *
     * @param sessionId the session identifier
     * @return ordered list of messages as DTOs
     */
    public List<MessageDto> getConversationHistory(String sessionId) {
        Long id = Long.parseLong(sessionId);
        List<ChatMessage> messages = messageRepository.findBySessionIdOrderByTimestampAsc(id);
        return messages.stream()
                .map(this::toDto)
                .toList();
    }

    /**
     * Submit CSAT feedback for a conversation. Looks up the conversation by
     * sessionId, maps the feedback fields, sets csatSubmittedAt, updates
     * status to CLOSED, and persists to PostgreSQL.
     *
     * @param sessionId the session identifier
     * @param request   the validated feedback DTO
     * @throws ResourceNotFoundException if no conversation exists for the given sessionId
     */
    @Transactional
    public void saveCsatFeedback(String sessionId, CustomerFeedbackRequest request) {
        Long id = Long.parseLong(sessionId);
        Conversation conversation = conversationRepository.findBySessionId(id)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Conversation not found for session: " + sessionId));

        conversation.setCsatScore(request.csatScore());
        conversation.setCsatComment(request.csatComment());
        conversation.setCsatSubmittedAt(LocalDateTime.now());
        conversation.setStatus(ConversationStatus.CLOSED);

        conversationRepository.save(conversation);
        log.info("CSAT feedback saved for session {}: score={}, closed conversation {}",
                sessionId, request.csatScore(), conversation.getId());
    }

    private MessageDto toDto(ChatMessage message) {
        return new MessageDto(
                message.getId(),
                message.getSessionId(),
                message.getSender(),
                message.getContent(),
                message.getTimestamp()
        );
    }
}
