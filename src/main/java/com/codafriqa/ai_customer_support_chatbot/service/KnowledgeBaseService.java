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
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
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

    public KnowledgeBaseService(VectorStore vectorStore,
                                KnowledgeDocumentRepository documentRepository,
                                KnowledgeChunkRepository chunkRepository) {
        this.vectorStore = vectorStore;
        this.documentRepository = documentRepository;
        this.chunkRepository = chunkRepository;
    }

    // ------------------------------------------------------------------
    // Ingestion
    // ------------------------------------------------------------------

    /** Index raw pasted FAQ/support text. */
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
     * (e.g. OPENAI_API_KEY is missing) the partial rows are rolled back and
     * a 400 is returned so the admin UI never lists unsearchable content.
     */
    private KnowledgeDocumentDto ingest(String title, String fileName, String sourceType,
                                        List<Document> parsed) {
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

            vectorDocs.add(new Document("kb-" + row.getId(), row.getContent(), metadata));
        }

        try {
            vectorStore.add(vectorDocs);
        } catch (Exception e) {
            log.warn("Embedding/storage failed for document '{}' — rolling back", title, e);
            chunkRepository.deleteByDocumentId(document.getId());
            documentRepository.delete(document);
            throw new OpenAIApiException(
                    "Could not generate embeddings for this document (is OPENAI_API_KEY set?). " +
                    e.getMessage(),
                    HttpStatus.BAD_REQUEST, e);
        }

        log.info("Indexed knowledge document '{}' ({} chunks)", title, vectorDocs.size());
        return toDocumentDto(document, vectorDocs.size());
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
    @Transactional
    public void deleteDocument(Long id) {
        KnowledgeDocument document = documentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Knowledge document not found: " + id));

        List<KnowledgeChunk> chunks = chunkRepository.findByDocumentIdOrderByChunkIndexAsc(id);
        List<String> vectorIds = chunks.stream()
                .map(c -> "kb-" + c.getId())
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
}
