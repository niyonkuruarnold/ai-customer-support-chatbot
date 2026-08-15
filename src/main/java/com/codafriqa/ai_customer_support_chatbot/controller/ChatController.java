package com.codafriqa.ai_customer_support_chatbot.controller;

import com.codafriqa.ai_customer_support_chatbot.dto.ChatRequestDto;
import com.codafriqa.ai_customer_support_chatbot.dto.ChatResponseDto;
import com.codafriqa.ai_customer_support_chatbot.dto.SessionInfoDto;
import com.codafriqa.ai_customer_support_chatbot.service.ChatService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * REST Controller for handling chat requests from the Vue.js frontend
 */
@RestController
@RequestMapping("/api/chat")
@CrossOrigin(origins = {"http://localhost:5173", "http://localhost:3000"})
public class ChatController {

    private final ChatService chatService;

    public ChatController(ChatService chatService) {
        this.chatService = chatService;
    }

    /**
     * Send a chat message and get AI response.
     * The backend persists the message in a session (created on first
     * message when sessionId is absent) and returns the session id plus
     * its status so the frontend can continue the conversation.
     * @param request ChatRequestDto with validated message and optional sessionId
     * @return ChatResponseDto with AI response, sessionId and session status
     */
    @PostMapping
    public ResponseEntity<ChatResponseDto> sendMessage(@Valid @RequestBody ChatRequestDto request) {
        ChatResponseDto response = chatService.sendMessage(request.getMessage(), request.getSessionId());
        return ResponseEntity.ok(response);
    }

    /**
     * Fetch session status + transcript (used by the customer frontend to
     * restore history and pick up agent replies after a handoff)
     */
    @GetMapping("/session/{id}")
    public ResponseEntity<SessionInfoDto> getSession(@PathVariable Long id) {
        return ResponseEntity.ok(chatService.getSessionInfo(id));
    }

    /**
     * Health check endpoint for frontend
     */
    @GetMapping("/health")
    public ResponseEntity<String> health() {
        return ResponseEntity.ok("AI Customer Support Chatbot is running");
    }
}