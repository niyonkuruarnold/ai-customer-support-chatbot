package com.codafriqa.ai_customer_support_chatbot.service;

import com.codafriqa.ai_customer_support_chatbot.dto.KnowledgeDocumentDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Dedicated RAG (Retrieval-Augmented Generation) service.
 *
 * <p>Injects the pgvector {@link VectorStore} and a {@link ChatClient.Builder}
 * so the ingestion pipeline and the context-aware chat both live here:
 *
 * <ul>
 *   <li>{@link #ingestText(String, String)} — chunk + store support
 *       documentation into the vector store (reusing the knowledge base
 *       ingestion pipeline).</li>
 *   <li>{@link #retrieveContext(String)} — convert the incoming user message
 *       into a vector query, retrieve the top-K most relevant chunks, and
 *       return both the context text (for the system prompt) and the source
 *       document references (for the response metadata).</li>
 * </ul>
 *
 * <p>Retrieval never throws: like the knowledge base service, it falls back to
 * an empty context when the store is unreachable or has nothing relevant.
 */
@Service
public class RagService {

    private static final Logger log = LoggerFactory.getLogger(RagService.class);

    /** Top-K relevant chunks retrieved per user message (spec: top 3-5). */
    private static final int RETRIEVAL_TOP_K = 4;

    private final VectorStore vectorStore;
    private final ChatClient chatClient;
    private final KnowledgeBaseService knowledgeBaseService;

    public RagService(VectorStore vectorStore,
                      ChatClient.Builder chatClientBuilder,
                      KnowledgeBaseService knowledgeBaseService) {
        this.vectorStore = vectorStore;
        this.chatClient = chatClientBuilder.build();
        this.knowledgeBaseService = knowledgeBaseService;
    }

    /**
     * Ingestion endpoint support: chunk and store support documentation into
     * the vector store. Delegates to the knowledge base ingestion pipeline
     * (document readers + TokenTextSplitter + pgvector persistence + rollback
     * on embedding failure) so there is exactly one ingestion path.
     */
    public KnowledgeDocumentDto ingestText(String title, String content) {
        return knowledgeBaseService.uploadText(title, content);
    }

    /**
     * Convert the user message into a vector query and retrieve the top-K
     * most relevant chunks from the vector store, together with the source
     * document metadata (documentId / title / sourceType) for response
     * references.
     */
    public RagContext retrieveContext(String query) {
        try {
            List<Document> results = vectorStore.similaritySearch(
                    SearchRequest.builder().query(query).topK(RETRIEVAL_TOP_K).build());
            if (results.isEmpty()) {
                return RagContext.empty();
            }

            String contextText = results.stream()
                    .map(d -> "- " + d.getText().replaceAll("\\s+", " ").trim())
                    .collect(Collectors.joining("\n"));

            // De-duplicate the source references by documentId, preserving order.
            Map<Long, ContextReference> byId = new LinkedHashMap<>();
            for (Document d : results) {
                Map<String, Object> meta = d.getMetadata();
                Object docId = meta.get("documentId");
                if (!(docId instanceof Number number)) {
                    continue; // legacy/unkeyed chunks carry no document reference
                }
                long id = number.longValue();
                byId.computeIfAbsent(id, k -> new ContextReference(
                        id,
                        String.valueOf(meta.getOrDefault("title", "document #" + id)),
                        String.valueOf(meta.getOrDefault("sourceType", "UNKNOWN"))));
            }

            return new RagContext(contextText, List.copyOf(byId.values()));
        } catch (Exception e) {
            // Never throws: log the exact failure and fall back to a plain prompt.
            log.warn("Vector retrieval failed ({}: {}); answering without knowledge base context",
                    e.getClass().getSimpleName(), e.getMessage(), e);
            return RagContext.empty();
        }
    }

    /** Retrieved context: the text injected into the system prompt + source references. */
    public record RagContext(String contextText, List<ContextReference> references) {
        static RagContext empty() {
            return new RagContext("", List.of());
        }
    }

    /** A single source document referenced by the retrieved chunks. */
    public record ContextReference(Long documentId, String title, String sourceType) {
    }
}
