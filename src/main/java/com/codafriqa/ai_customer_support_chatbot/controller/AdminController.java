package com.codafriqa.ai_customer_support_chatbot.controller;

import com.codafriqa.ai_customer_support_chatbot.dto.KnowledgeChunkDto;
import com.codafriqa.ai_customer_support_chatbot.dto.KnowledgeDocumentDto;
import com.codafriqa.ai_customer_support_chatbot.dto.KnowledgeTextRequestDto;
import com.codafriqa.ai_customer_support_chatbot.service.KnowledgeBaseService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

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

    public AdminController(KnowledgeBaseService knowledgeBaseService) {
        this.knowledgeBaseService = knowledgeBaseService;
    }

    /**
     * Upload a support document (.txt/.md/.pdf) and index it into the
     * knowledge base. The title defaults to the file name when not given.
     *
     * POST /api/admin/documents/upload  (multipart: file + optional title)
     */
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
     */
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
    @DeleteMapping("/documents/{id}")
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
