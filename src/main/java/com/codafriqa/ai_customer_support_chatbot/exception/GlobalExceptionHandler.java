package com.codafriqa.ai_customer_support_chatbot.exception;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * Global Exception Handler for the entire application.
 * Catches exceptions thrown by controllers and returns structured error responses.
 */
@ControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /**
     * Handle validation errors (e.g., @NotBlank, @Valid annotations).
     * Overrides ResponseEntityExceptionHandler.handleMethodArgumentNotValid.
     * In Spring 6.1 the parent exposes a single annotated dispatcher
     * (handleException(Exception, ...)) that routes to this protected method,
     * so this override must NOT declare its own @ExceptionHandler — declaring
     * one for MethodArgumentNotValidException would make the
     * ExceptionHandlerMethodResolver throw "Ambiguous @ExceptionHandler".
     */
    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException ex,
            HttpHeaders headers,
            HttpStatusCode status,
            WebRequest request) {
        
        Map<String, Object> response = new HashMap<>();
        response.put("timestamp", LocalDateTime.now());
        response.put("status", HttpStatus.BAD_REQUEST.value());
        response.put("error", "Validation Failed");
        response.put("message", "Invalid request parameters");
        
        Map<String, String> fieldErrors = new HashMap<>();
        ex.getBindingResult().getFieldErrors().forEach(error ->
            fieldErrors.put(error.getField(), error.getDefaultMessage())
        );
        
        response.put("fieldErrors", fieldErrors);
        response.put("path", request.getDescription(false).replace("uri=", ""));
        
        return new ResponseEntity<>(response, HttpStatus.BAD_REQUEST);
    }

    /**
     * Handle AI provider API errors (rate limits, authentication failures, etc.)
     */
    @ExceptionHandler(OpenAIApiException.class)
    public ResponseEntity<Map<String, Object>> handleOpenAIApiException(
            OpenAIApiException ex,
            WebRequest request) {
        
        Map<String, Object> response = new HashMap<>();
        response.put("timestamp", LocalDateTime.now());
        response.put("status", ex.getStatus().value());
        response.put("error", "AI Provider Error");
        response.put("message", ex.getMessage());
        addExactErrorFields(response, ex, ex.getStatus());
        response.put("path", request.getDescription(false).replace("uri=", ""));
        
        return new ResponseEntity<>(response, ex.getStatus());
    }

    /**
     * Handle RestClient errors from the OpenAI embedding/chat HTTP calls.
     * Logs the exact status code and response body to the server console.
     */
    @ExceptionHandler(RestClientResponseException.class)
    public ResponseEntity<Map<String, Object>> handleRestClientResponse(
            RestClientResponseException ex,
            WebRequest request) {

        int status = ex.getRawStatusCode();
        String body = ex.getResponseBodyAsString();
        log.error("RestClient HTTP error {} from AI provider — body: {}", status, body, ex);

        String userMessage = mapOpenAiHttpError(status, body);
        HttpStatus responseStatus = status >= 400 && status < 600
                ? HttpStatus.valueOf(status) : HttpStatus.BAD_GATEWAY;

        Map<String, Object> response = new HashMap<>();
        response.put("timestamp", LocalDateTime.now());
        response.put("status", responseStatus.value());
        response.put("error", "AI Provider Error");
        response.put("message", userMessage);
        addExactErrorFields(response, ex, responseStatus);
        response.put("path", request.getDescription(false).replace("uri=", ""));

        return new ResponseEntity<>(response, responseStatus);
    }

    /**
     * Handle network/timeout errors when the OpenAI API is unreachable.
     */
    @ExceptionHandler(ResourceAccessException.class)
    public ResponseEntity<Map<String, Object>> handleResourceAccess(
            ResourceAccessException ex,
            WebRequest request) {

        log.error("Could not reach AI provider API: {}", ex.getMessage(), ex);

        Map<String, Object> response = new HashMap<>();
        response.put("timestamp", LocalDateTime.now());
        response.put("status", HttpStatus.BAD_GATEWAY.value());
        response.put("error", "AI Service Unreachable");
        response.put("message", "Could not connect to the AI provider API. "
                + "Check your network connection and firewall settings."
                + (ex.getMessage() != null ? " Details: " + ex.getMessage() : ""));
        response.put("path", request.getDescription(false).replace("uri=", ""));

        return new ResponseEntity<>(response, HttpStatus.BAD_GATEWAY);
    }

    /**
     * Map an OpenAI HTTP status + response body to a clear, actionable
     * user-facing message. Logs the exact error for debugging.
     */
    private static String mapOpenAiHttpError(int status, String body) {
        String openaiMsg = extractOpenAiErrorMessage(body);
        String suffix = (openaiMsg != null && !openaiMsg.isBlank()) ? " — " + openaiMsg : "";
        return switch (status) {
            case 401 -> "Invalid AI API Key (401 Unauthorized). "
                    + "Your key may be missing, expired, or incorrectly formatted. "
                    + "Verify your key at https://aistudio.google.com/apikey" + suffix;
            case 403 -> "AI API access denied (403 Forbidden). "
                    + "Your key may not have permission. "
                    + "Check permissions at https://aistudio.google.com/apikey" + suffix;
            case 429 -> "AI API rate limit exceeded (429). "
                    + "Wait a moment and try again, or check your quota "
                    + "at https://aistudio.google.com" + suffix;
            case 404 -> "AI model not found (404). "
                    + "Verify the embedding model name in application.properties" + suffix;
            default -> "AI provider error (HTTP " + status + ")" + suffix;
        };
    }

    /** Extract the error message from an OpenAI JSON error body. */
    private static String extractOpenAiErrorMessage(String body) {
        if (body == null || body.isBlank()) return null;
        int idx = body.indexOf("\"message\":\"");
        if (idx >= 0) {
            int start = idx + 12;
            int end = body.indexOf('\"', start);
            if (end > start) return body.substring(start, end);
        }
        return body.length() > 200 ? body.substring(0, 200) + "..." : body;
    }

    /**
     * Handle database connection errors
     */
    @ExceptionHandler(DatabaseException.class)
    public ResponseEntity<Map<String, Object>> handleDatabaseException(
            DatabaseException ex,
            WebRequest request) {
        
        Map<String, Object> response = new HashMap<>();
        response.put("timestamp", LocalDateTime.now());
        response.put("status", HttpStatus.SERVICE_UNAVAILABLE.value());
        response.put("error", "Database Error");
        response.put("message", "Unable to connect to the database. Please try again later.");
        response.put("path", request.getDescription(false).replace("uri=", ""));
        
        return new ResponseEntity<>(response, HttpStatus.SERVICE_UNAVAILABLE);
    }

    /**
     * Handle resource not found errors
     */
    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<Map<String, Object>> handleResourceNotFoundException(
            ResourceNotFoundException ex,
            WebRequest request) {
        
        Map<String, Object> response = new HashMap<>();
        response.put("timestamp", LocalDateTime.now());
        response.put("status", HttpStatus.NOT_FOUND.value());
        response.put("error", "Not Found");
        response.put("message", ex.getMessage());
        response.put("path", request.getDescription(false).replace("uri=", ""));
        
        return new ResponseEntity<>(response, HttpStatus.NOT_FOUND);
    }

    /**
     * Handle invalid input (e.g. empty uploads, unreadable documents).
     * Maps IllegalArgumentException to a 400 with the message.
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> handleIllegalArgumentException(
            IllegalArgumentException ex,
            WebRequest request) {

        Map<String, Object> response = new HashMap<>();
        response.put("timestamp", LocalDateTime.now());
        response.put("status", HttpStatus.BAD_REQUEST.value());
        response.put("error", "Bad Request");
        response.put("message", ex.getMessage());
        response.put("path", request.getDescription(false).replace("uri=", ""));

        return new ResponseEntity<>(response, HttpStatus.BAD_REQUEST);
    }

    /**
     * Handle unauthorized access
     */
    @ExceptionHandler(UnauthorizedException.class)
    public ResponseEntity<Map<String, Object>> handleUnauthorizedException(
            UnauthorizedException ex,
            WebRequest request) {
        
        Map<String, Object> response = new HashMap<>();
        response.put("timestamp", LocalDateTime.now());
        response.put("status", HttpStatus.UNAUTHORIZED.value());
        response.put("error", "Unauthorized");
        response.put("message", ex.getMessage());
        response.put("path", request.getDescription(false).replace("uri=", ""));
        
        return new ResponseEntity<>(response, HttpStatus.UNAUTHORIZED);
    }

    /**
     * Handle all other unexpected exceptions
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleGlobalException(
            Exception ex,
            WebRequest request) {
        
        Map<String, Object> response = new HashMap<>();
        response.put("timestamp", LocalDateTime.now());
        response.put("status", HttpStatus.INTERNAL_SERVER_ERROR.value());
        response.put("error", "Internal Server Error");
        String detail = buildFullErrorDetail(ex);
        response.put("message", detail);
        response.put("response", detail);
        response.put("content", detail);
        response.put("path", request.getDescription(false).replace("uri=", ""));
        
        log.error("Unhandled exception: {}", ex.getMessage(), ex);
        
        return new ResponseEntity<>(response, HttpStatus.INTERNAL_SERVER_ERROR);
    }

    private static void addExactErrorFields(
            Map<String, Object> response, Exception ex, HttpStatus status) {
        if (status == HttpStatus.INTERNAL_SERVER_ERROR) {
            String detail = buildFullErrorDetail(ex);
            response.put("response", detail);
            response.put("content", detail);
        }
    }

    /**
     * Build a full error detail string including the cause chain so the
     * frontend sees the exact root cause (e.g. a 401 from Google Gemini)
     * rather than just the wrapper RuntimeException.
     */
    private static String buildFullErrorDetail(Exception ex) {
        StringBuilder sb = new StringBuilder();
        Throwable current = ex;
        while (current != null) {
            if (sb.length() > 0) sb.append(" Caused by: ");
            sb.append(current.getClass().getName())
              .append(": ")
              .append(current.getMessage());
            current = current.getCause();
        }
        return sb.toString();
    }
}
