-- Ensure the pgvector extension is available
CREATE EXTENSION IF NOT EXISTS vector;

-- Create the vector_store table only if it does not already exist.
-- Previous versions DROPped and recreated this table on every startup,
-- which destroyed all embeddings. Documents persisted in the metadata
-- tables (knowledge_documents / knowledge_chunks) would then show as
-- "uploaded" in the admin UI but have zero searchable vectors — so the
-- chatbot could never find them via similarity search.
--
-- The embedding dimension (768) matches Google text-embedding-004 output.
-- If you switch embedding models, ALTER the column to match:
--   ALTER TABLE vector_store ALTER COLUMN embedding TYPE VECTOR(768);
-- or DROP the table once while the app is stopped, then restart.
CREATE TABLE IF NOT EXISTS vector_store (
    id UUID PRIMARY KEY,
    content TEXT,
    metadata JSONB,
    embedding VECTOR(768)
);
