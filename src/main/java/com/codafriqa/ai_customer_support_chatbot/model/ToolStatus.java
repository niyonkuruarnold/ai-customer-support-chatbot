package com.codafriqa.ai_customer_support_chatbot.model;

/**
 * Tool availability status for the Smart Neighborhood Hub.
 *
 * AVAILABLE      – tool is ready for borrowing
 * BORROWED       – tool is currently checked out
 * IN_MAINTENANCE – tool is undergoing service / repair
 */
public enum ToolStatus {
    AVAILABLE,
    BORROWED,
    IN_MAINTENANCE
}
