package com.codafriqa.ai_customer_support_chatbot.dto;

import com.codafriqa.ai_customer_support_chatbot.model.ToolStatus;

/**
 * Request payload for updating tool availability status.
 */
public record UpdateToolStatusRequest(
        ToolStatus status) {
}
