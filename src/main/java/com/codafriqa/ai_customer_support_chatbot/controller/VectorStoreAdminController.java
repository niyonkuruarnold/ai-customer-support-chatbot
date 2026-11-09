package com.codafriqa.ai_customer_support_chatbot.controller;

import com.codafriqa.ai_customer_support_chatbot.service.KnowledgeBaseService;
import com.codafriqa.ai_customer_support_chatbot.service.SystemDataIndexer;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/admin/vector-stores")
public class VectorStoreAdminController {

    private final KnowledgeBaseService knowledgeBaseService;
    private final SystemDataIndexer systemDataIndexer;

    public VectorStoreAdminController(KnowledgeBaseService knowledgeBaseService,
                                      SystemDataIndexer systemDataIndexer) {
        this.knowledgeBaseService = knowledgeBaseService;
        this.systemDataIndexer = systemDataIndexer;
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/reindex")
    public ResponseEntity<Map<String, Integer>> reindexAll() {
        int knowledgeBaseChunks = knowledgeBaseService.reindexAll();
        long systemEntities = systemDataIndexer.reindexAllEntities();
        return ResponseEntity.ok(Map.of(
                "knowledgeBaseChunks", knowledgeBaseChunks,
                "systemEntities", Math.toIntExact(systemEntities)));
    }
}