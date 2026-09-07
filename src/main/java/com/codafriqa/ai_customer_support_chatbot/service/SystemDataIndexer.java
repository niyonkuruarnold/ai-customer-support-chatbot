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

@Service
public class SystemDataIndexer {

    private static final Logger log = LoggerFactory.getLogger(SystemDataIndexer.class);

    public static final String META_ENTITY_TYPE = "entityType";
    public static final String META_ENTITY_ID = "entityId";
    public static final String META_SOURCE = "source";
    public static final String SOURCE_SYSTEM_DB = "system-db";
    private static final String VEC_ID_PREFIX = "sys-";

    private final ToolRepository toolRepository;
    private final MaintenanceLogRepository maintenanceLogRepository;
    private final ReservationRepository reservationRepository;
    private final ReviewRepository reviewRepository;
    private final SupportTicketRepository supportTicketRepository;
    private final UserRepository userRepository;
    private final VectorStore vectorStore;
    private final boolean indexingEnabled;
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

    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady() {
        if (!indexingEnabled) {
            log.info("GEMINI_API_KEY is not configured - skipping system data indexing");
            startupIndexingComplete = true;
            return;
        }
        log.info("SystemDataIndexer: starting system data indexing");
        long start = System.currentTimeMillis();
        try {
            long count = indexAllEntities();
            long elapsed = System.currentTimeMillis() - start;
            log.info("SystemDataIndexer: {} entities indexed in {}ms", count, elapsed);
        } catch (Exception e) {
            log.error("SystemDataIndexer: indexing failed: {}", e.getMessage(), e);
        } finally {
            startupIndexingComplete = true;
        }
    }

    @Scheduled(cron = "0 0 * * * *")
    public void scheduledReindex() {
        if (!indexingEnabled) return;
        long start = System.currentTimeMillis();
        try {
            clearSystemVectors();
            long count = indexAllEntities();
            long elapsed = System.currentTimeMillis() - start;
            log.info("Hourly system re-index complete: {} entities indexed in {}ms", count, elapsed);
        } catch (Exception e) {
            log.error("Hourly system re-index failed: {}", e.getMessage(), e);
        }
    }

    public long indexAllEntities() {
        List<Document> docs = new ArrayList<>();
        for (Tool tool : toolRepository.findAll()) docs.add(toDocument(tool));
        for (MaintenanceLog ml : maintenanceLogRepository.findAll()) docs.add(toDocument(ml));
        for (Reservation reservation : reservationRepository.findAll()) docs.add(toDocument(reservation));
        for (Review review : reviewRepository.findAll()) docs.add(toDocument(review));
        for (SupportTicket ticket : supportTicketRepository.findAll()) docs.add(toDocument(ticket));
        for (User user : userRepository.findAll()) docs.add(toDocument(user));
        if (!docs.isEmpty()) {
            deleteAllSystemDocuments();
            try {
                vectorStore.add(docs);
            } catch (Exception e) {
                log.error("Vector store error: {}", e.getMessage(), e);
                throw new RuntimeException("Vector store error: " + e.getMessage());
            }
        }
        return docs.size();
    }

    public void syncEntity(Object entity) {
        if (!indexingEnabled || !startupIndexingComplete) return;
        try {
            Document doc = toDocument(entity);
            if (doc == null) return;
            vectorStore.delete(List.of(doc.getId()));
            vectorStore.add(List.of(doc));
        } catch (Exception e) {
            log.warn("Failed to sync {}: {}", entity.getClass().getSimpleName(), e.getMessage());
        }
    }

    public void removeEntity(Object entity) {
        if (!indexingEnabled || !startupIndexingComplete) return;
        try {
            String vecId = toVectorId(entity);
            if (vecId != null) vectorStore.delete(List.of(vecId));
        } catch (Exception e) {
            log.warn("Failed to remove {}: {}", entity.getClass().getSimpleName(), e.getMessage());
        }
    }

    private Document toDocument(Object entity) {
        if (entity instanceof Tool t) return toDocument(t);
        if (entity instanceof MaintenanceLog ml) return toDocument(ml);
        if (entity instanceof Reservation r) return toDocument(r);
        if (entity instanceof Review rev) return toDocument(rev);
        if (entity instanceof SupportTicket t) return toDocument(t);
        if (entity instanceof User u) return toDocument(u);
        return null;
    }

    private Document toDocument(Tool tool) {
        String text = "Tool: " + tool.getName() + " | Category: " + tool.getCategory()
                + " | Status: " + tool.getStatus()
                + (tool.getDescription() != null ? " | Description: " + tool.getDescription() : "");
        return buildDocument(vectorId(VEC_ID_PREFIX + "tool-" + tool.getId()), text, "Tool", tool.getId());
    }

    private Document toDocument(MaintenanceLog ml) {
        String text = "Maintenance Log for Tool #" + ml.getToolId()
                + " | Date: " + ml.getServiceDate() + " | Description: " + ml.getDescription()
                + (ml.getCost() != null ? " | Cost: $" + ml.getCost() : "");
        return buildDocument(vectorId(VEC_ID_PREFIX + "maintenance-" + ml.getId()), text, "MaintenanceLog", ml.getId());
    }

