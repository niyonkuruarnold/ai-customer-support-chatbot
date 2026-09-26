-- V8: activity-log actor_id must be nullable
--
-- TicketActivityLog.actorId is documented as "null for system actions" and
-- every caller (state-machine transitions, SYSTEM/AGENT role events) passes
-- NULL, so a NOT NULL constraint made every log insert fail — which, inside
-- a joined transaction, rolled back the very ticket update that triggered
-- the log ("Transaction silently rolled back because it has been marked as
-- rollback-only").
--
-- Idempotent: dropping NOT NULL on an already-nullable column is a no-op.
-- The application also applies this fix at startup
-- (SchemaCompatibilityBackfill) for environments that don't run migrations.

ALTER TABLE ticket_activity_logs ALTER COLUMN actor_id DROP NOT NULL;
