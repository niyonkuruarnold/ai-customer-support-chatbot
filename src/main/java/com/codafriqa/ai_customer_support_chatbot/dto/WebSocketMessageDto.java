package com.codafriqa.ai_customer_support_chatbot.dto;

import java.time.LocalDateTime;

/**
 * DTO for WebSocket chat messages broadcast to clients.
 * Contains all information needed for real-time display.
 */
public record WebSocketMessageDto(
    Long id,
    Long sessionId,
    String sender,      // USER, AI, AGENT
    String content,
    LocalDateTime timestamp,
    boolean internal,   // true for internal notes (agent-only)
    MessageType type    // MESSAGE, NOTE, SUMMARY, STATUS_CHANGE
) {
    /**
     * Message types for WebSocket routing and display.
     */
    public enum MessageType {
        MESSAGE,        // Regular chat message
        NOTE,           // Internal agent note
        SUMMARY,        // AI handoff summary
        STATUS_CHANGE   // Session status update
    }

    /**
     * Factory method for regular chat messages.
     */
    public static WebSocketMessageDto chatMessage(Long id, Long sessionId, String sender, String content) {
        return new WebSocketMessageDto(
            id,
            sessionId,
            sender,
            content,
            LocalDateTime.now(),
            false,
            MessageType.MESSAGE
        );
    }

    /**
     * Factory method for internal notes.
     */
    public static WebSocketMessageDto internalNote(Long id, Long sessionId, String content) {
        return new WebSocketMessageDto(
            id,
            sessionId,
            "AGENT",
            content,
            LocalDateTime.now(),
            true,
            MessageType.NOTE
        );
    }

    /**
     * Factory method for AI summary broadcast.
     */
    public static WebSocketMessageDto summary(Long sessionId, String summaryText, String sentiment) {
        return new WebSocketMessageDto(
            null,
            sessionId,
            "SYSTEM",
            summaryText,
            LocalDateTime.now(),
            false,
            MessageType.SUMMARY
        );
    }

    /**
     * Factory method for status change notifications.
     */
    public static WebSocketMessageDto statusChange(Long sessionId, String newStatus) {
        return new WebSocketMessageDto(
            null,
            sessionId,
            "SYSTEM",
            newStatus,
            LocalDateTime.now(),
            false,
            MessageType.STATUS_CHANGE
        );
    }
}
