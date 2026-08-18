package com.codafriqa.ai_customer_support_chatbot.service;

import com.codafriqa.ai_customer_support_chatbot.model.KnowledgeChunk;
import com.codafriqa.ai_customer_support_chatbot.model.KnowledgeDocument;
import com.codafriqa.ai_customer_support_chatbot.model.SystemScanLog;
import com.codafriqa.ai_customer_support_chatbot.repository.KnowledgeChunkRepository;
import com.codafriqa.ai_customer_support_chatbot.repository.KnowledgeDocumentRepository;
import com.codafriqa.ai_customer_support_chatbot.repository.SystemScanLogRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Automated system scanner that periodically scans knowledge documents
 * and ingests un-indexed or updated records into the pgvector store.
 *
 * Runs every 5 minutes. Each scan:
 *   1. Queries KnowledgeDocuments where indexedAt IS NULL or updatedAt > indexedAt
 *   2. Uses Spring AI TokenTextSplitter to chunk text content
 *   3. Adds metadata (source_id, source_type, scanned_at) to each Document chunk
 *   4. Saves embeddings to the pgvector VectorStore
 *   5. Records a SystemScanLog entry with timestamps, counts, and status
 *
 * The scanner uses the same TokenTextSplitter configuration as
 * KnowledgeBaseService for consistency. Embedding failures are
 * caught and logged - the scan is marked as FAILED but does not crash
 * the application.
 */
@Service
public class SystemScannerService {

    private static final Logger log = LoggerFactory.getLogger(SystemScannerService.class);

    private static final TokenTextSplitter SPLITTER = TokenTextSplitter.builder()
            .withChunkSize(500)
            .withMinChunkSizeChars(350)
            .withMinChunkLengthToEmbed(5)
            .withMaxNumChunks(10000)
            .build();

    private final KnowledgeDocumentRepository documentRepository;
    private final KnowledgeChunkRepository chunkRepository;
    private final SystemScanLogRepository scanLogRepository;
    private final VectorStore vectorStore;

    public SystemScannerService(KnowledgeDocumentRepository documentRepository,
                                KnowledgeChunkRepository chunkRepository,
                                SystemScanLogRepository scanLogRepository,
                                VectorStore vectorStore) {
        this.documentRepository = documentRepository;
        this.chunkRepository = chunkRepository;
        this.scanLogRepository = scanLogRepository;
        this.vectorStore = vectorStore;
    }

    @Scheduled(cron = "0 */5 * * * *")
    public void runScheduledScan() {
        executeScan();
    }

    @Transactional
    public SystemScanLog executeScan() {
        long startTime = System.currentTimeMillis();
        SystemScanLog scanLog = new SystemScanLog();
        scanLog.setStatus(SystemScanLog.ScanStatus.RUNNING);
        scanLog.setRecordsScanned(0L);
        scanLog.setRecordsIndexed(0L);
        scanLog.setChunksCreated(0L);
        scanLog = scanLogRepository.save(scanLog);

        try {
            List<KnowledgeDocument> unindexedDocs = documentRepository.findUnindexedOrUpdated();

            scanLog.setRecordsScanned((long) unindexedDocs.size());

            if (unindexedDocs.isEmpty()) {
                scanLog.setStatus(SystemScanLog.ScanStatus.COMPLETED);
                scanLog.setMessage("No documents need indexing");
                scanLog.setDurationMs(System.currentTimeMillis() - startTime);
                return scanLogRepository.save(scanLog);
            }

            long totalChunks = 0;
            long indexedCount = 0;

            for (KnowledgeDocument doc : unindexedDocs) {
                try {
                    List<KnowledgeChunk> chunks = chunkRepository.findByDocumentIdOrderByChunkIndexAsc(doc.getId());

                    if (chunks.isEmpty()) {
                        log.debug("Document '{}' has no chunks in metadata - skipping", doc.getTitle());
                        continue;
                    }

                    List<Document> vectorDocs = new ArrayList<>(chunks.size());
                    for (KnowledgeChunk chunk : chunks) {
                        Map<String, Object> metadata = new HashMap<>();
                        metadata.put("source_id", doc.getId());
                        metadata.put("source_type", doc.getSourceType());
                        metadata.put("scanned_at", LocalDateTime.now().toString());
                        metadata.put("documentId", doc.getId());
                        metadata.put("title", doc.getTitle());
                        metadata.put("chunkIndex", chunk.getChunkIndex());

                        vectorDocs.add(new Document(
                                "kb-" + chunk.getId(),
                                chunk.getContent(),
                                metadata));
                    }

                    List<Document> splitDocs = SPLITTER.split(vectorDocs);

                    vectorStore.add(splitDocs);

                    doc.setIndexedAt(LocalDateTime.now());
                    documentRepository.save(doc);

                    totalChunks += splitDocs.size();
                    indexedCount++;

                    log.debug("Indexed document '{}' ({} chunks)", doc.getTitle(), splitDocs.size());

                } catch (Exception e) {
                    log.warn("Failed to index document '{}': {}", doc.getTitle(), e.getMessage(), e);
                }
            }

            scanLog.setRecordsIndexed(indexedCount);
            scanLog.setChunksCreated(totalChunks);
            scanLog.setStatus(SystemScanLog.ScanStatus.COMPLETED);
            scanLog.setMessage(String.format("Indexed %d of %d documents (%d chunks)",
                    indexedCount, unindexedDocs.size(), totalChunks));
            scanLog.setDurationMs(System.currentTimeMillis() - startTime);

            log.info("System scan complete: indexed {}/{} documents ({} chunks) in {}ms",
                    indexedCount, unindexedDocs.size(), totalChunks, scanLog.getDurationMs());

        } catch (Exception e) {
            scanLog.setStatus(SystemScanLog.ScanStatus.FAILED);
            scanLog.setMessage("Scan failed: " + e.getMessage());
            scanLog.setDurationMs(System.currentTimeMillis() - startTime);
            log.error("System scan failed: {}", e.getMessage(), e);
        }

        return scanLogRepository.save(scanLog);
    }

    public SystemScanLog getLatestScan() {
        return scanLogRepository.findLatestFinishedScan().orElse(null);
    }
}
