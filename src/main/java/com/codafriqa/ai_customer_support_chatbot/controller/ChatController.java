package com.codafriqa.ai_customer_support_chatbot.controller;

import com.codafriqa.ai_customer_support_chatbot.dto.ChatRequestDto;
import com.codafriqa.ai_customer_support_chatbot.dto.ChatResponseDto;
import com.codafriqa.ai_customer_support_chatbot.dto.SessionInfoDto;
import com.codafriqa.ai_customer_support_chatbot.service.ChatService;
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

    public ChatController(ChatService chatService) {
        this.chatService = chatService;
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
            ChatResponseDto response = chatService.sendMessage(request.getMessage(), request.getSessionId());
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Chat request failed ({}: {}); returning fallback response",
                    e.getClass().getSimpleName(), e.getMessage(), e);
            ChatResponseDto fallback = new ChatResponseDto(
                    "I'm sorry, I'm having trouble processing your request right now. " +
                    "Please try again shortly, or ask to speak with a human support agent.",
                    request.getSessionId(), null);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(fallback);
        }
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