-- V2: Ticket Management and Activity Logging
-- Tables: support_tickets, ticket_activity_logs

-- ============================================================
-- Support Tickets (full lifecycle)
-- ============================================================
CREATE TABLE IF NOT EXISTS support_tickets (
    id BIGSERIAL PRIMARY KEY,
    ticket_reference VARCHAR(255) UNIQUE,
    subject VARCHAR(500) NOT NULL,
    description TEXT,
    status VARCHAR(50) NOT NULL DEFAULT 'NEW',
    priority VARCHAR(50) NOT NULL DEFAULT 'MEDIUM',
    category VARCHAR(50) DEFAULT 'GENERAL',
    assigned_agent VARCHAR(255),
    customer_email VARCHAR(255),
    customer_name VARCHAR(255),
    session_id BIGINT,
    organization_id BIGINT,
    customer_reply_count INTEGER DEFAULT 0,
    closed_at TIMESTAMP,
    reopened_at TIMESTAMP,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (session_id) REFERENCES chat_sessions(id),
    FOREIGN KEY (organization_id) REFERENCES organizations(id)
);

CREATE INDEX IF NOT EXISTS idx_tickets_status ON support_tickets(status);
CREATE INDEX IF NOT EXISTS idx_tickets_priority ON support_tickets(priority);
CREATE INDEX IF NOT EXISTS idx_tickets_agent ON support_tickets(assigned_agent);
CREATE INDEX IF NOT EXISTS idx_tickets_reference ON support_tickets(ticket_reference);

-- ============================================================
-- Ticket Activity Logs (immutable audit trail)
-- ============================================================
CREATE TABLE IF NOT EXISTS ticket_activity_logs (
    id BIGSERIAL PRIMARY KEY,
    ticket_id BIGINT NOT NULL,
    actor_id BIGINT,
    actor_name VARCHAR(255) NOT NULL,
    action_type VARCHAR(50) NOT NULL,
    previous_value TEXT,
    new_value TEXT,
    description TEXT,
    timestamp TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    customer_visible BOOLEAN DEFAULT TRUE,
    FOREIGN KEY (ticket_id) REFERENCES support_tickets(id)
);

CREATE INDEX IF NOT EXISTS idx_activity_ticket ON ticket_activity_logs(ticket_id);
CREATE INDEX IF NOT EXISTS idx_activity_type ON ticket_activity_logs(action_type);
CREATE INDEX IF NOT EXISTS idx_activity_timestamp ON ticket_activity_logs(timestamp);
