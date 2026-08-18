package com.codafriqa.ai_customer_support_chatbot.controller;

import com.codafriqa.ai_customer_support_chatbot.dto.KnowledgeDocumentDto;
import com.codafriqa.ai_customer_support_chatbot.service.RagService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * RAG (Retrieval-Augmented Generation) endpoints.
 *
 * <p>Exposed under both /api/rag and /api/v1/rag (the codebase uses
 * unprefixed paths with /api/v1 aliases matching the API spec). The chat
 * endpoint that consumes the retrieved context lives at /api/chat (see
 * ChatController) — RagService.retrieveContext() feeds it the top-K chunks.
 */
@RestController
@RequestMapping({"/api/rag", "/api/v1/rag"})
public class RagController {

    private final RagService ragService;

    public RagController(RagService ragService) {
        this.ragService = ragService;
    }

    /** Request body for POST /api/v1/rag/ingest. */
    public record IngestRequest(
            @NotBlank(message = "Title cannot be empty") String title,
            @NotBlank(message = "Content cannot be empty") String content) {
    }

    /**
     * Ingestion endpoint: chunk and store support documentation into the
     * vector store.
     *
     * POST /api/v1/rag/ingest  { "title": "...", "content": "..." }
     */
    @PostMapping("/ingest")
    public ResponseEntity<KnowledgeDocumentDto> ingest(@Valid @RequestBody IngestRequest request) {
        return ResponseEntity.ok(ragService.ingestText(request.title(), request.content()));
    }
}
