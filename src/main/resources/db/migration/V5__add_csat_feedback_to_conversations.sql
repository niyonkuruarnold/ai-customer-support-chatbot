-- V5: Conversations table with CSAT feedback columns
-- Adds csat_score, csat_comment, and csat_submitted_at to conversations

CREATE TABLE IF NOT EXISTS conversations (
    id              BIGSERIAL PRIMARY KEY,
    session_id      BIGINT,
    customer_id     BIGINT,
    agent_id        BIGINT,
    status          VARCHAR(50)  NOT NULL DEFAULT 'ACTIVE',
    csat_score      INT          CHECK (csat_score >= 1 AND csat_score <= 5),
    csat_comment    TEXT,
    csat_submitted_at TIMESTAMP WITH TIME ZONE,
    created_at      TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (session_id) REFERENCES chat_sessions(id),
    FOREIGN KEY (customer_id) REFERENCES users(id),
    FOREIGN KEY (agent_id)   REFERENCES users(id)
);

CREATE INDEX IF NOT EXISTS idx_conversations_session  ON conversations(session_id);
CREATE INDEX IF NOT EXISTS idx_conversations_customer ON conversations(customer_id);
CREATE INDEX IF NOT EXISTS idx_conversations_status   ON conversations(status);
