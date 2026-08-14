package com.codafriqa.ai_customer_support_chatbot.controller;

import com.codafriqa.ai_customer_support_chatbot.service.ChatService; // Ensure this package matches your ChatService file
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/chat")
public class ChatController {

    private final ChatService chatService;

    public ChatController(ChatService chatService) {
        this.chatService = chatService;
    }

    @PostMapping
    public ResponseEntity<Map<String, String>> sendMessage(@RequestBody Map<String, String> request) {
        String userMessage = request.get("message");

        // Generate response using your OpenAI/Spring AI logic
        String aiResponse = chatService.generateResponse(userMessage);

        return ResponseEntity.ok(Map.of("response", aiResponse));
    }
}