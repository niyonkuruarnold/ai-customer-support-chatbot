package com.codafriqa.ai_customer_support_chatbot.exception;

import org.springframework.http.HttpStatus;

/**
 * Exception for OpenAI API errors (rate limits, authentication failures, etc.)
 */
public class OpenAIApiException extends RuntimeException {
    
    private final HttpStatus status;
    
    public OpenAIApiException(String message, HttpStatus status) {
        super(message);
        this.status = status;
    }
    
    public OpenAIApiException(String message, HttpStatus status, Throwable cause) {
        super(message, cause);
        this.status = status;
    }
    
    public HttpStatus getStatus() {
        return status;
    }
}
