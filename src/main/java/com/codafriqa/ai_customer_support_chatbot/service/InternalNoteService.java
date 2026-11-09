package com.codafriqa.ai_customer_support_chatbot.service;

import com.codafriqa.ai_customer_support_chatbot.controller.WebSocketChatController;
import com.codafriqa.ai_customer_support_chatbot.dto.InternalNoteDto;
import com.codafriqa.ai_customer_support_chatbot.model.ChatMessage;
import com.codafriqa.ai_customer_support_chatbot.repository.ChatMessageRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service for creating and broadcasting internal agent notes.
 *
 * <p>Internal notes (is_internal = true) are persisted as ChatMessage
 * records and broadcast exclusively over agent-specific WebSocket channels
 * ({@code /topic/agent/{sessionId}}).  They are never sent to public
 * customer topics ({@code /topic/chat/{sessionId}}).
 */
@Service
public class InternalNoteService {

    private static final Logger log = LoggerFactory.getLogger(InternalNoteService.class);

    private final ChatMessageRepository messageRepository;
    private final WebSocketChatController webSocketController;

    public InternalNoteService(ChatMessageRepository messageRepository,
                               WebSocketChatController webSocketController) {
        this.messageRepository = messageRepository;
        this.webSocketController = webSocketController;
    }

    /**
     * Create an internal note and broadcast it to agent-only channels.
     *
     * @param sessionId the chat session ID
     * @param request   the note content and agent name
     * @return the persisted ChatMessage entity
     */
    @Transactional
    public ChatMessage createInternalNote(Long sessionId, InternalNoteDto request) {
        ChatMessage note = new ChatMessage(
                sessionId,
                request.agentName() != null ? request.agentName() : "AGENT",
                request.content(),
                true  // isInternal = true
        );

        ChatMessage saved = messageRepository.save(note);

        // Broadcast to agent-only channel — never to /topic/chat/{sessionId}
        try {
            webSocketController.broadcastInternalNote(sessionId, saved.getContent(), saved.getId());
        } catch (Exception e) {
            log.debug("WebSocket broadcast failed for internal note on session {}: {}",
                    sessionId, e.getMessage());
        }

        log.info("Internal note created for session {} by {}: {}",
                sessionId, saved.getSender(),
                saved.getContent().length() > 80
                        ? saved.getContent().substring(0, 80) + "…"
                        : saved.getContent());

        return saved;
    }
}
