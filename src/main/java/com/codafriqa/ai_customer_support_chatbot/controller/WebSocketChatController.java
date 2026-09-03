package com.codafriqa.ai_customer_support_chatbot.controller;

import com.codafriqa.ai_customer_support_chatbot.dto.WebSocketMessageDto;
import com.codafriqa.ai_customer_support_chatbot.model.ChatMessage;
import com.codafriqa.ai_customer_support_chatbot.repository.ChatMessageRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.handler.annotation.SendTo;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;

import java.time.LocalDateTime;

/**
 * WebSocket controller for real-time chat messaging.
 * 
 * Handles:
 * - Receiving messages from clients (customer or agent)
 * - Broadcasting to appropriate topics based on session and sender type
 * - Routing internal notes to agent-only channels
 */
@Controller
public class WebSocketChatController {

    private static final Logger log = LoggerFactory.getLogger(WebSocketChatController.class);

    private final SimpMessagingTemplate messagingTemplate;
    private final ChatMessageRepository messageRepository;

    public WebSocketChatController(SimpMessagingTemplate messagingTemplate,
                                   ChatMessageRepository messageRepository) {
        this.messagingTemplate = messagingTemplate;
        this.messageRepository = messageRepository;
    }

    /**
     * Handle incoming chat messages from clients.
     * 
     * Route:
     * - Regular messages → /topic/chat/{sessionId} (all subscribers)
     * - Internal notes → /topic/agent/{sessionId} (agent-only)
     * 
     * @param sessionId the chat session ID
     * @param message the incoming message DTO
     * @return the broadcast message
     */
    @MessageMapping("/chat.sendMessage/{sessionId}")
    @SendTo("/topic/chat/{sessionId}")
    public WebSocketMessageDto sendMessage(
            @DestinationVariable Long sessionId,
            @Payload WebSocketMessageDto message) {
        
        log.debug("WebSocket message received for session {}: sender={}, internal={}", 
                  sessionId, message.sender(), message.internal());

        // Persist the message to database
        ChatMessage chatMessage = new ChatMessage(
            sessionId,
            message.sender(),
            message.content(),
            message.internal()
        );
        ChatMessage saved = messageRepository.save(chatMessage);

        // Create broadcast DTO with saved ID
        WebSocketMessageDto broadcastMessage = new WebSocketMessageDto(
            saved.getId(),
            sessionId,
            message.sender(),
            message.content(),
            saved.getTimestamp(),
            message.internal(),
            message.type()
        );

        // If this is an internal note, also broadcast to agent-only topic
        if (message.internal()) {
            messagingTemplate.convertAndSend(
                "/topic/agent/" + sessionId,
                broadcastMessage
            );
        }

        return broadcastMessage;
    }

    /**
     * Broadcast AI handoff summary to all subscribers of a session.
     * 
     * @param sessionId the chat session ID
     * @param summary the AI-generated summary
     */
    public void broadcastSummary(Long sessionId, String summary, String sentiment) {
        WebSocketMessageDto summaryMessage = WebSocketMessageDto.summary(sessionId, summary, sentiment);
        messagingTemplate.convertAndSend("/topic/chat/" + sessionId, summaryMessage);
        log.debug("Broadcast summary to session {}: {}", sessionId, summary);
    }

    /**
     * Broadcast status change notification.
     * 
     * @param sessionId the chat session ID
     * @param newStatus the new session status
     */
    public void broadcastStatusChange(Long sessionId, String newStatus) {
        WebSocketMessageDto statusMessage = WebSocketMessageDto.statusChange(sessionId, newStatus);
        messagingTemplate.convertAndSend("/topic/chat/" + sessionId, statusMessage);
        log.debug("Broadcast status change to session {}: {}", sessionId, newStatus);
    }

    /**
     * Broadcast internal note to agent-only channel.
     * 
     * @param sessionId the chat session ID
     * @param note the internal note content
     */
    public void broadcastInternalNote(Long sessionId, String note, Long messageId) {
        WebSocketMessageDto noteMessage = new WebSocketMessageDto(
            messageId,
            sessionId,
            "AGENT",
            note,
            LocalDateTime.now(),
            true,
            WebSocketMessageDto.MessageType.NOTE
        );
        // Broadcast to agent-only topic
        messagingTemplate.convertAndSend("/topic/agent/" + sessionId, noteMessage);
        log.debug("Broadcast internal note to agents for session {}", sessionId);
    }
}
