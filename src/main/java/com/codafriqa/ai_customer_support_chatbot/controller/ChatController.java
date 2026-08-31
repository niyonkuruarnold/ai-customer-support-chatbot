package com.codafriqa.ai_customer_support_chatbot.controller;

import com.codafriqa.ai_customer_support_chatbot.dto.ChatRequestDto;
import com.codafriqa.ai_customer_support_chatbot.dto.ChatResponseDto;
import com.codafriqa.ai_customer_support_chatbot.dto.SessionInfoDto;
import com.codafriqa.ai_customer_support_chatbot.dto.SuggestedQuestionsDto;
import com.codafriqa.ai_customer_support_chatbot.service.ChatService;
import com.codafriqa.ai_customer_support_chatbot.service.RagService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * REST Controller for handling chat requests from the Vue.js frontend.
 *
 * <p>Mapped under both /api/chat and /api/v1/chat (with the message POST
 * also reachable as /api/v1/chat/message) following the codebase convention
 * of unprefixed paths with /api/v1 aliases matching the API spec — the
 * frontend keeps using /api/chat, the spec path /api/v1/chat/message also
 * works.
 */
@RestController
@RequestMapping({"/api/chat", "/api/v1/chat"})
@CrossOrigin(origins = {"http://localhost:5173", "http://localhost:3000"})
@Tag(name = "Chat", description = "AI-powered chat endpoints for customer conversations")
public class ChatController {

    private static final Logger log = LoggerFactory.getLogger(ChatController.class);

    private final ChatService chatService;
    private final RagService ragService;

    public ChatController(ChatService chatService, RagService ragService) {
        this.chatService = chatService;
        this.ragService = ragService;
    }

    /**
     * Send a chat message and get AI response.
     * The backend persists the message in a session (created on first
     * message when sessionId is absent) and returns the session id plus
     * its status so the frontend can continue the conversation.
     */
    @Operation(
            summary = "Send a chat message",
            description = "Send a user message to the AI chatbot. The backend persists the message in a session " +
                    "(created on the first message when sessionId is absent) and returns the AI-generated response " +
                    "along with the session id and status.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "AI response returned successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid request (empty message or validation error)"),
            @ApiResponse(responseCode = "500", description = "AI service temporarily unavailable")
    })
    @PostMapping({"", "/message"})
    public ResponseEntity<ChatResponseDto> sendMessage(@Valid @RequestBody ChatRequestDto request) {
        try {
                        return ResponseEntity.ok(generateResponse(request.getMessage(), request.getSessionId()));
        } catch (Exception e) {
            log.error("Chat request failed ({}: {})",
                    e.getClass().getSimpleName(), e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(errorResponse(request.getSessionId(), e));
        }
    }

    /**
     * Session-oriented alias for clients that include the session id in the
     * URL. It delegates to the same persistence, RAG retrieval, and AI
     * generation pipeline as the primary chat route.
     */
    @PostMapping({"/session/{id}/messages", "/sessions/{id}/messages"})
    public ResponseEntity<ChatResponseDto> sendSessionMessage(
            @PathVariable Long id, @Valid @RequestBody ChatRequestDto request) {
        try {
            return ResponseEntity.ok(generateResponse(request.getMessage(), id));
        } catch (Exception e) {
            log.error("Session chat request failed ({}: {})",
                    e.getClass().getSimpleName(), e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(fallbackResponse(id, e));
        }
    }

    private ChatResponseDto generateResponse(String message, Long sessionId) {
        return chatService.sendMessage(message, sessionId);
    }

    private ChatResponseDto fallbackResponse(Long sessionId, Exception exception) {
                return errorResponse(sessionId, exception);
        }

        private ChatResponseDto errorResponse(Long sessionId, Exception exception) {
                String detail = buildFullErrorDetail(exception);
                return new ChatResponseDto(detail, sessionId, null);
    }

        /**
         * Build a full error detail string including the cause chain so the
         * frontend (and logs) show the exact root cause (e.g. a 401 from
         * Google Gemini API) rather than just the wrapper RuntimeException.
         */
        private static String buildFullErrorDetail(Exception exception) {
                StringBuilder sb = new StringBuilder();
                Throwable current = exception;
                while (current != null) {
                        if (sb.length() > 0) sb.append(" Caused by: ");
                        sb.append(current.getClass().getName())
                          .append(": ")
                          .append(current.getMessage());
                        current = current.getCause();
                }
                return sb.toString();
    }

    /**
     * Fetch dynamically generated suggested questions from the vector store.
     *
     * <p>Extracts top sample queries from the currently indexed knowledge
     * base content so the customer-facing frontend can display up-to-date
     * quick-suggest chips instead of hardcoded fallback suggestions.
     */
    @Operation(
            summary = "Get suggested questions",
            description = "Return a list of suggested quick-question chips generated from the " +
                    "currently indexed knowledge base content in the vector store.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Suggested questions returned successfully"),
            @ApiResponse(responseCode = "500", description = "Vector store unavailable; fallback questions returned")
    })
    @GetMapping("/suggested-questions")
    public ResponseEntity<SuggestedQuestionsDto> getSuggestedQuestions() {
        List<String> questions = ragService.getSuggestedQuestions();
        boolean fromKB = questions.stream().noneMatch(q ->
                q.contains("support hours") || q.contains("track my order") ||
                q.contains("refund policy") || q.contains("human agent"));
        return ResponseEntity.ok(new SuggestedQuestionsDto(questions, fromKB));
    }

    /**
     * Fetch session status + transcript (used by the customer frontend to
     * restore history and pick up agent replies after a handoff)
     */
    @Operation(
            summary = "Get chat session info",
            description = "Retrieve the full session state including status (ACTIVE / ESCALATED) and " +
                    "the complete message transcript. Used by the customer frontend to restore history " +
                    "and pick up agent replies after a human handoff.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Session info returned successfully"),
            @ApiResponse(responseCode = "404", description = "Session not found")
    })
    @GetMapping("/session/{id}")
    public ResponseEntity<SessionInfoDto> getSession(@PathVariable Long id) {
        return ResponseEntity.ok(chatService.getSessionInfo(id));
    }

    /**
     * Close/end the active chat session (marks it as CLOSED on the backend).
     * The frontend calls this when the customer clicks "New Conversation"
     * so the session is properly archived before the UI resets.
     */
    @Operation(
            summary = "Close chat session",
            description = "Mark an existing chat session as CLOSED. The frontend calls this when the customer " +
                    "starts a new conversation so the session is properly archived.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Session closed successfully"),
            @ApiResponse(responseCode = "404", description = "Session not found")
    })
    @PostMapping("/session/{id}/close")
    public ResponseEntity<Void> closeSession(@PathVariable Long id) {
        chatService.closeSession(id);
        return ResponseEntity.ok().build();
    }

    /**
     * Health check endpoint for frontend
     */
    @Operation(
            summary = "Health check",
            description = "Simple health check endpoint to verify the backend is running.")
    @ApiResponse(responseCode = "200", description = "Service is healthy")
    @GetMapping("/health")
    public ResponseEntity<String> health() {
        return ResponseEntity.ok("AI Customer Support Chatbot is running");
    }
}