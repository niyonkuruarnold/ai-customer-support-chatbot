package com.codafriqa.ai_customer_support_chatbot.dto;

import java.util.List;

/**
 * DTO for chat responses sent to the frontend
 */
public class ChatResponseDto {

    private String response;

    /** The session this message belongs to; the frontend stores it for follow-ups. */
    private Long sessionId;

    /** Current session status: ACTIVE or ESCALATED. */
    private String status;

    /**
     * True when the answer was grounded in retrieved knowledge base context
     * (RAG). Empty when the vector store was unavailable or had nothing
     * relevant, so the AI answered from its base instruction only.
     */
    private boolean ragUsed;

    /**
     * Source documents referenced by the retrieved context chunks — lets the
     * frontend show citations/"answered from" metadata. Empty when no RAG
     * context was used.
     */
    private List<ContextReference> contextReferences;

    /** A source document referenced by the retrieved chunks. */
    public record ContextReference(Long documentId, String title, String sourceType) {
    }

    public ChatResponseDto() {
        this.contextReferences = List.of();
    }

    public ChatResponseDto(String response) {
        this.response = response;
        this.contextReferences = List.of();
    }

    public ChatResponseDto(String response, Long sessionId, String status) {
        this.response = response;
        this.sessionId = sessionId;
        this.status = status;
        this.contextReferences = List.of();
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

    public boolean isRagUsed() {
        return ragUsed;
    }

    public void setRagUsed(boolean ragUsed) {
        this.ragUsed = ragUsed;
    }

    public List<ContextReference> getContextReferences() {
        return contextReferences;
    }

    public void setContextReferences(List<ContextReference> contextReferences) {
        this.contextReferences = contextReferences == null ? List.of() : contextReferences;
    }
}
