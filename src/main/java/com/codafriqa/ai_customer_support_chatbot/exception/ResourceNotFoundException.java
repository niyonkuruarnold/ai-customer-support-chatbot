package com.codafriqa.ai_customer_support_chatbot.exception;

/**
 * Exception for resource not found errors
 */
public class ResourceNotFoundException extends RuntimeException {
    
    public ResourceNotFoundException(String message) {
        super(message);
    }
    
    public ResourceNotFoundException(String message, Throwable cause) {
        super(message, cause);
    }
}
