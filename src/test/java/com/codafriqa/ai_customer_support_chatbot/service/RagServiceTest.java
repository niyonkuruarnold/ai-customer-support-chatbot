package com.codafriqa.ai_customer_support_chatbot.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.Filter;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for {@link RagService}: citation extraction from vector store
 * metadata and graceful fallback when retrieval fails.
 */
@ExtendWith(MockitoExtension.class)
class RagServiceTest {

    /** Minimal VectorStore returning a fixed result list. */
    static class StubVectorStore implements VectorStore {
        private final List<Document> results;

        StubVectorStore(List<Document> results) {
            this.results = results;
        }

        @Override
        public void add(List<Document> documents) {}

        @Override
        public void delete(List<String> ids) {}

        @Override
        public void delete(Filter.Expression expression) {}

        @Override
        public List<Document> similaritySearch(SearchRequest request) {
            return results;
        }
    }

    /** VectorStore that always throws to simulate an unavailable store. */
    static class ThrowingVectorStore implements VectorStore {
        @Override
        public void add(List<Document> documents) {}

        @Override
        public void delete(List<String> ids) {}

        @Override
        public void delete(Filter.Expression expression) {}

        @Override
        public List<Document> similaritySearch(SearchRequest request) {
            throw new IllegalStateException("vector store unavailable");
        }
    }

    @Test
    void retrieveContextExtractsCitationsFromDocumentMetadata() {
        Document doc1 = new Document("Returns are accepted within 30 days.",
                Map.of("documentId", 10L, "title", "Returns Policy", "sourceType", "TEXT"));
        Document doc2 = new Document("Shipping takes 3-5 business days.",
                Map.of("documentId", 20L, "title", "Shipping Guide", "sourceType", "PDF"));

        RagService ragService = new RagService(new StubVectorStore(List.of(doc1, doc2)), null, "test-api-key");
        RagService.RagContext ctx = ragService.retrieveContext("return policy");

        assertFalse(ctx.contextText().isBlank());
        assertEquals(2, ctx.references().size());

        assertEquals(10L, ctx.references().get(0).documentId());
        assertEquals("Returns Policy", ctx.references().get(0).title());
        assertEquals("TEXT", ctx.references().get(0).sourceType());

        assertEquals(20L, ctx.references().get(1).documentId());
        assertEquals("Shipping Guide", ctx.references().get(1).title());
        assertEquals("PDF", ctx.references().get(1).sourceType());
    }

    @Test
    void toCitationsMapsReferencesToSourceCitationDto() {
        Document doc = new Document("Refund within 7 days.",
                Map.of("documentId", 42L, "title", "Refund Policy", "sourceType", "MARKDOWN"));

        RagService ragService = new RagService(new StubVectorStore(List.of(doc)), null, "test-api-key");
        RagService.RagContext ctx = ragService.retrieveContext("refund");

        var citations = ctx.toCitations();
        assertEquals(1, citations.size());
        assertEquals(42L, citations.get(0).sourceId());
        assertEquals("Refund Policy", citations.get(0).title());
        assertEquals("MARKDOWN", citations.get(0).sourceType());
    }

    @Test
    void retrieveContextDeduplicatesByDocumentId() {
        Document doc1 = new Document("Chunk A",
                Map.of("documentId", 10L, "title", "Returns Policy", "sourceType", "TEXT"));
        Document doc2 = new Document("Chunk B",
                Map.of("documentId", 10L, "title", "Returns Policy", "sourceType", "TEXT"));
        Document doc3 = new Document("Chunk C",
                Map.of("documentId", 20L, "title", "Shipping Guide", "sourceType", "PDF"));

        RagService ragService = new RagService(new StubVectorStore(List.of(doc1, doc2, doc3)), null, "test-api-key");
        RagService.RagContext ctx = ragService.retrieveContext("returns");

        assertEquals(2, ctx.references().size());
        assertEquals(10L, ctx.references().get(0).documentId());
        assertEquals(20L, ctx.references().get(1).documentId());
    }

