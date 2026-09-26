package com.codafriqa.ai_customer_support_chatbot.service;

import com.codafriqa.ai_customer_support_chatbot.config.SpringContextHolder;
import com.codafriqa.ai_customer_support_chatbot.model.*;
import jakarta.persistence.PostPersist;
import jakarta.persistence.PostRemove;
import jakarta.persistence.PostUpdate;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

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
        schedule(entity, false);
    }

    @PostUpdate
    public void afterUpdate(Object entity) {
        schedule(entity, false);
    }

    @PostRemove
    public void afterDelete(Object entity) {
        schedule(entity, true);
    }

    /**
     * Vector-store sync must NOT run while the entity's transaction is still
     * flushing: the index SQL executes on the same JDBC connection, and a
     * failure there (e.g. an embedding-dimension mismatch) aborts the whole
     * transaction — Postgres 25P02 "current transaction is aborted" — turning
     * an otherwise successful entity write into a 500. Deferring to
     * afterCommit fixes that and also skips pointless reindexes when the
     * transaction rolls back. The indexer methods are {@code @Async}, so the
     * request thread never waits on embedding/vector I/O either.
     */
    private void schedule(Object entity, boolean removal) {
        if (!isSupported(entity)) {
            return;
        }
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    syncQuietly(entity, removal);
                }
            });
        } else {
            syncQuietly(entity, removal);
        }
    }

    /** Best-effort: exceptions must never surface (esp. from afterCommit). */
    private void syncQuietly(Object entity, boolean removal) {
        try {
            SystemDataIndexer indexer = SpringContextHolder.getBean(SystemDataIndexer.class);
            if (removal) {
                indexer.removeEntity(entity);
            } else {
                indexer.syncEntity(entity);
            }
        } catch (Exception e) {
            // Fail silently — the entity write already committed successfully.
        }
    }

    /** Only handle entities that SystemDataIndexer knows how to index. */
    private boolean isSupported(Object entity) {
        return entity instanceof Tool
                || entity instanceof MaintenanceLog
                || entity instanceof Reservation
                || entity instanceof Review
                || entity instanceof SupportTicket
                || entity instanceof User;
    }
}
