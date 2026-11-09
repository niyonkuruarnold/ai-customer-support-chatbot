package com.codafriqa.ai_customer_support_chatbot.controller;

import com.codafriqa.ai_customer_support_chatbot.dto.KnowledgeDocumentDto;
import com.codafriqa.ai_customer_support_chatbot.service.KnowledgeBaseService;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

/**
 * Knowledge base (RAG) management endpoints for admins.
 *
 * Text and Markdown (plus PDF) support documents are parsed with Spring AI
 * document readers, split into chunks with {@code TokenTextSplitter}, and
 * stored — with their embeddings — in the PostgreSQL {@code vector_store}
 * table via the Spring AI {@code VectorStore}.
 *
 * All endpoints require authentication (see SecurityConfig). Available under
 * both /api/v1/admin/knowledge-base (as specified) and the unprefixed
 * /api/admin/knowledge-base alias used elsewhere in the codebase.
 */
@RestController
@RequestMapping({"/api/admin/knowledge-base", "/api/v1/admin/knowledge-base"})
public class KnowledgeBaseController {

    private final KnowledgeBaseService knowledgeBaseService;

    public KnowledgeBaseController(KnowledgeBaseService knowledgeBaseService) {
        this.knowledgeBaseService = knowledgeBaseService;
    }

    /**
     * Upload and index a support document (text or Markdown; PDF also works).
     * The file is parsed, chunked, embedded, and stored in the vector store.
     *
     * POST /api/v1/admin/knowledge-base/upload  (multipart: file + optional title)
     * Requires ADMIN role.
     */
    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<KnowledgeDocumentDto> upload(
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

    /** List indexed documents (with chunk counts) -> GET /api/v1/admin/knowledge-base */
    @GetMapping
    public ResponseEntity<List<KnowledgeDocumentDto>> listDocuments() {
        return ResponseEntity.ok(knowledgeBaseService.listDocuments());
    }

    /** Remove a document (and its chunks) from the vector store -> DELETE /api/v1/admin/knowledge-base/{id} */
    @PreAuthorize("hasRole('ADMIN')")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteDocument(@PathVariable Long id) {
        knowledgeBaseService.deleteDocument(id);
        return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
    }

    private static String defaultTitle(String fileName) {
        if (fileName == null || fileName.isBlank()) {
            return "Untitled document";
        }
        int dot = fileName.lastIndexOf('.');
        return dot > 0 ? fileName.substring(0, dot) : fileName;
    }
}