    @Test
    void retrieveContextSkipsDocumentsWithoutDocumentId() {
        Document docWithId = new Document("Has metadata",
                Map.of("documentId", 5L, "title", "Policy", "sourceType", "TEXT"));
        Document docWithoutId = new Document("No metadata key");

        RagService ragService = new RagService(new StubVectorStore(List.of(docWithId, docWithoutId)), null, "test-api-key");
        RagService.RagContext ctx = ragService.retrieveContext("policy");

        assertEquals(1, ctx.references().size());
        assertEquals(5L, ctx.references().get(0).documentId());
    }

    @Test
    void retrieveContextReturnsEmptyWhenNoResults() {
        RagService ragService = new RagService(new StubVectorStore(List.of()), null, "test-api-key");
        RagService.RagContext ctx = ragService.retrieveContext("nonexistent");

        assertTrue(ctx.contextText().isBlank());
        assertTrue(ctx.references().isEmpty());
    }

    @Test
    void retrieveContextFallsBackGracefullyOnStoreFailure() {
        RagService ragService = new RagService(new ThrowingVectorStore(), null, "test-api-key");
        RagService.RagContext ctx = ragService.retrieveContext("anything");

        assertNotNull(ctx);
        assertTrue(ctx.contextText().isBlank());
        assertTrue(ctx.references().isEmpty());
    }

    @Test
    void emptyContextHasNoCitations() {
        RagService.RagContext empty = RagService.RagContext.empty();

        assertTrue(empty.contextText().isBlank());
        assertTrue(empty.references().isEmpty());
        assertTrue(empty.toCitations().isEmpty());
    }

    @Test
    void retrieveContextReturnsMockContextWithCitationsWhenApiKeyIsMissing() {
        RagService ragService = new RagService(new StubVectorStore(List.of()), null, null);
        RagService.RagContext ctx = ragService.retrieveContext("anything");

        assertNotNull(ctx);
        assertFalse(ctx.contextText().isBlank());
        assertTrue(ctx.contextText().contains("Code of Africa"));

        // Mock context must carry citations so the frontend citation UI can be tested.
        assertFalse(ctx.references().isEmpty());
        assertEquals(3, ctx.references().size());
        assertEquals("Code of Africa — Products & Services", ctx.references().get(0).title());
        assertEquals("TEXT", ctx.references().get(0).sourceType());
        assertEquals("Code of Africa — RAG Chatbot Architecture", ctx.references().get(1).title());
        assertEquals("MARKDOWN", ctx.references().get(1).sourceType());
        assertEquals("Code of Africa — Contact & Support", ctx.references().get(2).title());
        assertEquals("PDF", ctx.references().get(2).sourceType());
    }

    @Test
    void retrieveContextReturnsMockContextWhenApiKeyIsPlaceholder() {
        RagService ragService = new RagService(new StubVectorStore(List.of()), null, "your-api-key-here");
        RagService.RagContext ctx = ragService.retrieveContext("anything");

        assertNotNull(ctx);
        assertFalse(ctx.contextText().isBlank());
        assertTrue(ctx.contextText().contains("Code of Africa"));
        assertFalse(ctx.references().isEmpty());
    }

    @Test
    void mockContextHasCitationsForCitationFlowTesting() {
        RagService.RagContext mockCtx = RagService.RagContext.mockContext();

        assertFalse(mockCtx.contextText().isBlank());
        assertFalse(mockCtx.references().isEmpty());
        assertEquals(3, mockCtx.references().size());

        // toCitations() must map references into SourceCitationDto objects
        // so the frontend citation rendering can be exercised end-to-end.
        var citations = mockCtx.toCitations();
        assertEquals(3, citations.size());
        assertEquals("Code of Africa — Products & Services", citations.get(0).title());
        assertEquals("TEXT", citations.get(0).sourceType());
    }
}
