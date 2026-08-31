package com.codafriqa.ai_customer_support_chatbot.service;

import com.codafriqa.ai_customer_support_chatbot.dto.KnowledgeChunkDto;
import com.codafriqa.ai_customer_support_chatbot.dto.KnowledgeDocumentDto;
import com.codafriqa.ai_customer_support_chatbot.exception.OpenAIApiException;
import com.codafriqa.ai_customer_support_chatbot.exception.ResourceNotFoundException;
import com.codafriqa.ai_customer_support_chatbot.model.KnowledgeChunk;
import com.codafriqa.ai_customer_support_chatbot.model.KnowledgeDocument;
import com.codafriqa.ai_customer_support_chatbot.repository.KnowledgeChunkRepository;
import com.codafriqa.ai_customer_support_chatbot.repository.KnowledgeDocumentRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.reader.TextReader;
import org.springframework.ai.reader.markdown.MarkdownDocumentReader;
import org.springframework.ai.reader.markdown.config.MarkdownDocumentReaderConfig;
import org.springframework.ai.reader.pdf.PagePdfDocumentReader;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientResponseException;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Knowledge base ingestion pipeline for the RAG setup.
 *
 * <p>Uploaded text, Markdown or PDF support documents are parsed with Spring
 * AI {@code DocumentReader}s, split into chunks with a {@link TokenTextSplitter},
 * and stored in PostgreSQL via the Spring AI pgvector {@link VectorStore}
 * (which auto-creates the {@code vector} extension and {@code vector_store}
 * table on startup). Each chunk row in the metadata tables mirrors a vector
 * store entry keyed by {@code "kb-" + chunkId}, so the admin endpoints can
 * list and delete indexed content by document.
 */
@Service
public class KnowledgeBaseService {

    private static final Logger log = LoggerFactory.getLogger(KnowledgeBaseService.class);

    /** How many relevant chunks the chat service retrieves per user message. */
    private static final int RETRIEVAL_TOP_K = 4;

    private static final TokenTextSplitter SPLITTER = TokenTextSplitter.builder()
            .withChunkSize(500)
            .withMinChunkSizeChars(350)
            .withMinChunkLengthToEmbed(5)
            .withMaxNumChunks(10000)
            .build();

    private final VectorStore vectorStore;
    private final KnowledgeDocumentRepository documentRepository;
    private final KnowledgeChunkRepository chunkRepository;
    private final String geminiApiKey;

    public KnowledgeBaseService(VectorStore vectorStore,
                                KnowledgeDocumentRepository documentRepository,
                                KnowledgeChunkRepository chunkRepository,
                                @Value("${spring.ai.google.genai.api-key:}") String geminiApiKey) {
        this.vectorStore = vectorStore;
        this.documentRepository = documentRepository;
        this.chunkRepository = chunkRepository;
        this.geminiApiKey = geminiApiKey;
    }

    /** Returns true when the Gemini API key is missing, is a placeholder, or is the raw env-var template. */
    private boolean isApiKeyMissing() {
        if (geminiApiKey == null || geminiApiKey.isBlank()) return true;
        if (geminiApiKey.contains("${")) return true;
        if (geminiApiKey.contains("your-") || geminiApiKey.contains("YOUR_ACTUAL")) return true;
        if (geminiApiKey.contains("placeholder")) return true;
        return false;
    }

    /** Log the key status for debugging (never logs the full key). */
    private void logKeyStatus() {
        if (geminiApiKey == null || geminiApiKey.isBlank()) {
            System.err.println("[Gemini] API key is NOT SET — set GEMINI_API_KEY env var");
            log.error("GEMINI_API_KEY is not set. Set it to a valid key from https://aistudio.google.com/apikey");
        } else if (geminiApiKey.contains("${")) {
            System.err.println("[Gemini] API key resolved to raw placeholder: " + geminiApiKey);
            log.error("GEMINI_API_KEY env var is not set — Spring resolved it to the literal placeholder '{}'", geminiApiKey);
        } else {
            String masked = geminiApiKey.substring(0, Math.min(7, geminiApiKey.length())) + "****";
            System.err.println("[Gemini] API key starts with: " + masked + " (length=" + geminiApiKey.length() + ")");
            log.info("Using GEMINI_API_KEY starting with '{}' (length={})", masked, geminiApiKey.length());
        }
    }

    // ------------------------------------------------------------------
    // Ingestion
    // ------------------------------------------------------------------

