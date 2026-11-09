package com.codafriqa.ai_customer_support_chatbot.exception;

/**
 * Exception for database-related errors
 */
public class DatabaseException extends RuntimeException {
    
    public DatabaseException(String message) {
        super(message);
    }
    
    public DatabaseException(String message, Throwable cause) {
        super(message, cause);
    }
}
