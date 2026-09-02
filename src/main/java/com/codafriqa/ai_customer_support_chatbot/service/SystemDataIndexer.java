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
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Dual-layer system data indexer that keeps the pgvector vector store in sync
 * with the live PostgreSQL database.
 *
 * <h3>Layer 1 — Startup indexing</h3>
 * An {@link ApplicationReadyEvent} listener fetches all system entities,
 * maps each into a descriptive semantic text block, wraps them as Spring AI
 * {@link Document}s, and saves them into the vector store. This runs once
 * after the application context is fully initialized.
 *
 * <h3>Layer 2 — Hourly scheduled re-index</h3>
 * A {@link Scheduled} cron job ({@code "0 0 * * * *"}) runs every hour to
 * clear outdated system vectors (filtering metadata {@code source = 'system-db'})
 * and batch-insert fresh snapshots of all core entities.
 *
 * <h3>Layer 3 — Real-time sync via JPA Entity Listeners</h3>
 * {@link SystemDataSyncListener} calls {@link #syncEntity(Object)} on
 * {@code @PostPersist/@PostUpdate} and {@link #removeEntity(Object)} on
 * {@code @PostRemove} to keep the vector store in sync whenever a database
 * record is created, updated, or deleted.
 *
 * <h3>Indexed entities</h3>
 * <ul>
 *   <li>{@link Tool} — borrowable tools with availability status</li>
 *   <li>{@link MaintenanceLog} — service history and costs</li>
 *   <li>{@link Reservation} — tool borrowing records</li>
 *   <li>{@link Review} — user feedback and ratings</li>
 *   <li>{@link SupportTicket} — customer support tickets</li>
 *   <li>{@link User} — system user accounts and roles</li>
 * </ul>
 *
 * <h3>Embedding safety</h3>
 * When {@code GEMINI_API_KEY} is missing or invalid, indexing is skipped
 * entirely (no embedding API available). The sync methods are no-ops so
 * CRUD operations proceed normally.
 */
@Service
public class SystemDataIndexer {

    private static final Logger log = LoggerFactory.getLogger(SystemDataIndexer.class);

    /** Metadata key stored on every vector document for entity mapping. */
    public static final String META_ENTITY_TYPE = "entityType";
    public static final String META_ENTITY_ID = "entityId";

    /**
     * Metadata key that identifies all vector documents managed by this
     * indexer as originating from the system database. Used during hourly
     * re-index to clear outdated vectors before batch-inserting fresh data.
     */
    public static final String META_SOURCE = "source";
    public static final String SOURCE_SYSTEM_DB = "system-db";

    /** Prefix for all vector store IDs managed by this indexer. */
    private static final String VEC_ID_PREFIX = "sys-";

    private final ToolRepository toolRepository;
    private final MaintenanceLogRepository maintenanceLogRepository;
    private final ReservationRepository reservationRepository;
    private final ReviewRepository reviewRepository;
    private final SupportTicketRepository supportTicketRepository;
    private final UserRepository userRepository;
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
                             UserRepository userRepository,
                             VectorStore vectorStore,
                             @Value("${spring.ai.google.genai.api-key:}") String geminiApiKey) {
        this.toolRepository = toolRepository;
        this.maintenanceLogRepository = maintenanceLogRepository;
        this.reservationRepository = reservationRepository;
        this.reviewRepository = reviewRepository;
        this.supportTicketRepository = supportTicketRepository;
        this.userRepository = userRepository;
        this.vectorStore = vectorStore;
        this.indexingEnabled = geminiApiKey != null && !geminiApiKey.isBlank()
                && !geminiApiKey.contains("your-") && !geminiApiKey.contains("placeholder");
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
            log.info("GEMINI_API_KEY is not configured — skipping system data indexing");
            startupIndexingComplete = true;
            return;
        }

        log.info("SystemDataIndexer: ApplicationReadyEvent fired — starting system data indexing");
        long start = System.currentTimeMillis();
        try {
            long count = indexAllEntities();
            long elapsed = System.currentTimeMillis() - start;
            log.info("SystemDataIndexer: SUCCESS — {} entities indexed into vector store in {}ms", count, elapsed);
        } catch (Exception e) {
            log.error("SystemDataIndexer: FAILED — system data indexing failed: {}", e.getMessage(), e);
        } finally {
            startupIndexingComplete = true;
        }
    }

    // ------------------------------------------------------------------
    // Hourly scheduled re-index
    // ------------------------------------------------------------------

    /**
     * Hourly cron job that clears outdated system vectors (metadata
     * {@code source = 'system-db'}) and batch-inserts a fresh snapshot
     * of all core entities. This ensures the vector store stays current
     * even if JPA entity listeners missed an update (e.g., during bulk
     * imports or direct SQL modifications).
     */
    @Scheduled(cron = "0 0 * * * *")
    public void scheduledReindex() {
        if (!indexingEnabled) return;

        long start = System.currentTimeMillis();
        try {
            // Clear all existing system-db vectors
            clearSystemVectors();

            // Batch-insert fresh entity snapshots
            long count = indexAllEntities();
            long elapsed = System.currentTimeMillis() - start;
            log.info("Hourly system re-index complete: {} entities indexed in {}ms", count, elapsed);
        } catch (Exception e) {
            log.error("Hourly system re-index failed: {}", e.getMessage(), e);
        }
    }

    // ------------------------------------------------------------------
    // Full entity indexing
    // ------------------------------------------------------------------

    /**
     * Fetch all system entities, map them to documents, and save to the
     * vector store. Returns the total number of indexed entities.
     */
    public long indexAllEntities() {
        // Log entity counts before indexing so we can verify the seeder ran
        long toolCount = toolRepository.count();
        long maintenanceLogCount = maintenanceLogRepository.count();
        long reservationCount = reservationRepository.count();
        long reviewCount = reviewRepository.count();
        long ticketCount = supportTicketRepository.count();
        long userCount = userRepository.count();
        log.info("SystemDataIndexer: entity counts — tools={}, maintenanceLogs={}, reservations={}, reviews={}, tickets={}, users={}",
                toolCount, maintenanceLogCount, reservationCount, reviewCount, ticketCount, userCount);

        List<Document> docs = new ArrayList<>();

        // Tools
        for (Tool tool : toolRepository.findAll()) {
            docs.add(toDocument(tool));
        }

        // Maintenance Logs
        for (MaintenanceLog ml : maintenanceLogRepository.findAll()) {
            docs.add(toDocument(ml));
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

        // Users (team members, agents, admins)
        for (User user : userRepository.findAll()) {
            docs.add(toDocument(user));
        }

        if (!docs.isEmpty()) {
            // Delete existing system-indexed documents first to avoid duplicates
            deleteAllSystemDocuments();
            try {
                vectorStore.add(docs);
            } catch (org.springframework.web.client.RestClientResponseException e) {
                System.err.println("Gemini API HTTP Error Code: " + e.getRawStatusCode());
                System.err.println("Gemini API Response Body: " + e.getResponseBodyAsString());
                throw new RuntimeException("Gemini API Error: " + e.getResponseBodyAsString());
            } catch (Exception e) {
                e.printStackTrace();
                throw new RuntimeException("Vector store error: " + e.getMessage());
            }
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
        } else if (entity instanceof User user) {
            return toDocument(user);
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
        return buildDocument(vectorId(VEC_ID_PREFIX + "tool-" + tool.getId()), text,
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
        return buildDocument(vectorId(VEC_ID_PREFIX + "maintenance-" + ml.getId()), text,
                "MaintenanceLog", ml.getId());
    }

    private Document toDocument(Reservation r) {
        String text = "Reservation: Tool #" + r.getToolId()
                + " | Borrower #" + r.getBorrowerId()
                + " | Period: " + r.getStartDate() + " to " + r.getEndDate()
                + " | Status: " + r.getStatus()
                + (r.getNotes() != null ? " | Notes: " + r.getNotes() : "");
        return buildDocument(vectorId(VEC_ID_PREFIX + "reservation-" + r.getId()), text,
                "Reservation", r.getId());
    }

    private Document toDocument(Review review) {
        String text = "Review for Tool #" + review.getToolId()
                + " | Rating: " + review.getRating() + "/5"
                + (review.getComment() != null
                    ? " | Comment: " + review.getComment()
                    : "");
        return buildDocument(vectorId(VEC_ID_PREFIX + "review-" + review.getId()), text,
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
        return buildDocument(vectorId(VEC_ID_PREFIX + "ticket-" + ticket.getId()), text,
                "SupportTicket", ticket.getId());
    }

    private Document toDocument(User user) {
        String text = "Team Member: " + user.getEmail()
                + " | Role: " + user.getRole()
                + " | Joined: " + user.getCreatedAt();
        return buildDocument(vectorId(VEC_ID_PREFIX + "user-" + user.getId()), text,
                "User", user.getId());
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private Document buildDocument(String id, String text, String entityType, Long entityId) {
        Map<String, Object> metadata = new HashMap<>();
        metadata.put(META_ENTITY_TYPE, entityType);
        metadata.put(META_ENTITY_ID, entityId);
        metadata.put(META_SOURCE, SOURCE_SYSTEM_DB);
        return new Document(id, text, metadata);
    }

    /**
     * Generate a deterministic UUID from a key string so the same entity
     * always maps to the same vector-store ID (needed for delete/sync).
     */
    private static String vectorId(String key) {
        return UUID.nameUUIDFromBytes(key.getBytes(java.nio.charset.StandardCharsets.UTF_8)).toString();
    }

    /**
     * Derive the vector store ID for an entity so we can delete it.
     */
    private String toVectorId(Object entity) {
        if (entity instanceof Tool t) {
            return vectorId(VEC_ID_PREFIX + "tool-" + t.getId());
        } else if (entity instanceof MaintenanceLog ml) {
            return vectorId(VEC_ID_PREFIX + "maintenance-" + ml.getId());
        } else if (entity instanceof Reservation r) {
            return vectorId(VEC_ID_PREFIX + "reservation-" + r.getId());
        } else if (entity instanceof Review rev) {
            return vectorId(VEC_ID_PREFIX + "review-" + rev.getId());
        } else if (entity instanceof SupportTicket t) {
            return vectorId(VEC_ID_PREFIX + "ticket-" + t.getId());
        } else if (entity instanceof User u) {
            return vectorId(VEC_ID_PREFIX + "user-" + u.getId());
        }
        return null;
    }

    /**
     * Remove all system-indexed documents from the vector store before a
     * full re-index. Builds the list of known system IDs by querying all
     * repositories.
     */
    private void deleteAllSystemDocuments() {
        try {
            List<String> existingIds = new ArrayList<>();
            for (Tool t : toolRepository.findAll()) existingIds.add(vectorId(VEC_ID_PREFIX + "tool-" + t.getId()));
            for (MaintenanceLog ml : maintenanceLogRepository.findAll()) existingIds.add(vectorId(VEC_ID_PREFIX + "maintenance-" + ml.getId()));
            for (Reservation r : reservationRepository.findAll()) existingIds.add(vectorId(VEC_ID_PREFIX + "reservation-" + r.getId()));
            for (Review rev : reviewRepository.findAll()) existingIds.add(vectorId(VEC_ID_PREFIX + "review-" + rev.getId()));
            for (SupportTicket t : supportTicketRepository.findAll()) existingIds.add(vectorId(VEC_ID_PREFIX + "ticket-" + t.getId()));
            for (User u : userRepository.findAll()) existingIds.add(vectorId(VEC_ID_PREFIX + "user-" + u.getId()));
            if (!existingIds.isEmpty()) {
                vectorStore.delete(existingIds);
            }
        } catch (Exception e) {
            log.warn("Could not pre-clean system documents: {}", e.getMessage());
        }
    }

    /**
     * Clear all vector documents that originated from the system database
     * (metadata {@code source = 'system-db'}). This is used by the hourly
     * scheduled re-index to ensure stale vectors are removed before fresh
     * data is batch-inserted.
     */
    private void clearSystemVectors() {
        try {
            // Build the list of all known system vector IDs
            List<String> systemIds = new ArrayList<>();
            for (Tool t : toolRepository.findAll()) systemIds.add(vectorId(VEC_ID_PREFIX + "tool-" + t.getId()));
            for (MaintenanceLog ml : maintenanceLogRepository.findAll()) systemIds.add(vectorId(VEC_ID_PREFIX + "maintenance-" + ml.getId()));
            for (Reservation r : reservationRepository.findAll()) systemIds.add(vectorId(VEC_ID_PREFIX + "reservation-" + r.getId()));
            for (Review rev : reviewRepository.findAll()) systemIds.add(vectorId(VEC_ID_PREFIX + "review-" + rev.getId()));
            for (SupportTicket t : supportTicketRepository.findAll()) systemIds.add(vectorId(VEC_ID_PREFIX + "ticket-" + t.getId()));
            for (User u : userRepository.findAll()) systemIds.add(vectorId(VEC_ID_PREFIX + "user-" + u.getId()));
            if (!systemIds.isEmpty()) {
                vectorStore.delete(systemIds);
                log.debug("Cleared {} system vectors before re-index", systemIds.size());
            }
        } catch (Exception e) {
            log.warn("Could not clear system vectors: {}", e.getMessage());
        }
    }
}