    /** Index raw pasted FAQ/support text. */
    @PreAuthorize("hasRole('ADMIN')")
    @Transactional
    public KnowledgeDocumentDto uploadText(String title, String content) {
        if (content == null || content.isBlank()) {
            throw new IllegalArgumentException("There is no readable content to index.");
        }
        return ingest(title.trim(), null, "TEXT", List.of(new Document(content)));
    }

    /**
     * Index an uploaded support file. The type is inferred from the file
     * extension: .pdf -> PDF reader, .md/.markdown -> Markdown reader,
     * anything else -> plain text reader.
     */
    @PreAuthorize("hasRole('ADMIN')")
    @Transactional
    public KnowledgeDocumentDto uploadFile(String title, String fileName, byte[] bytes) {
        if (bytes == null || bytes.length == 0) {
            throw new IllegalArgumentException("The uploaded file is empty.");
        }

        String name = fileName == null ? "" : fileName.toLowerCase(Locale.ROOT);
        ByteArrayResource resource = new ByteArrayResource(bytes, fileName);

        List<Document> parsed;
        String sourceType;
        if (name.endsWith(".pdf")) {
            sourceType = "PDF";
            parsed = new PagePdfDocumentReader(resource).get();
        } else if (name.endsWith(".md") || name.endsWith(".markdown")) {
            sourceType = "MARKDOWN";
            MarkdownDocumentReader reader = new MarkdownDocumentReader(
                    resource, MarkdownDocumentReaderConfig.defaultConfig());
            parsed = reader.get();
        } else {
            sourceType = "TEXT";
            TextReader reader = new TextReader(resource);
            reader.setCharset(java.nio.charset.StandardCharsets.UTF_8);
            parsed = reader.get();
        }

        return ingest(title.trim(), fileName, sourceType, parsed);
    }

    /**
     * Shared pipeline: split into chunks, persist the metadata rows, then
     * embed + store each chunk in the pgvector store. If embedding fails
     * (e.g. GEMINI_API_KEY is missing) the partial rows are rolled back and
     * a 400/500 is returned so the admin UI never lists unsearchable content.
     */
    private KnowledgeDocumentDto ingest(String title, String fileName, String sourceType,
                                        List<Document> parsed) {
        // ── Pre-flight validation: fail fast before touching the database ──
        if (isApiKeyMissing()) {
            logKeyStatus();
            throw new OpenAIApiException(
                    "GEMINI_API_KEY is not configured or is invalid. "
                    + "Set the environment variable to a valid key from "
                    + "https://aistudio.google.com/apikey and restart the application.\n"
                    + "Tip: copy .env.example to .env and fill in your real key.",
                    HttpStatus.BAD_REQUEST);
        }

        List<Document> chunks = parsed == null ? List.of() : SPLITTER.split(parsed);
        chunks = chunks.stream().filter(c -> c.getText() != null && !c.getText().isBlank()).toList();

        if (chunks.isEmpty()) {
            throw new IllegalArgumentException(
                    "No readable text could be extracted from this document.");
        }

        KnowledgeDocument document = documentRepository.save(
                new KnowledgeDocument(title, sourceType, fileName));

        List<Document> vectorDocs = new ArrayList<>(chunks.size());
        for (int i = 0; i < chunks.size(); i++) {
            KnowledgeChunk row = chunkRepository.save(
                    new KnowledgeChunk(document.getId(), i, chunks.get(i).getText()));

            Map<String, Object> metadata = new HashMap<>(chunks.get(i).getMetadata());
            metadata.put("documentId", document.getId());
            metadata.put("title", title);
            metadata.put("sourceType", sourceType);
            metadata.put("chunkIndex", i);

            vectorDocs.add(new Document(vectorIdForChunk(row.getId()), row.getContent(), metadata));
        }

        try {
            vectorStore.add(vectorDocs);
        } catch (RestClientResponseException e) {
            // ── Catch RestClient exceptions first (has getResponseBodyAsString) ──
            System.err.println("Gemini API HTTP Error Code: " + e.getRawStatusCode());
            System.err.println("Gemini API Response Body: " + e.getResponseBodyAsString());
            log.error("Gemini embedding API returned HTTP {} — body: {}",
                    e.getRawStatusCode(), e.getResponseBodyAsString(), e);
            rollbackIngestion(document.getId());
            throw new OpenAIApiException(
                    formatRestClientError(e), HttpStatus.valueOf(e.getRawStatusCode()), e);
        } catch (ResourceAccessException e) {
            // ── Network / timeout / DNS resolution failure ──
            log.error("Could not connect to Gemini API: {}", e.getMessage(), e);
            rollbackIngestion(document.getId());
            throw new OpenAIApiException(
                    "Could not connect to the Gemini API — check your network "
                    + "connection, firewall settings, and that generativelanguage.googleapis.com is reachable."
                    + (e.getMessage() != null ? " Details: " + e.getMessage() : ""),
                    HttpStatus.BAD_GATEWAY, e);
                } catch (DataAccessException e) {
                    rollbackIngestion(document.getId());
                    String databaseMessage = rootCauseMessage(e);
                    log.error("Vector store database write failed for document '{}' — {}", title, databaseMessage, e);
                    throw new OpenAIApiException(
                        "The document was read, but PostgreSQL rejected the vector-store write. "
                        + "Check that pgvector is enabled and vector_store.embedding matches the "
                        + "embedding model output dimension (768 for text-embedding-004), with metadata as JSONB. "
                        + "Database details: " + databaseMessage,
                        HttpStatus.SERVICE_UNAVAILABLE, e);
        } catch (Exception e) {
            // ── Generic fallback: log the full cause chain ──
            e.printStackTrace();
            System.err.println("Vector store error: " + e.getMessage());
            log.error("Embedding/storage failed for document '{}' — cause chain: {}",
                    title, summarizeCauseChain(e), e);
            rollbackIngestion(document.getId());
            OpenAIErrorInfo errorInfo = extractOpenAIError(e);
            throw new OpenAIApiException(errorInfo.userMessage, errorInfo.httpStatus, e);
        }

        log.info("Indexed knowledge document '{}' ({} chunks)", title, vectorDocs.size());
        return toDocumentDto(document, vectorDocs.size());
    }

