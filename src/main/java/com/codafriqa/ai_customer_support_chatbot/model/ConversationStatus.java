package com.codafriqa.ai_customer_support_chatbot.model;

/**
 * Lifecycle states for a customer support conversation.
 */
public enum ConversationStatus {
    AI_ASSISTANT,
    WAITING_FOR_AGENT,
    CONNECTED_TO_AGENT,
    CLOSED
}
