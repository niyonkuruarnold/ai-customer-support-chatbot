package com.codafriqa.ai_customer_support_chatbot.service;

import com.codafriqa.ai_customer_support_chatbot.config.SpringContextHolder;
import com.codafriqa.ai_customer_support_chatbot.model.*;
import jakarta.persistence.PostPersist;
import jakarta.persistence.PostRemove;
import jakarta.persistence.PostUpdate;

/**
 * JPA entity listener that auto-syncs system entities with the pgvector
 * vector store whenever a database record is created, updated, or deleted.
 *
 * <p>This listener is attached to each system entity class via the
 * {@code @EntityListeners} annotation on the entity. Because JPA entity
 * listeners are instantiated by Hibernate (not by Spring), we cannot
 * inject beans directly. Instead, we use the {@link SpringContextHolder}
 * static accessor to look up the {@link SystemDataIndexer} bean.
 *
 * <p>The sync is best-effort: failures are logged but never propagate
 * to the transaction, so entity CRUD always succeeds even if the vector
 * store is temporarily unavailable.
 */
public class SystemDataSyncListener {

    @PostPersist
    public void afterCreate(Object entity) {
        if (!isSupported(entity)) return;
        try {
            SpringContextHolder.getBean(SystemDataIndexer.class).syncEntity(entity);
        } catch (Exception e) {
            // Fail silently — the entity is already persisted
        }
    }

    @PostUpdate
    public void afterUpdate(Object entity) {
        if (!isSupported(entity)) return;
        try {
            SpringContextHolder.getBean(SystemDataIndexer.class).syncEntity(entity);
        } catch (Exception e) {
            // Fail silently — the entity is already persisted
        }
    }

    @PostRemove
    public void afterDelete(Object entity) {
        if (!isSupported(entity)) return;
        try {
            SpringContextHolder.getBean(SystemDataIndexer.class).removeEntity(entity);
        } catch (Exception e) {
            // Fail silently — the entity is already removed
        }
    }

    /** Only handle entities that SystemDataIndexer knows how to index. */
    private boolean isSupported(Object entity) {
        return entity instanceof Tool
                || entity instanceof MaintenanceLog
                || entity instanceof Reservation
                || entity instanceof Review
                || entity instanceof SupportTicket;
    }
}
