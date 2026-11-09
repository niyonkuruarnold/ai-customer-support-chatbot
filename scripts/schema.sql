<<<<<<< HEAD
CREATE EXTENSION IF NOT EXISTS vector;

DO $$
BEGIN
    CREATE TYPE user_role AS ENUM (
        'ADMIN', 'MANAGER', 'AGENT',
        'KNOWLEDGE_EDITOR', 'ANALYST', 'CUSTOMER'
    );
EXCEPTION
    WHEN duplicate_object THEN NULL;
END $$;


CREATE TABLE IF NOT EXISTS users (
=======

CREATE EXTENSION IF NOT EXISTS vector;

-- User Roles
CREATE TYPE user_role AS ENUM (
    'ADMIN', 'MANAGER', 'AGENT', 
    'KNOWLEDGE_EDITOR', 'ANALYST', 'CUSTOMER'
);


CREATE TABLE users (
>>>>>>> acc811384ffec57b834a51b9cfe1d759cdddcda5
    id SERIAL PRIMARY KEY,
    email VARCHAR(255) UNIQUE NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    role user_role NOT NULL DEFAULT 'CUSTOMER',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);


<<<<<<< HEAD
CREATE TABLE IF NOT EXISTS chat_sessions (
=======
CREATE TABLE chat_sessions (
>>>>>>> acc811384ffec57b834a51b9cfe1d759cdddcda5
    id SERIAL PRIMARY KEY,
    customer_id INT REFERENCES users(id) ON DELETE SET NULL,
    status VARCHAR(50) DEFAULT 'ACTIVE',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);


<<<<<<< HEAD
CREATE TABLE IF NOT EXISTS messages (
=======
CREATE TABLE messages (
>>>>>>> acc811384ffec57b834a51b9cfe1d759cdddcda5
    id SERIAL PRIMARY KEY,
    session_id INT REFERENCES chat_sessions(id) ON DELETE CASCADE,
    sender_type VARCHAR(20) CHECK (sender_type IN ('CUSTOMER', 'BOT', 'AGENT')),
    content TEXT NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

<<<<<<< HEAD
CREATE TABLE IF NOT EXISTS tickets (
=======

CREATE TABLE tickets (
>>>>>>> acc811384ffec57b834a51b9cfe1d759cdddcda5
    id SERIAL PRIMARY KEY,
    session_id INT REFERENCES chat_sessions(id) ON DELETE CASCADE,
    assigned_agent_id INT REFERENCES users(id) ON DELETE SET NULL,
    status VARCHAR(50) DEFAULT 'OPEN',
    priority VARCHAR(20) DEFAULT 'MEDIUM',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);


<<<<<<< HEAD
CREATE TABLE IF NOT EXISTS knowledge_chunks (
    id SERIAL PRIMARY KEY,
    title VARCHAR(255) NOT NULL,
    content TEXT NOT NULL,
    embedding vector(1536),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_chat_sessions_customer_id ON chat_sessions(customer_id);
CREATE INDEX IF NOT EXISTS idx_messages_session_id ON messages(session_id);
CREATE INDEX IF NOT EXISTS idx_tickets_status_priority ON tickets(status, priority);
CREATE INDEX IF NOT EXISTS idx_knowledge_chunks_created_at ON knowledge_chunks(created_at DESC);
=======
CREATE TABLE knowledge_chunks (
    id SERIAL PRIMARY KEY,
    title VARCHAR(255) NOT NULL,
    content TEXT NOT NULL,
    embedding vector(1536), 
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
>>>>>>> acc811384ffec57b834a51b9cfe1d759cdddcda5
