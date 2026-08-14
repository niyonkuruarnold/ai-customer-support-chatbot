package com.codafriqa.ai_customer_support_chatbot.controller;

import com.codafriqa.ai_customer_support_chatbot.dto.ChatRequestDto;
import com.codafriqa.ai_customer_support_chatbot.dto.ChatResponseDto;
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
     * Send a chat message and get AI response
     * @param request ChatRequestDto with validated message field
     * @return ChatResponseDto with AI response
     */
    @PostMapping
    public ResponseEntity<ChatResponseDto> sendMessage(@Valid @RequestBody ChatRequestDto request) {
        String userMessage = request.getMessage();
        String aiResponse = chatService.generateResponse(userMessage);

        ChatResponseDto response = new ChatResponseDto(aiResponse);
        return ResponseEntity.ok(response);
    }

    /**
     * Health check endpoint for frontend
     */
    @GetMapping("/health")
    public ResponseEntity<String> health() {
        return ResponseEntity.ok("AI Customer Support Chatbot is running");
    }
}