    /** Roll back metadata rows on embedding failure. */
    private void rollbackIngestion(Long documentId) {
        try {
            chunkRepository.deleteByDocumentId(documentId);
            documentRepository.deleteById(documentId);
        } catch (Exception rollbackEx) {
            log.error("Failed to roll back ingestion for document {}: {}",
                    documentId, rollbackEx.getMessage(), rollbackEx);
        }
    }

    // ------------------------------------------------------------------
    // Admin queries
    // ------------------------------------------------------------------

    public List<KnowledgeDocumentDto> listDocuments() {
        return documentRepository.findAllByOrderByCreatedAtDesc().stream()
                .map(d -> toDocumentDto(d, chunkRepository.countByDocumentId(d.getId())))
                .toList();
    }

    public List<KnowledgeChunkDto> listChunks() {
        Map<Long, KnowledgeDocument> docs = documentRepository.findAll().stream()
                .collect(Collectors.toMap(KnowledgeDocument::getId, Function.identity()));
        return chunkRepository.findAllByOrderByIdAsc().stream()
                .map(c -> {
                    KnowledgeDocument doc = docs.get(c.getDocumentId());
                    return new KnowledgeChunkDto(
                            c.getId(),
                            c.getDocumentId(),
                            doc != null ? doc.getTitle() : "deleted document",
                            doc != null ? doc.getSourceType() : "UNKNOWN",
                            c.getChunkIndex(),
                            c.getContent(),
                            c.getCreatedAt());
                })
                .toList();
    }

    /** Remove a document and all of its chunks from the vector store + metadata tables. */
    @PreAuthorize("hasRole('ADMIN')")
    @Transactional
    public void deleteDocument(Long id) {
        KnowledgeDocument document = documentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Knowledge document not found: " + id));

        List<KnowledgeChunk> chunks = chunkRepository.findByDocumentIdOrderByChunkIndexAsc(id);
        List<String> vectorIds = chunks.stream()
                .map(c -> vectorIdForChunk(c.getId()))
                .toList();

        if (!vectorIds.isEmpty()) {
            try {
                vectorStore.delete(vectorIds);
            } catch (Exception e) {
                // Deletion needs no embeddings, but if the store is unreachable
                // the metadata rows are still removed below — log and continue.
                log.warn("Could not remove vectors for document '{}' — removing metadata rows only", id, e);
            }
        }

        chunkRepository.deleteByDocumentId(id);
        documentRepository.delete(document);
        log.info("Deleted knowledge document '{}' ({} chunks)", document.getTitle(), chunks.size());
    }

