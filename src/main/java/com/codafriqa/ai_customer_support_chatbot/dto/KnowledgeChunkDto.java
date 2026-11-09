package com.codafriqa.ai_customer_support_chatbot.dto;

import java.time.LocalDateTime;

/**
 * One indexed knowledge base chunk, used by the admin UI to show what is
 * actually stored in the vector store.
 */
public record KnowledgeChunkDto(
        Long id,
        Long documentId,
        String title,        // title of the owning document
        String sourceType,
        Integer chunkIndex,
        String content,
        LocalDateTime createdAt) {
}
