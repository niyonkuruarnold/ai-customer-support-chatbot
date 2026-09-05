-- V6: Add AI escalation summary to conversations table
-- Stores the Gemini-generated handoff summary when a conversation is escalated

ALTER TABLE conversations
    ADD COLUMN IF NOT EXISTS escalation_summary TEXT;
