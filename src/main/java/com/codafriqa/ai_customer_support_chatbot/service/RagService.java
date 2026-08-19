package com.codafriqa.ai_customer_support_chatbot.service;

import com.codafriqa.ai_customer_support_chatbot.dto.KnowledgeDocumentDto;
import com.codafriqa.ai_customer_support_chatbot.dto.SourceCitationDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Value;
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

    /**
     * Mock knowledge base context returned when the OpenAI API key is missing
     * or invalid, allowing the chat pipeline and citation flow to be tested
     * end-to-end during local development without a paid API quota.
     *
     * <p>Each entry simulates a retrieved vector store chunk with realistic
     * document metadata so the frontend citation UI can be exercised.
     */
    private static final String MOCK_CHUNK_1 =
            "Code of Africa offers AI-powered customer support solutions including "
            + "intelligent chatbots, knowledge base management, and ticket escalation.";

    private static final String MOCK_CHUNK_2 =
            "Our flagship product is a RAG-enabled chatbot that retrieves relevant "
            + "knowledge base context to answer customer inquiries accurately.";

    private static final String MOCK_CHUNK_3 =
            "For support, contact support@codofafrica.com or use the in-app "
            + "escalation feature to connect with a human support agent.";

    private final VectorStore vectorStore;
    private final KnowledgeBaseService knowledgeBaseService;
    private final String openaiApiKey;

    public RagService(VectorStore vectorStore,
                      KnowledgeBaseService knowledgeBaseService,
                      @Value("${spring.ai.openai.api-key:}") String openaiApiKey) {
        this.vectorStore = vectorStore;
        this.knowledgeBaseService = knowledgeBaseService;
        this.openaiApiKey = openaiApiKey;
    }

    /**
     * Returns true when the OpenAI API key is not configured or is set to
     * an obviously invalid placeholder value.
     */
    private boolean isApiKeyMissing() {
        return openaiApiKey == null || openaiApiKey.isBlank()
                || openaiApiKey.contains("your-") || openaiApiKey.contains("sk-placeholder");
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
     *
     * <p>When the OpenAI API key is missing, returns a mock context so the
     * chat pipeline can be exercised end-to-end in local development without
     * a paid API quota. Vector store failures (unreachable store, connection
     * errors, empty results) are never propagated — they fall back to either
     * the mock context (when the key is missing) or an empty context.
     */
    public RagContext retrieveContext(String query) {
        if (isApiKeyMissing()) {
            log.info("OPENAI_API_KEY is not configured — returning mock context for local development");
            return RagContext.mockContext();
        }

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
            // Never throws: log the exact failure and fall back to the mock
            // context when the API key is missing, or an empty context otherwise.
            log.warn("Vector retrieval failed ({}: {}); answering without knowledge base context",
                    e.getClass().getSimpleName(), e.getMessage(), e);
            if (isApiKeyMissing()) {
                return RagContext.mockContext();
            }
            return RagContext.empty();
        }
    }

    /** Retrieved context: the text injected into the system prompt + source references. */
    public record RagContext(String contextText, List<ContextReference> references) {
        static RagContext empty() {
            return new RagContext("", List.of());
        }

        /**
         * Return a mock context used when the OpenAI API key is not configured.
         * This allows the entire chat pipeline and citation rendering to be
         * exercised in local development without a paid API quota.
         */
        static RagContext mockContext() {
            String contextText = List.of(MOCK_CHUNK_1, MOCK_CHUNK_2, MOCK_CHUNK_3)
                    .stream().map(c -> "- " + c).collect(Collectors.joining("\n"));
            List<ContextReference> references = List.of(
                    new ContextReference(-1L, "Code of Africa — Products & Services", "TEXT"),
                    new ContextReference(-2L, "Code of Africa — RAG Chatbot Architecture", "MARKDOWN"),
                    new ContextReference(-3L, "Code of Africa — Contact & Support", "PDF"));
            return new RagContext(contextText, references);
        }

        /**
         * Map the internal context references into DTOs suitable for the
         * response payload. Each reference becomes a {@link SourceCitationDto}
         * carrying sourceId, title, and sourceType.
         */
        public List<SourceCitationDto> toCitations() {
            return references.stream()
                    .map(r -> new SourceCitationDto(r.documentId(), r.title(), r.sourceType()))
                    .toList();
        }
    }

    /** A single source document referenced by the retrieved chunks. */
    public record ContextReference(Long documentId, String title, String sourceType) {
    }
}