    // ------------------------------------------------------------------
    // RAG retrieval (used by ChatService)
    // ------------------------------------------------------------------

    /**
     * Retrieve the top-K most relevant knowledge base chunks for a user
     * message. Returns an empty string when the retrieval is unavailable
     * (no API key, store unreachable, or nothing relevant) so the chat
     * service can fall back to a plain prompt.
     */
    public String retrieveContext(String query) {
        try {
            List<Document> results = vectorStore.similaritySearch(
                    SearchRequest.builder().query(query).topK(RETRIEVAL_TOP_K).build());
            if (results.isEmpty()) {
                return "";
            }
            return results.stream()
                    .map(d -> "- " + d.getText().replaceAll("\\s+", " ").trim())
                    .collect(Collectors.joining("\n"));
        } catch (Exception e) {
            // Never throws and never fails silently: log the exact failure and
            // return "" so the chat falls back to a context-free prompt. An
            // empty vector store is NOT an error — similaritySearch just returns
            // an empty list and this method returns "".
            log.warn("Vector retrieval failed ({}: {}); answering without knowledge base context",
                    e.getClass().getSimpleName(), e.getMessage(), e);
            return "";
        }
    }

    private KnowledgeDocumentDto toDocumentDto(KnowledgeDocument d, long chunkCount) {
        return new KnowledgeDocumentDto(
                d.getId(), d.getTitle(), d.getSourceType(), d.getFileName(),
                chunkCount, d.getCreatedAt());
    }

    // ------------------------------------------------------------------
    // Error formatting helpers
    // ------------------------------------------------------------------

    /**
     * Format a user-friendly message from a RestClientResponseException,
     * extracting the exact OpenAI error from the JSON response body.
     */
    private static String formatRestClientError(RestClientResponseException e) {
        int status = e.getRawStatusCode();
        String body = e.getResponseBodyAsString();
        String openaiMsg = extractOpenAIErrorMessage(body);
        return formatByStatus(status, openaiMsg);
    }

    /** Format from any HttpStatusCodeException. */
    private static String formatHttpStatusCodeError(HttpStatusCodeException e) {
        int status = e.getRawStatusCode();
        String body = e.getResponseBodyAsString();
        String openaiMsg = extractOpenAIErrorMessage(body);
        return formatByStatus(status, openaiMsg);
    }

    /** Map an HTTP status code + AI provider error message to a clear user-facing string. */
    private static String formatByStatus(int status, String openaiMsg) {
        String suffix = openaiMsg != null && !openaiMsg.isBlank() ? " — " + openaiMsg : "";
        return switch (status) {
            case 401 -> "Invalid AI API Key (401 Unauthorized). "
                    + "Your key may be missing, expired, or incorrectly formatted. "
                    + "Generate a new key at https://aistudio.google.com/apikey" + suffix;
            case 403 -> "AI API access denied (403 Forbidden). "
                    + "Your API key may not have permission for the embeddings endpoint. "
                    + "Check your key's permissions at https://aistudio.google.com/apikey" + suffix;
            case 429 -> "AI API rate limit exceeded (429). "
                    + "Wait a moment and try again, or check your quota "
                    + "at https://aistudio.google.com" + suffix;
            case 404 -> "AI model not found (404). "
                    + "Verify that the embedding model name is correct in application.properties "
                    + "(spring.ai.google.genai.embedding.options.model)" + suffix;
            default -> {
                if (status >= 400 && status < 500) {
                    yield "AI provider client error (HTTP " + status + "). "
                            + "Check your API key and request parameters" + suffix;
                }
                if (status >= 500) {
                    yield "AI provider server error (HTTP " + status + "). "
                            + "This is an issue on the provider's side — try again shortly" + suffix;
                }
                yield "Embedding generation failed (HTTP " + status + ")" + suffix;
            }
        };
    }

    /**
     * Walk the exception cause chain and extract the actual OpenAI HTTP error.
     * Used as a fallback when the top-level exception is not a direct HTTP type.
     */
    private record OpenAIErrorInfo(String userMessage, HttpStatus httpStatus) {
        static OpenAIErrorInfo generic(String msg) {
            return new OpenAIErrorInfo(msg, HttpStatus.BAD_REQUEST);
        }
    }

