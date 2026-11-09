package com.codafriqa.ai_customer_support_chatbot.controller;

import com.codafriqa.ai_customer_support_chatbot.dto.KnowledgeDocumentDto;
import com.codafriqa.ai_customer_support_chatbot.model.SystemScanLog;
import com.codafriqa.ai_customer_support_chatbot.service.RagService;
import com.codafriqa.ai_customer_support_chatbot.service.SystemScannerService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.Map;

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
@Tag(name = "RAG Ingestion", description = "Knowledge base document ingestion for Retrieval-Augmented Generation")
public class RagController {

    private final RagService ragService;
    private final SystemScannerService systemScannerService;

    public RagController(RagService ragService, SystemScannerService systemScannerService) {
        this.ragService = ragService;
        this.systemScannerService = systemScannerService;
    }

    /** Request body for POST /api/v1/rag/ingest. */
    public record IngestRequest(
            @NotBlank(message = "Title cannot be empty") String title,
            @NotBlank(message = "Content cannot be empty") String content) {
    }

    /**
     * Ingestion endpoint: chunk and store support documentation into the
     * vector store.
     */
    @Operation(
            summary = "Ingest a document into the knowledge base",
            description = "Chunk and store support documentation into the pgvector vector store. " +
                    "The document is parsed with Spring AI document readers, split into ~500-token chunks, " +
                    "embedded with Google text-embedding-004, and stored for RAG retrieval. " +
                    "Without GEMINI_API_KEY, fails fast with a 400 and rolls back cleanly.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Document ingested successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid request (empty title/content) or embedding failure"),
            @ApiResponse(responseCode = "500", description = "Unexpected ingestion error")
    })
    @PostMapping("/ingest")
    public ResponseEntity<KnowledgeDocumentDto> ingest(@Valid @RequestBody IngestRequest request) {
        return ResponseEntity.ok(ragService.ingestText(request.title(), request.content()));
    }

    /**
     * System sync status: returns the timestamp and record count of the
     * latest automated scan.
     */
    @Operation(
            summary = "Get system scan sync status",
            description = "Returns the timestamp, record count, and status of the latest automated " +
                    "system scan that indexes knowledge documents into the vector store.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Sync status returned")
    })
    @GetMapping("/sync-status")
    public ResponseEntity<Map<String, Object>> getSyncStatus() {
        SystemScanLog latest = systemScannerService.getLatestScan();

        if (latest == null) {
            return ResponseEntity.ok(Map.of(
                    "status", "NO_SCANS",
                    "message", "No system scans have been executed yet",
                    "timestamp", LocalDateTime.now().toString()));
        }

        return ResponseEntity.ok(Map.of(
                "status", latest.getStatus().name(),
                "timestamp", latest.getScannedAt().toString(),
                "recordsScanned", latest.getRecordsScanned(),
                "recordsIndexed", latest.getRecordsIndexed(),
                "chunksCreated", latest.getChunksCreated(),
                "durationMs", latest.getDurationMs() != null ? latest.getDurationMs() : 0,
                "message", latest.getMessage() != null ? latest.getMessage() : ""));
    }
}