    private Document toDocument(Reservation r) {
        String text = "Reservation: Tool #" + r.getToolId() + " | Borrower #" + r.getBorrowerId()
                + " | Period: " + r.getStartDate() + " to " + r.getEndDate()
                + " | Status: " + r.getStatus();
        return buildDocument(vectorId(VEC_ID_PREFIX + "reservation-" + r.getId()), text, "Reservation", r.getId());
    }

    private Document toDocument(Review review) {
        String text = "Review for Tool #" + review.getToolId() + " | Rating: " + review.getRating() + "/5";
        return buildDocument(vectorId(VEC_ID_PREFIX + "review-" + review.getId()), text, "Review", review.getId());
    }

    private Document toDocument(SupportTicket ticket) {
        String text = "Support Ticket: " + ticket.getSubject()
                + " | Status: " + (ticket.getStatus() != null ? ticket.getStatus().name() : "UNKNOWN")
                + " | Priority: " + (ticket.getPriority() != null ? ticket.getPriority().name() : "UNKNOWN")
                + " | Description: " + ticket.getDescription();
        return buildDocument(vectorId(VEC_ID_PREFIX + "ticket-" + ticket.getId()), text, "SupportTicket", ticket.getId());
    }

    private Document toDocument(User user) {
        String text = "Team Member: " + user.getEmail() + " | Role: " + user.getRole();
        return buildDocument(vectorId(VEC_ID_PREFIX + "user-" + user.getId()), text, "User", user.getId());
    }

    private Document buildDocument(String id, String text, String entityType, Long entityId) {
        Map<String, Object> metadata = new HashMap<>();
        metadata.put(META_ENTITY_TYPE, entityType);
        metadata.put(META_ENTITY_ID, entityId);
        metadata.put(META_SOURCE, SOURCE_SYSTEM_DB);
        return new Document(id, text, metadata);
    }

    private static String vectorId(String key) {
        return UUID.nameUUIDFromBytes(key.getBytes(java.nio.charset.StandardCharsets.UTF_8)).toString();
    }

    private String toVectorId(Object entity) {
        if (entity instanceof Tool t) return vectorId(VEC_ID_PREFIX + "tool-" + t.getId());
        if (entity instanceof MaintenanceLog ml) return vectorId(VEC_ID_PREFIX + "maintenance-" + ml.getId());
        if (entity instanceof Reservation r) return vectorId(VEC_ID_PREFIX + "reservation-" + r.getId());
        if (entity instanceof Review rev) return vectorId(VEC_ID_PREFIX + "review-" + rev.getId());
        if (entity instanceof SupportTicket t) return vectorId(VEC_ID_PREFIX + "ticket-" + t.getId());
        if (entity instanceof User u) return vectorId(VEC_ID_PREFIX + "user-" + u.getId());
        return null;
    }

    private void deleteAllSystemDocuments() {
        try {
            List<String> ids = new ArrayList<>();
            for (Tool t : toolRepository.findAll()) ids.add(vectorId(VEC_ID_PREFIX + "tool-" + t.getId()));
            for (MaintenanceLog ml : maintenanceLogRepository.findAll()) ids.add(vectorId(VEC_ID_PREFIX + "maintenance-" + ml.getId()));
            for (Reservation r : reservationRepository.findAll()) ids.add(vectorId(VEC_ID_PREFIX + "reservation-" + r.getId()));
            for (Review rev : reviewRepository.findAll()) ids.add(vectorId(VEC_ID_PREFIX + "review-" + rev.getId()));
            for (SupportTicket t : supportTicketRepository.findAll()) ids.add(vectorId(VEC_ID_PREFIX + "ticket-" + t.getId()));
            for (User u : userRepository.findAll()) ids.add(vectorId(VEC_ID_PREFIX + "user-" + u.getId()));
            if (!ids.isEmpty()) vectorStore.delete(ids);
        } catch (Exception e) {
            log.warn("Could not pre-clean system documents: {}", e.getMessage());
        }
    }

    private void clearSystemVectors() {
        try {
            List<String> ids = new ArrayList<>();
            for (Tool t : toolRepository.findAll()) ids.add(vectorId(VEC_ID_PREFIX + "tool-" + t.getId()));
            for (MaintenanceLog ml : maintenanceLogRepository.findAll()) ids.add(vectorId(VEC_ID_PREFIX + "maintenance-" + ml.getId()));
            for (Reservation r : reservationRepository.findAll()) ids.add(vectorId(VEC_ID_PREFIX + "reservation-" + r.getId()));
            for (Review rev : reviewRepository.findAll()) ids.add(vectorId(VEC_ID_PREFIX + "review-" + rev.getId()));
            for (SupportTicket t : supportTicketRepository.findAll()) ids.add(vectorId(VEC_ID_PREFIX + "ticket-" + t.getId()));
            for (User u : userRepository.findAll()) ids.add(vectorId(VEC_ID_PREFIX + "user-" + u.getId()));
            if (!ids.isEmpty()) vectorStore.delete(ids);
        } catch (Exception e) {
            log.warn("Could not clear system vectors: {}", e.getMessage());
        }
    }
}
