package com.codafriqa.ai_customer_support_chatbot.dto;

/**
 * A source document citation surfaced by the RAG retrieval pipeline.
 *
 * <p>Returned inside {@link ChatResponseDto#getSourceCitations()} so the
 * frontend can display "answered from" metadata (document title, source type)
 * alongside the AI-generated response text.
 *
 * @param sourceId   the knowledge document id that the chunk was derived from
 * @param title      human-readable title of the source document
 * @param sourceType ingestion source type (TEXT, PDF, MARKDOWN, etc.)
 */
public record SourceCitationDto(Long sourceId, String title, String sourceType) {
}
