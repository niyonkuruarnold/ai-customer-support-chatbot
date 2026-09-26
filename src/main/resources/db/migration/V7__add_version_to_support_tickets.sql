-- V7: Optimistic locking for support_tickets
--
-- Backs the new @Version field on SupportTicket. Conflicting concurrent
-- writes now fail fast (StaleObjectStateException) instead of silently
-- overwriting each other; TicketUpdateGuard reloads and retries.
--
-- PG >= 11 fills existing rows from the DEFAULT instantly; the UPDATE keeps
-- this safe on any PG version and is idempotent. The application also runs
-- an equivalent backfill at startup (SupportTicketVersionBackfill), so this
-- file is a no-op safeguard for Flyway-managed environments.

ALTER TABLE support_tickets ADD COLUMN IF NOT EXISTS version BIGINT DEFAULT 0;

UPDATE support_tickets SET version = 0 WHERE version IS NULL;
