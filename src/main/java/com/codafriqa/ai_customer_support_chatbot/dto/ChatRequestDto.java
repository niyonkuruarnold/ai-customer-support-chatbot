package com.codafriqa.ai_customer_support_chatbot.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * DTO for incoming chat requests from the frontend
 */
public class ChatRequestDto {

    @NotBlank(message = "Message cannot be empty")
    @Size(min = 1, max = 2000, message = "Message must be between 1 and 2000 characters")
    private String message;

    /** Optional: the chat session this message belongs to. Created on first message when absent. */
    private Long sessionId;

    public ChatRequestDto() {
    }

    public ChatRequestDto(String message) {
        this.message = message;
    }

    public ChatRequestDto(String message, Long sessionId) {
        this.message = message;
        this.sessionId = sessionId;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public Long getSessionId() {
        return sessionId;
    }

    public void setSessionId(Long sessionId) {
        this.sessionId = sessionId;
    }
}
