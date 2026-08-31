package com.codafriqa.ai_customer_support_chatbot.service;

import com.codafriqa.ai_customer_support_chatbot.dto.KnowledgeDocumentDto;
import com.codafriqa.ai_customer_support_chatbot.dto.SourceCitationDto;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
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

    /** Keep retrieval permissive enough for short support questions. */
    private static final double RETRIEVAL_SIMILARITY_THRESHOLD = 0.3;

    /**
     * Mock knowledge base context returned when the GEMINI_API_KEY is missing
     * or invalid, allowing the chat pipeline and citation flow to be tested
     * end-to-end during local development without an API quota.
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
    private final ChatClient chatClient;
    private final String geminiApiKey;

    public RagService(VectorStore vectorStore,
                      KnowledgeBaseService knowledgeBaseService,
                      ChatClient.Builder chatClientBuilder,
                      @Value("${spring.ai.google.genai.api-key:}") String geminiApiKey) {
        this.vectorStore = vectorStore;
        this.knowledgeBaseService = knowledgeBaseService;
        this.chatClient = chatClientBuilder != null ? chatClientBuilder.build() : null;
        this.geminiApiKey = geminiApiKey;
    }

    /**
     * Returns true when the Gemini API key is not configured or is set to
     * an obviously invalid placeholder value.
     */
    private boolean isApiKeyMissing() {
        return geminiApiKey == null || geminiApiKey.isBlank()
                || geminiApiKey.contains("your-") || geminiApiKey.contains("placeholder");
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
            log.info("GEMINI_API_KEY is not configured — returning mock context for local development");
            return RagContext.mockContext();
        }

        if (vectorStore == null || query == null || query.isBlank()) {
            log.debug("Skipping vector retrieval because the store or query is empty");
            return RagContext.empty();
        }

        try {
            List<Document> results = vectorStore.similaritySearch(
                    SearchRequest.builder()
                        .query(query)
                        .topK(RETRIEVAL_TOP_K)
                        .similarityThreshold(RETRIEVAL_SIMILARITY_THRESHOLD)
                        .build());
                if (results == null || results.isEmpty()) {
                log.debug("Vector similarity search returned no results for query: {}",
                    query.length() > 80 ? query.substring(0, 80) + "..." : query);
                return RagContext.empty();
            }
                log.debug("Vector similarity search returned {} results for query: {}",
                    results.size(), query.length() > 80 ? query.substring(0, 80) + "..." : query);

            String contextText = results.stream()
                    .filter(d -> d != null && d.getText() != null && !d.getText().isBlank())
                    .map(d -> "- " + d.getText().replaceAll("\\s+", " ").trim())
                    .collect(Collectors.joining("\n"));

                if (contextText.isBlank()) {
                return RagContext.empty();
                }

            // De-duplicate the source references by documentId, preserving order.
            Map<Long, ContextReference> byId = new LinkedHashMap<>();
            for (Document d : results) {
                if (d == null || d.getMetadata() == null) {
                    continue;
                }
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
            log.error("Vector retrieval failed ({}: {}); answering without knowledge base context. "
                    + "This likely means the embedding API key is invalid, the vector_store table "
                    + "was reset, or the pgvector extension is unavailable.",
                    e.getClass().getSimpleName(), e.getMessage(), e);
            if (isApiKeyMissing()) {
                return RagContext.mockContext();
            }
            return RagContext.empty();
        }
    }

    /**
     * Prompt template used by the LLM to generate suggested quick-question
     * chips from the indexed knowledge base content.
     */
    private static final String SUGGESTION_PROMPT_TEMPLATE = """
            You are an AI assistant for CODAFRIQA. Analyze the following knowledge base document content and generate 4 concise, high-value quick questions that a customer is most likely to ask.

            Document Content:
            ---------------------
            %s
            ---------------------

            Requirements:
            1. Generate exactly 4 short questions (6–10 words max per question).
            2. The questions must directly reflect key details in the provided document (e.g., support hours, SLAs, processes, pricing, or policies).
            3. The 4th question should always offer human escalation (e.g., "Talk to a human agent").
            4. Output ONLY a raw JSON array of strings with no markdown formatting, code blocks, or extra text.

            Example Output:
            ["What are your active support hours?", "What is the response SLA for tickets?", "How does enterprise onboarding work?", "Talk to a human agent"]
            """;

    /**
     * Generate suggested quick-question chips by querying the vector store
     * for knowledge base content and sending it to the LLM with a
     * structured prompt that returns a JSON array of questions.
     *
     * <p>When the API key is missing or the vector store is empty, returns
     * a hardcoded fallback so the UI always has suggestions to display.
     */
    public List<String> getSuggestedQuestions() {
        if (isApiKeyMissing()) {
            return fallbackQuestions();
        }

        try {
            // Broad query to pull diverse chunks from the knowledge base
            List<Document> results = vectorStore.similaritySearch(
                    SearchRequest.builder()
                            .query("customer support policies services hours contact refunds orders escalation")
                            .topK(10)
                            .similarityThreshold(RETRIEVAL_SIMILARITY_THRESHOLD)
                            .build());

            if (results.isEmpty()) {
                return fallbackQuestions();
            }

            // Combine all retrieved document content into a single block
            String documentContent = results.stream()
                    .map(Document::getText)
                    .filter(t -> t != null && !t.isBlank())
                    .collect(Collectors.joining("\n\n"));

            if (documentContent.isBlank()) {
                return fallbackQuestions();
            }

            // Ask the LLM to generate questions from the document content
            String systemPrompt = String.format(SUGGESTION_PROMPT_TEMPLATE, documentContent);
            String jsonResponse = chatClient.prompt()
                    .system(systemPrompt)
                    .call()
                    .content();

            // Parse the JSON array into a List<String>
            ObjectMapper mapper = new ObjectMapper();
            List<String> questions = mapper.readValue(jsonResponse,
                    new TypeReference<List<String>>() {});

            return (questions != null && !questions.isEmpty()) ? questions : fallbackQuestions();
        } catch (Exception e) {
            log.warn("Could not generate suggested questions ({}: {}); using fallback",
                    e.getClass().getSimpleName(), e.getMessage());
            return fallbackQuestions();
        }
    }

    /** Hardcoded fallback suggestions when the vector store is empty. */
    private static List<String> fallbackQuestions() {
        return List.of(
                "What are your support hours?",
                "How do I track my order?",
                "What is your refund policy?",
                "Talk to a human agent"
        );
    }

    /** Retrieved context: the text injected into the system prompt + source references. */
    public record RagContext(String contextText, List<ContextReference> references) {
        static RagContext empty() {
            return new RagContext("", List.of());
        }

        /**
         * Return a mock context used when the GEMINI_API_KEY is not configured.
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
