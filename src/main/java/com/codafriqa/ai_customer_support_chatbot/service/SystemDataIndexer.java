package com.codafriqa.ai_customer_support_chatbot.service;

import com.codafriqa.ai_customer_support_chatbot.model.*;
import com.codafriqa.ai_customer_support_chatbot.repository.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Indexes core system entities (Tools, Maintenance Logs, Reservations,
 * Reviews, Support Tickets) into the pgvector vector store so the RAG
 * chat pipeline can retrieve them.
 *
 * <h3>Startup indexing</h3>
 * An {@link ApplicationReadyEvent} listener fetches all system entities,
 * maps each into a descriptive text string, wraps them as Spring AI
 * {@link Document}s, and saves them into the vector store. This runs
 * once after the application context is fully initialized.
 *
 * <h3>Real-time sync</h3>
 * JPA entity listeners ({@link SystemDataSyncListener}) call
 * {@link #syncEntity(Object)} on {@code @PostPersist/@PostUpdate} and
 * {@link #removeEntity(Object)} on {@code @PostRemove} to keep the
 * vector store in sync whenever a database record is created, updated,
 * or deleted.
 *
 * <h3>Embedding safety</h3>
 * When {@code OPENAI_API_KEY} is missing or invalid, indexing is
 * skipped entirely (no embedding API available). The sync methods are
 * no-ops so CRUD operations proceed normally.
 */
@Service
public class SystemDataIndexer {

    private static final Logger log = LoggerFactory.getLogger(SystemDataIndexer.class);

    /**
     * Metadata key stored on every vector document so that similarity
     * search results can be mapped back to the source entity.
     */
    public static final String META_ENTITY_TYPE = "entityType";
    public static final String META_ENTITY_ID = "entityId";

    /**
     * Prefix for all vector store IDs managed by this indexer, so we
     * can distinguish them from knowledge-base chunks ({@code "kb-*"}).
     */
    private static final String VEC_ID_PREFIX = "sys-";

    private final ToolRepository toolRepository;
    private final MaintenanceLogRepository maintenanceLogRepository;
    private final ReservationRepository reservationRepository;
    private final ReviewRepository reviewRepository;
    private final SupportTicketRepository supportTicketRepository;
    private final VectorStore vectorStore;
    private final boolean indexingEnabled;

    /**
     * Read-write lock that protects the startup flag so the sync
     * methods are no-ops until the initial indexing pass finishes.
     */
    private volatile boolean startupIndexingComplete = false;

    public SystemDataIndexer(ToolRepository toolRepository,
                             MaintenanceLogRepository maintenanceLogRepository,
                             ReservationRepository reservationRepository,
                             ReviewRepository reviewRepository,
                             SupportTicketRepository supportTicketRepository,
                             VectorStore vectorStore,
                             @Value("${spring.ai.openai.api-key:}") String openaiApiKey) {
        this.toolRepository = toolRepository;
        this.maintenanceLogRepository = maintenanceLogRepository;
        this.reservationRepository = reservationRepository;
        this.reviewRepository = reviewRepository;
        this.supportTicketRepository = supportTicketRepository;
        this.vectorStore = vectorStore;
        this.indexingEnabled = openaiApiKey != null && !openaiApiKey.isBlank()
                && !openaiApiKey.contains("your-") && !openaiApiKey.contains("sk-placeholder");
    }

    // ------------------------------------------------------------------
    // Startup indexing
    // ------------------------------------------------------------------

    /**
     * Runs once after the application context is fully ready. Indexes
     * every system entity into the vector store so the first RAG query
     * has data to retrieve.
     */
    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady() {
        if (!indexingEnabled) {
            log.info("OPENAI_API_KEY is not configured — skipping system data indexing");
            startupIndexingComplete = true;
            return;
        }

        long start = System.currentTimeMillis();
        try {
            long count = indexAllEntities();
            long elapsed = System.currentTimeMillis() - start;
            log.info("System data indexing complete: {} entities indexed in {}ms", count, elapsed);
        } catch (Exception e) {
            log.error("System data indexing failed: {}", e.getMessage(), e);
        } finally {
            startupIndexingComplete = true;
        }
    }

    /**
     * Fetch all system entities, map them to documents, and save to the
     * vector store. Returns the total number of indexed entities.
     */
    public long indexAllEntities() {
        List<Document> docs = new ArrayList<>();

        // Tools
        for (Tool tool : toolRepository.findAll()) {
            docs.add(toDocument(tool));
        }

        // Maintenance Logs
        for (MaintenanceLog log : maintenanceLogRepository.findAll()) {
            docs.add(toDocument(log));
        }

        // Reservations
        for (Reservation reservation : reservationRepository.findAll()) {
            docs.add(toDocument(reservation));
        }

        // Reviews
        for (Review review : reviewRepository.findAll()) {
            docs.add(toDocument(review));
        }

        // Support Tickets
        for (SupportTicket ticket : supportTicketRepository.findAll()) {
            docs.add(toDocument(ticket));
        }

        if (!docs.isEmpty()) {
            // Delete existing system-indexed documents first to avoid duplicates
            deleteAllSystemDocuments();
            vectorStore.add(docs);
        }

        return docs.size();
    }

    // ------------------------------------------------------------------
    // Real-time sync (called by SystemDataSyncListener)
    // ------------------------------------------------------------------

    /**
     * Re-index a single entity after it has been persisted or updated.
     * If startup indexing has not completed yet, this is a no-op (the
     * full re-index will handle it).
     */
    public void syncEntity(Object entity) {
        if (!indexingEnabled || !startupIndexingComplete) return;
        try {
            Document doc = toDocument(entity);
            if (doc == null) return;

            // Remove old version if it exists, then add the new one
            String vecId = doc.getId();
            vectorStore.delete(List.of(vecId));
            vectorStore.add(List.of(doc));

            log.debug("Synced {} to vector store", doc.getId());
        } catch (Exception e) {
            log.warn("Failed to sync {} to vector store: {}",
                    entity.getClass().getSimpleName(), e.getMessage(), e);
        }
    }

    /**
     * Remove an entity from the vector store after it has been deleted.
     * If startup indexing has not completed yet, this is a no-op.
     */
    public void removeEntity(Object entity) {
        if (!indexingEnabled || !startupIndexingComplete) return;
        try {
            String vecId = toVectorId(entity);
            if (vecId == null) return;

            vectorStore.delete(List.of(vecId));
            log.debug("Removed {} from vector store", vecId);
        } catch (Exception e) {
            log.warn("Failed to remove {} from vector store: {}",
                    entity.getClass().getSimpleName(), e.getMessage(), e);
        }
    }

    // ------------------------------------------------------------------
    // Entity → Document mapping
    // ------------------------------------------------------------------

    /**
     * Convert a system entity into a Spring AI {@link Document} suitable
     * for embedding and storage in the vector store.
     *
     * @return a Document, or null if the entity type is not supported
     */
    private Document toDocument(Object entity) {
        if (entity instanceof Tool tool) {
            return toDocument(tool);
        } else if (entity instanceof MaintenanceLog ml) {
            return toDocument(ml);
        } else if (entity instanceof Reservation reservation) {
            return toDocument(reservation);
        } else if (entity instanceof Review review) {
            return toDocument(review);
        } else if (entity instanceof SupportTicket ticket) {
            return toDocument(ticket);
        }
        return null;
    }

    private Document toDocument(Tool tool) {
        String text = "Tool: " + tool.getName()
                + " | Category: " + tool.getCategory()
                + " | Status: " + tool.getStatus()
                + (tool.getDescription() != null
                    ? " | Description: " + tool.getDescription()
                    : "");
        return buildDocument(VEC_ID_PREFIX + "tool-" + tool.getId(), text,
                "Tool", tool.getId());
    }

    private Document toDocument(MaintenanceLog ml) {
        String text = "Maintenance Log for Tool #" + ml.getToolId()
                + " | Date: " + ml.getServiceDate()
                + " | Description: " + ml.getDescription()
                + (ml.getCost() != null ? " | Cost: $" + ml.getCost() : "")
                + (ml.getNextServiceDue() != null
                    ? " | Next Service Due: " + ml.getNextServiceDue()
                    : "");
        return buildDocument(VEC_ID_PREFIX + "maintenance-" + ml.getId(), text,
                "MaintenanceLog", ml.getId());
    }

    private Document toDocument(Reservation r) {
        String text = "Reservation: Tool #" + r.getToolId()
                + " | Borrower #" + r.getBorrowerId()
                + " | Period: " + r.getStartDate() + " to " + r.getEndDate()
                + " | Status: " + r.getStatus()
                + (r.getNotes() != null ? " | Notes: " + r.getNotes() : "");
        return buildDocument(VEC_ID_PREFIX + "reservation-" + r.getId(), text,
                "Reservation", r.getId());
    }

    private Document toDocument(Review review) {
        String text = "Review for Tool #" + review.getToolId()
                + " | Rating: " + review.getRating() + "/5"
                + (review.getComment() != null
                    ? " | Comment: " + review.getComment()
                    : "");
        return buildDocument(VEC_ID_PREFIX + "review-" + review.getId(), text,
                "Review", review.getId());
    }

    private Document toDocument(SupportTicket ticket) {
        String text = "Support Ticket: " + ticket.getSubject()
                + " | Status: " + ticket.getStatus()
                + " | Priority: " + ticket.getPriority()
                + " | Description: " + ticket.getDescription()
                + (ticket.getAssignedAgent() != null
                    ? " | Assigned Agent: " + ticket.getAssignedAgent()
                    : "");
        return buildDocument(VEC_ID_PREFIX + "ticket-" + ticket.getId(), text,
                "SupportTicket", ticket.getId());
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private Document buildDocument(String id, String text, String entityType, Long entityId) {
        Map<String, Object> metadata = new HashMap<>();
        metadata.put(META_ENTITY_TYPE, entityType);
        metadata.put(META_ENTITY_ID, entityId);
        return new Document(id, text, metadata);
    }

    /**
     * Derive the vector store ID for an entity so we can delete it.
     */
    private String toVectorId(Object entity) {
        if (entity instanceof Tool t) {
            return VEC_ID_PREFIX + "tool-" + t.getId();
        } else if (entity instanceof MaintenanceLog ml) {
            return VEC_ID_PREFIX + "maintenance-" + ml.getId();
        } else if (entity instanceof Reservation r) {
            return VEC_ID_PREFIX + "reservation-" + r.getId();
        } else if (entity instanceof Review rev) {
            return VEC_ID_PREFIX + "review-" + rev.getId();
        } else if (entity instanceof SupportTicket t) {
            return VEC_ID_PREFIX + "ticket-" + t.getId();
        }
        return null;
    }

    /**
     * Remove all system-indexed documents from the vector store before a
     * full re-index. Queries for documents with the {@code sys-} prefix
     * in their ID by searching for a generic term and filtering, or
     * simply does a bulk delete of all known system IDs.
     */
    private void deleteAllSystemDocuments() {
        try {
            List<String> existingIds = new ArrayList<>();
            for (Tool t : toolRepository.findAll()) existingIds.add(VEC_ID_PREFIX + "tool-" + t.getId());
            for (MaintenanceLog ml : maintenanceLogRepository.findAll()) existingIds.add(VEC_ID_PREFIX + "maintenance-" + ml.getId());
            for (Reservation r : reservationRepository.findAll()) existingIds.add(VEC_ID_PREFIX + "reservation-" + r.getId());
            for (Review rev : reviewRepository.findAll()) existingIds.add(VEC_ID_PREFIX + "review-" + rev.getId());
            for (SupportTicket t : supportTicketRepository.findAll()) existingIds.add(VEC_ID_PREFIX + "ticket-" + t.getId());
            if (!existingIds.isEmpty()) {
                vectorStore.delete(existingIds);
            }
        } catch (Exception e) {
            log.warn("Could not pre-clean system documents: {}", e.getMessage());
        }
    }
}
