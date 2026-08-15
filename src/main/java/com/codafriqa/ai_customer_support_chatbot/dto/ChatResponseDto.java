package com.codafriqa.ai_customer_support_chatbot.dto;

/**
 * DTO for chat responses sent to the frontend
 */
public class ChatResponseDto {

    private String response;

    /** The session this message belongs to; the frontend stores it for follow-ups. */
    private Long sessionId;

    /** Current session status: ACTIVE or ESCALATED. */
    private String status;

    public ChatResponseDto() {
    }

    public ChatResponseDto(String response) {
        this.response = response;
    }

    public ChatResponseDto(String response, Long sessionId, String status) {
        this.response = response;
        this.sessionId = sessionId;
        this.status = status;
    }

    public String getResponse() {
        return response;
    }

    public void setResponse(String response) {
        this.response = response;
    }

    public Long getSessionId() {
        return sessionId;
    }

    public void setSessionId(Long sessionId) {
        this.sessionId = sessionId;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }
}
