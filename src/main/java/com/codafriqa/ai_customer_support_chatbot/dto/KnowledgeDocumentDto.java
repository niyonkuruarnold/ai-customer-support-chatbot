package com.codafriqa.ai_customer_support_chatbot.dto;

import java.time.LocalDateTime;

/**
 * Knowledge base document summary for the admin UI.
 */
public record KnowledgeDocumentDto(
        Long id,
        String title,
        String sourceType,   // TEXT | MARKDOWN | PDF
        String fileName,     // null for pasted text
        long chunkCount,     // number of indexed (embedded) chunks
        LocalDateTime createdAt) {
}