    private OpenAIErrorInfo extractOpenAIError(Exception e) {
        if (isApiKeyMissing()) {
            return OpenAIErrorInfo.generic(
                    "GEMINI_API_KEY is not configured or is invalid. "
                    + "Set the environment variable to a valid key from "
                    + "https://aistudio.google.com/apikey and restart the application.");
        }

        // Walk the cause chain looking for the actual HTTP-level failure
        Throwable cause = e;
        while (cause != null) {
            if (cause instanceof RestClientResponseException rcre) {
                log.error("Gemini embedding API returned HTTP {} — body: {}",
                        rcre.getRawStatusCode(), rcre.getResponseBodyAsString(), rcre);
                return OpenAIErrorInfo.generic(formatRestClientError(rcre));
            }
            if (cause instanceof HttpStatusCodeException hce) {
                log.error("Gemini embedding API returned HTTP {} — body: {}",
                        hce.getRawStatusCode(), hce.getResponseBodyAsString(), hce);
                return OpenAIErrorInfo.generic(formatHttpStatusCodeError(hce));
            }
            if (cause instanceof ResourceAccessException) {
                return OpenAIErrorInfo.generic(
                        "Could not connect to the Gemini API — check your network "
                        + "connection and firewall settings."
                        + (cause.getMessage() != null ? " Details: " + cause.getMessage() : ""));
            }
            cause = cause.getCause();
        }

        // No HTTP exception found — fall back to message-based detection
        String msg = lower(e.getMessage());
        if (msg != null && msg.contains("text/plain")) {
            return OpenAIErrorInfo.generic(
                    "The AI provider returned a non-JSON response. This usually "
                    + "means the API key is invalid or has been deactivated. "
                    + "Verify your key at https://aistudio.google.com/apikey.");
        }
        if (msg != null && msg.contains("insufficient_quota")) {
            return OpenAIErrorInfo.generic(
                    "AI provider account has insufficient quota. "
                    + "Check your quota at https://aistudio.google.com.");
        }
        if (msg != null && msg.contains("model_not_found")) {
            return OpenAIErrorInfo.generic(
                    "AI embedding model not found. "
                    + "Verify spring.ai.google.genai.embedding.options.model in application.properties.");
        }

        log.error("Embedding failure could not be mapped to a known AI provider error. "
                + "Exception chain: {}", summarizeCauseChain(e));
        return OpenAIErrorInfo.generic("Embedding generation failed: " + e.getMessage());
    }

    /**
     * Try to parse the human-readable message from an OpenAI JSON error body.
     * OpenAI errors: {"error":{"message":"...","type":"...","code":"..."}}
     */
    private static String extractOpenAIErrorMessage(String body) {
        if (body == null || body.isBlank()) return null;
        // Primary: extract from JSON "message" field
        int msgIdx = body.indexOf("\"message\":\"");
        if (msgIdx >= 0) {
            int start = msgIdx + 12;
            int end = body.indexOf('\"', start);
            if (end > start) return body.substring(start, end);
        }
        // Fallback: return the first 200 chars
        return truncate(body, 200);
    }

    /** Walk the exception cause chain and return a compact summary for logging. */
    private static String summarizeCauseChain(Throwable t) {
        StringBuilder sb = new StringBuilder();
        Throwable current = t;
        int depth = 0;
        while (current != null && depth < 6) {
            if (depth > 0) sb.append(" → ");
            sb.append(current.getClass().getSimpleName());
            if (current.getMessage() != null) {
                sb.append(": ").append(truncate(current.getMessage(), 120));
            }
            current = current.getCause();
            depth++;
        }
        return sb.toString();
    }

    /** Generate a deterministic UUID from a knowledge chunk ID.
     *  Uses UUID.nameUUIDFromBytes so the same chunk always maps to the
     *  same vector-store ID (needed for delete operations).
     */
    private static String vectorIdForChunk(Long chunkId) {
        return UUID.nameUUIDFromBytes(("kb-" + chunkId).getBytes(java.nio.charset.StandardCharsets.UTF_8)).toString();
    }

    private static String lower(String s) {
        return s == null ? null : s.toLowerCase(Locale.ROOT);
    }

    private static String truncate(String s, int maxLen) {
        if (s == null) return null;
        return s.length() <= maxLen ? s : s.substring(0, maxLen) + "\u2026";
    }

    private static String rootCauseMessage(Throwable error) {
        Throwable cause = error;
        while (cause.getCause() != null) {
            cause = cause.getCause();
        }
        return cause.getMessage() == null ? error.getMessage() : cause.getMessage();
    }
}
