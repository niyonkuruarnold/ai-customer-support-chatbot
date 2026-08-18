package com.codafriqa.ai_customer_support_chatbot.service;

import com.codafriqa.ai_customer_support_chatbot.model.KnowledgeChunk;
import com.codafriqa.ai_customer_support_chatbot.model.KnowledgeDocument;
import com.codafriqa.ai_customer_support_chatbot.model.SystemScanLog;
import com.codafriqa.ai_customer_support_chatbot.repository.KnowledgeChunkRepository;
import com.codafriqa.ai_customer_support_chatbot.repository.KnowledgeDocumentRepository;
import com.codafriqa.ai_customer_support_chatbot.repository.SystemScanLogRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.Filter;
import org.springframework.scheduling.annotation.Scheduled;

import java.lang.reflect.Method;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SystemScannerServiceTest {

    @Mock
    private KnowledgeDocumentRepository documentRepository;

    @Mock
    private KnowledgeChunkRepository chunkRepository;

    @Mock
    private SystemScanLogRepository scanLogRepository;

    /**
     * Minimal VectorStore stub that records added documents.
     * Mockito cannot mock VectorStore on Java 26 (ByteBuddy limitation),
     * so we use a hand-rolled stub like RagServiceTest does.
     */
    static class StubVectorStore implements VectorStore {
        final List<Document> added = new ArrayList<>();
        RuntimeException failureToThrow;

        @Override
        public void add(List<Document> documents) {
            if (failureToThrow != null) throw failureToThrow;
            added.addAll(documents);
        }

        @Override
        public void delete(List<String> ids) {}

        @Override
        public void delete(Filter.Expression expression) {}

        @Override
        public List<Document> similaritySearch(SearchRequest request) {
            return List.of();
        }
    }

    private StubVectorStore stubVectorStore;
    private SystemScannerService service;

    @BeforeEach
    void setUp() {
        stubVectorStore = new StubVectorStore();
        service = new SystemScannerService(
                documentRepository, chunkRepository, scanLogRepository, stubVectorStore);
    }

    // ---- @Scheduled annotation ----

    @Test
    void executeScanMethodHasScheduledAnnotation() throws NoSuchMethodException {
        Method method = SystemScannerService.class.getMethod("runScheduledScan");
        Scheduled scheduled = method.getAnnotation(Scheduled.class);
        assertNotNull(scheduled, "runScheduledScan must have @Scheduled annotation");
        assertEquals("0 */5 * * * *", scheduled.cron());
    }

    // ---- executeScan: no unindexed documents ----

    @Test
    void executeScanWithNoUnindexedDocumentsRecordsCompletedWithZeroCounts() {
        when(documentRepository.findUnindexedOrUpdated()).thenReturn(List.of());
        when(scanLogRepository.save(any(SystemScanLog.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        SystemScanLog result = service.executeScan();

        assertEquals(SystemScanLog.ScanStatus.COMPLETED, result.getStatus());
        assertEquals(0L, result.getRecordsScanned());
        assertEquals(0L, result.getRecordsIndexed());
        assertEquals(0L, result.getChunksCreated());
        assertEquals("No documents need indexing", result.getMessage());
        assertNotNull(result.getDurationMs());
        assertTrue(result.getDurationMs() >= 0);
        assertTrue(stubVectorStore.added.isEmpty());
    }

    // ---- executeScan: documents with chunks ----

    @Test
    void executeScanIndexesDocumentsAndRecordsMetrics() {
        KnowledgeDocument doc = new KnowledgeDocument("Returns Policy", "TEXT", null);
        doc.setId(1L);

        KnowledgeChunk chunk = new KnowledgeChunk(1L, 0, "Returns are accepted within 30 days.");
        chunk.setId(100L);

        when(documentRepository.findUnindexedOrUpdated()).thenReturn(List.of(doc));
        when(chunkRepository.findByDocumentIdOrderByChunkIndexAsc(1L)).thenReturn(List.of(chunk));
        when(scanLogRepository.save(any(SystemScanLog.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        SystemScanLog result = service.executeScan();

        assertEquals(SystemScanLog.ScanStatus.COMPLETED, result.getStatus());
        assertEquals(1L, result.getRecordsScanned());
        assertEquals(1L, result.getRecordsIndexed());
        assertTrue(result.getChunksCreated() >= 1);
        assertNotNull(result.getMessage());
        assertTrue(result.getMessage().contains("1 of 1"));

        // Verify metadata was attached to the vector document
        assertFalse(stubVectorStore.added.isEmpty());
        Document storedDoc = stubVectorStore.added.get(0);
        assertEquals("Returns are accepted within 30 days.", storedDoc.getText());
        assertEquals(1L, storedDoc.getMetadata().get("source_id"));
        assertEquals("TEXT", storedDoc.getMetadata().get("source_type"));
        assertEquals(1L, storedDoc.getMetadata().get("documentId"));
        assertEquals("Returns Policy", storedDoc.getMetadata().get("title"));
        assertNotNull(storedDoc.getMetadata().get("scanned_at"));

        // Verify document is marked as indexed
        verify(documentRepository).save(doc);
        assertNotNull(doc.getIndexedAt());
    }

    // ---- executeScan: multiple documents with multiple chunks ----

    @Test
    void executeScanHandlesMultipleDocumentsAndChunks() {
        KnowledgeDocument doc1 = new KnowledgeDocument("Shipping Guide", "PDF", "shipping.pdf");
        doc1.setId(1L);
        KnowledgeDocument doc2 = new KnowledgeDocument("Refund Policy", "TEXT", null);
        doc2.setId(2L);

        KnowledgeChunk chunk1 = new KnowledgeChunk(1L, 0, "Shipping takes 3-5 business days.");
        chunk1.setId(10L);
        KnowledgeChunk chunk2 = new KnowledgeChunk(1L, 1, "Express shipping is available.");
        chunk2.setId(11L);
        KnowledgeChunk chunk3 = new KnowledgeChunk(2L, 0, "Refunds processed within 7 days.");
        chunk3.setId(20L);

        when(documentRepository.findUnindexedOrUpdated()).thenReturn(List.of(doc1, doc2));
        when(chunkRepository.findByDocumentIdOrderByChunkIndexAsc(1L)).thenReturn(List.of(chunk1, chunk2));
        when(chunkRepository.findByDocumentIdOrderByChunkIndexAsc(2L)).thenReturn(List.of(chunk3));
        when(scanLogRepository.save(any(SystemScanLog.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        SystemScanLog result = service.executeScan();

        assertEquals(SystemScanLog.ScanStatus.COMPLETED, result.getStatus());
        assertEquals(2L, result.getRecordsScanned());
        assertEquals(2L, result.getRecordsIndexed());
        assertTrue(result.getChunksCreated() >= 2);

        // Both documents should be marked as indexed
        assertNotNull(doc1.getIndexedAt());
        assertNotNull(doc2.getIndexedAt());

        // All chunks from both documents should have been added to the vector store
        assertTrue(stubVectorStore.added.size() >= 3);
    }

    // ---- executeScan: document with no chunks is skipped ----

    @Test
    void executeScanSkipsDocumentsWithNoChunks() {
        KnowledgeDocument doc = new KnowledgeDocument("Empty Doc", "TEXT", null);
        doc.setId(1L);

        when(documentRepository.findUnindexedOrUpdated()).thenReturn(List.of(doc));
        when(chunkRepository.findByDocumentIdOrderByChunkIndexAsc(1L)).thenReturn(List.of());
        when(scanLogRepository.save(any(SystemScanLog.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        SystemScanLog result = service.executeScan();

        assertEquals(SystemScanLog.ScanStatus.COMPLETED, result.getStatus());
        assertEquals(1L, result.getRecordsScanned());
        assertEquals(0L, result.getRecordsIndexed());
        assertEquals(0L, result.getChunksCreated());
        assertTrue(stubVectorStore.added.isEmpty());
    }

    // ---- executeScan: vector store failure ----

    @Test
    void executeScanRecordsFailureWhenVectorStoreThrows() {
        KnowledgeDocument doc = new KnowledgeDocument("Policy", "TEXT", null);
        doc.setId(1L);

        KnowledgeChunk chunk = new KnowledgeChunk(1L, 0, "Some policy text.");
        chunk.setId(50L);

        stubVectorStore.failureToThrow = new RuntimeException("Embedding service unavailable");

        when(documentRepository.findUnindexedOrUpdated()).thenReturn(List.of(doc));
        when(chunkRepository.findByDocumentIdOrderByChunkIndexAsc(1L)).thenReturn(List.of(chunk));
        when(scanLogRepository.save(any(SystemScanLog.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        SystemScanLog result = service.executeScan();

        // The per-document try-catch swallows the error, so the overall scan
        // still completes — but the document is NOT indexed.
        assertEquals(SystemScanLog.ScanStatus.COMPLETED, result.getStatus());
        assertEquals(1L, result.getRecordsScanned());
        assertEquals(0L, result.getRecordsIndexed());
        assertNull(doc.getIndexedAt(), "Document should not be marked as indexed after vector store failure");
    }

    // ---- executeScan: repository query failure ----

    @Test
    void executeScanRecordsFailureWhenRepositoryThrows() {
        when(documentRepository.findUnindexedOrUpdated())
                .thenThrow(new RuntimeException("Database connection lost"));
        when(scanLogRepository.save(any(SystemScanLog.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        SystemScanLog result = service.executeScan();

        assertEquals(SystemScanLog.ScanStatus.FAILED, result.getStatus());
        assertTrue(result.getMessage().contains("Scan failed"));
        assertTrue(result.getMessage().contains("Database connection lost"));
    }

    // ---- getLatestScan ----

    @Test
    void getLatestScanDelegatesToRepository() {
        SystemScanLog log = new SystemScanLog();
        log.setStatus(SystemScanLog.ScanStatus.COMPLETED);
        log.setScannedAt(LocalDateTime.now());
        when(scanLogRepository.findLatestFinishedScan()).thenReturn(Optional.of(log));

        SystemScanLog result = service.getLatestScan();

        assertNotNull(result);
        assertEquals(SystemScanLog.ScanStatus.COMPLETED, result.getStatus());
        verify(scanLogRepository).findLatestFinishedScan();
    }

    @Test
    void getLatestScanReturnsNullWhenNoScansExist() {
        when(scanLogRepository.findLatestFinishedScan()).thenReturn(Optional.empty());

        SystemScanLog result = service.getLatestScan();

        assertNull(result);
    }

    // ---- executeScan: scan log is persisted at start and end ----

    @Test
    void executeScanSavesLogTwiceOnceAtStartAndOnceAtEnd() {
        when(documentRepository.findUnindexedOrUpdated()).thenReturn(List.of());

        List<SystemScanLog.ScanStatus> snapshots = new ArrayList<>();
        when(scanLogRepository.save(any(SystemScanLog.class)))
                .thenAnswer(inv -> {
                    // Deep-copy the status before the caller mutates it
                    SystemScanLog log = inv.getArgument(0);
                    snapshots.add(log.getStatus());
                    return log;
                });

        service.executeScan();

        // Once for the RUNNING record, once for the COMPLETED record
        verify(scanLogRepository, times(2)).save(any(SystemScanLog.class));
        assertEquals(2, snapshots.size());
        assertEquals(SystemScanLog.ScanStatus.RUNNING, snapshots.get(0));
        assertEquals(SystemScanLog.ScanStatus.COMPLETED, snapshots.get(1));
    }

    // ---- executeScan: documents without chunks don't crash the scan ----

    @Test
    void executeScanCompletesEvenWhenSomeDocumentsHaveNoChunks() {
        KnowledgeDocument docWithChunks = new KnowledgeDocument("Good Doc", "TEXT", null);
        docWithChunks.setId(1L);
        KnowledgeDocument docWithoutChunks = new KnowledgeDocument("Empty Doc", "MARKDOWN", null);
        docWithoutChunks.setId(2L);

        KnowledgeChunk chunk = new KnowledgeChunk(1L, 0, "Some content here.");
        chunk.setId(10L);

        when(documentRepository.findUnindexedOrUpdated()).thenReturn(List.of(docWithChunks, docWithoutChunks));
        when(chunkRepository.findByDocumentIdOrderByChunkIndexAsc(1L)).thenReturn(List.of(chunk));
        when(chunkRepository.findByDocumentIdOrderByChunkIndexAsc(2L)).thenReturn(List.of());
        when(scanLogRepository.save(any(SystemScanLog.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        SystemScanLog result = service.executeScan();

        assertEquals(SystemScanLog.ScanStatus.COMPLETED, result.getStatus());
        assertEquals(2L, result.getRecordsScanned());
        assertEquals(1L, result.getRecordsIndexed());
        assertTrue(result.getChunksCreated() >= 1);
    }
}
