package com.codafriqa.ai_customer_support_chatbot.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Idempotent, non-fatal startup schema compatibility fixes. They run right
 * after the context refresh — i.e. after Hibernate's {@code ddl-auto=update}
 * schema pass and before the {@code ApplicationReadyEvent} seeders touch any
 * data — for environments that do not execute the {@code db/migration}
 * scripts (Flyway is not on the classpath).
 *
 * <ol>
 *   <li><b>{@code support_tickets.version}</b> — {@code ddl-auto=update} adds
 *       the new {@code @Version} column as a nullable bigint with no default,
 *       so pre-existing rows keep NULL and every later update would fail the
 *       optimistic-lock check. Rows are defaulted to 0.</li>
 *   <li><b>{@code ticket_activity_logs.actor_id}</b> — the entity documents
 *       actorId as nullable (null for system actions) but the deployed table
 *       still carries the old NOT NULL constraint; every system/agent activity
 *       log insert would violate it and abort the caller's transaction.
 *       The constraint is dropped.</li>
 * </ol>
 *
 * All statements are idempotent; failures are logged, never fatal.
 */
@Component
public class SchemaCompatibilityBackfill {

    private static final Logger log = LoggerFactory.getLogger(SchemaCompatibilityBackfill.class);

    private final JdbcTemplate jdbcTemplate;

    public SchemaCompatibilityBackfill(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @EventListener(ContextRefreshedEvent.class)
    public void applySchemaCompatibilityFixes() {
        // 1) Optimistic-lock version column + backfill for legacy rows.
        run("ALTER TABLE IF EXISTS support_tickets "
                + "ADD COLUMN IF NOT EXISTS version BIGINT DEFAULT 0");
        int backfilled = run("UPDATE support_tickets SET version = 0 WHERE version IS NULL");
        if (backfilled > 0) {
            log.info("Backfilled version=0 on {} legacy support_tickets row(s) for @Version locking",
                    backfilled);
        }

        // 2) Activity-log actor_id must accept NULL (system actions).
        run("ALTER TABLE IF EXISTS ticket_activity_logs ALTER COLUMN actor_id DROP NOT NULL");
    }

    /** Executes one idempotent statement; returns affected rows, or 0 on failure. */
    private int run(String sql) {
        try {
            return jdbcTemplate.update(sql);
        } catch (Exception e) {
            log.warn("Schema compatibility step skipped ({}): {}",
                    e.getClass().getSimpleName(), e.getMessage());
            return 0;
        }
    }
}
