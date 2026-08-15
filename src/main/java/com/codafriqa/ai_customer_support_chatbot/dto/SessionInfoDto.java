package com.codafriqa.ai_customer_support_chatbot.dto;

import java.util.List;

/**
 * Chat session state exposed to the customer-facing frontend: current
 * status (ACTIVE / ESCALATED) plus the full persisted transcript
 * (including AGENT replies after a human handoff).
 */
public record SessionInfoDto(Long id, String status, List<ChatMessageDto> messages) {
}
