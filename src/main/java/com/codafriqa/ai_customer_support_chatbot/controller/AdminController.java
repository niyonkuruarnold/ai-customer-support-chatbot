package com.codafriqa.ai_customer_support_chatbot.controller;

import com.codafriqa.ai_customer_support_chatbot.dto.KnowledgeChunkDto;
import com.codafriqa.ai_customer_support_chatbot.dto.KnowledgeDocumentDto;
import com.codafriqa.ai_customer_support_chatbot.dto.KnowledgeTextRequestDto;
import com.codafriqa.ai_customer_support_chatbot.service.KnowledgeBaseService;
import jakarta.validation.Valid;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.Map;

/**
 * Admin REST endpoints for the knowledge base (RAG) ingestion pipeline.
 * All endpoints require authentication (see SecurityConfig).
 *
 * Available under both /api/admin and /api/v1/admin — the codebase uses
 * unprefixed paths, but the /api/v1 alias matches the API spec.
 */
@RestController
@RequestMapping({"/api/admin", "/api/v1/admin"})
public class AdminController {

    private final KnowledgeBaseService knowledgeBaseService;
    private final EmbeddingModel embeddingModel;

    public AdminController(KnowledgeBaseService knowledgeBaseService,
                           EmbeddingModel embeddingModel) {
        this.knowledgeBaseService = knowledgeBaseService;
        this.embeddingModel = embeddingModel;
    }

    /**
     * Upload a support document (.txt/.md/.pdf) and index it into the
     * knowledge base. The title defaults to the file name when not given.
     *
     * POST /api/admin/documents/upload  (multipart: file + optional title)
     * Requires ADMIN role.
     */
    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping(value = "/documents/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<KnowledgeDocumentDto> uploadDocument(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "title", required = false) String title) throws IOException {

        if (file.isEmpty()) {
            throw new IllegalArgumentException("The uploaded file is empty.");
        }
        String resolvedTitle = (title == null || title.isBlank())
                ? defaultTitle(file.getOriginalFilename())
                : title;
        return ResponseEntity.ok(knowledgeBaseService.uploadFile(
                resolvedTitle, file.getOriginalFilename(), file.getBytes()));
    }

    /**
     * Paste raw FAQ/support text and index it.
     * POST /api/admin/documents/text  (JSON: { title, content })
     * Requires ADMIN role.
     */
    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/documents/text")
    public ResponseEntity<KnowledgeDocumentDto> addText(
            @Valid @RequestBody KnowledgeTextRequestDto request) {
        return ResponseEntity.ok(knowledgeBaseService.uploadText(request.getTitle(), request.getContent()));
    }

    /** List indexed knowledge base documents with chunk counts -> GET /api/admin/documents */
    @GetMapping("/documents")
    public ResponseEntity<List<KnowledgeDocumentDto>> listDocuments() {
        return ResponseEntity.ok(knowledgeBaseService.listDocuments());
    }

    /** List every indexed chunk -> GET /api/admin/documents/chunks */
    @GetMapping("/documents/chunks")
    public ResponseEntity<List<KnowledgeChunkDto>> listChunks() {
        return ResponseEntity.ok(knowledgeBaseService.listChunks());
    }

    /** Remove a document (and its chunks) from the vector store -> DELETE /api/admin/documents/{id} */
    @PreAuthorize("hasRole('ADMIN')")
    @DeleteMapping("/documents/{id}")
    public ResponseEntity<Void> deleteDocument(@PathVariable Long id) {
        knowledgeBaseService.deleteDocument(id);
        return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
    }

    // ------------------------------------------------------------------
    // Gemini connectivity test
    // ------------------------------------------------------------------

    /**
     * Simple diagnostic endpoint that calls the Gemini embedding model
     * directly to verify Spring AI can communicate with Gemini outside
     * of the document ingestion pipeline.
     *
     * GET /api/admin/documents/test-openai
     * Requires ADMIN role.
     */
    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/documents/test-openai")
    public ResponseEntity<Map<String, Object>> testGemini(@Value("${spring.ai.google.genai.api-key:}") String apiKey) {
        if (apiKey == null || apiKey.isBlank()) {
            System.err.println("[Gemini TEST] API key is NOT SET");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of(
                    "status", "error",
                    "message", "GEMINI_API_KEY environment variable is not set. "
                            + "Copy .env.example to .env and fill in your key, "
                            + "or export GEMINI_API_KEY=AIza... in your terminal."
            ));
        }
        if (apiKey.contains("${")) {
            System.err.println("[Gemini TEST] API key resolved to raw placeholder: " + apiKey);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of(
                    "status", "error",
                    "message", "GEMINI_API_KEY env var is not set — Spring resolved it to the literal '" + apiKey + "'. "
                            + "Set it in your shell: export GEMINI_API_KEY=AIza..."
            ));
        }

        String maskedKey = apiKey.substring(0, Math.min(10, apiKey.length())) + "****";
        System.err.println("[Gemini TEST] Attempting embedding with key: " + maskedKey + " (length=" + apiKey.length() + ")");

        try {
            float[] embeddings = embeddingModel.embed("Test string");
            String msg = "Gemini embedding API is reachable and working. Key '" + maskedKey + "' is valid.";
            System.err.println("[Gemini TEST] SUCCESS — embedding dimensions=" + embeddings.length);
            return ResponseEntity.ok(Map.of(
                    "status", "success",
                    "model", "embedding-processed",
                    "dimensions", embeddings.length,
                    "apiKeyPrefix", maskedKey,
                    "message", msg
            ));
        } catch (RestClientResponseException e) {
            int status = e.getRawStatusCode();
            String body = e.getResponseBodyAsString();
            System.err.println("[Gemini TEST] FAILED — HTTP " + status);
            System.err.println("[Gemini TEST] Response body: " + body);
            String userMsg = switch (status) {
                case 401 -> "Invalid Gemini API Key (401 Unauthorized). Key '" + maskedKey + "' is rejected by Google. "
                        + "Generate a new key at https://aistudio.google.com/apikey";
                case 403 -> "Gemini API access denied (403 Forbidden). Key '" + maskedKey + "' lacks embedding permission.";
                case 429 -> "Gemini API rate limit exceeded (429). Wait a moment and try again.";
                case 404 -> "Gemini model not found (404). Check spring.ai.google.genai.embedding.options.model in application.properties.";
                default -> "Gemini API error (HTTP " + status + "). Response: " + body.substring(0, Math.min(200, body.length()));
            };
            return ResponseEntity.status(status).body(Map.of(
                    "status", "error",
                    "errorCode", status,
                    "errorBody", body.substring(0, Math.min(500, body.length())),
                    "apiKeyPrefix", maskedKey,
                    "message", userMsg
            ));
        } catch (Exception e) {
            e.printStackTrace();
            System.err.println("[Gemini TEST] FAILED — " + e.getClass().getSimpleName() + ": " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
                    "status", "error",
                    "message", "Failed to call Gemini embedding API: " + e.getMessage(),
                    "exceptionType", e.getClass().getSimpleName()
            ));
        }
    }

    private static String defaultTitle(String fileName) {
        if (fileName == null || fileName.isBlank()) {
            return "Untitled document";
        }
        int dot = fileName.lastIndexOf('.');
        return dot > 0 ? fileName.substring(0, dot) : fileName;
    }
}
