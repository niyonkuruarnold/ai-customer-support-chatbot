package com.codafriqa.ai_customer_support_chatbot.dto;

import java.time.LocalDateTime;

/**
 * A single persisted chat message exposed via the API.
 * sender is one of USER, AI, AGENT.
 * internal flag indicates agent-only notes that should not appear in customer chat.
 */
public record ChatMessageDto(Long id, String sender, String content, LocalDateTime timestamp, boolean internal) {
    
    /**
     * Convenience constructor for backward compatibility.
     * Creates a message with internal=false.
     */
    public ChatMessageDto(Long id, String sender, String content, LocalDateTime timestamp) {
        this(id, sender, content, timestamp, false);
    }
}
