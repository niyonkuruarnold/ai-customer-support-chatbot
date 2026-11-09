package com.codafriqa.ai_customer_support_chatbot.controller;

import com.codafriqa.ai_customer_support_chatbot.dto.CustomerFeedbackRequest;
import com.codafriqa.ai_customer_support_chatbot.dto.MessageDto;
import com.codafriqa.ai_customer_support_chatbot.service.CustomerChatService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Customer-facing chat endpoints: message history and CSAT feedback.
 */
@RestController
@RequestMapping("/api/v1/chat")
@CrossOrigin(origins = {"http://localhost:5173", "http://localhost:3000"})
@Tag(name = "Customer Chat", description = "Customer conversation history and CSAT feedback")
public class CustomerChatController {

    private static final Logger log = LoggerFactory.getLogger(CustomerChatController.class);

    private final CustomerChatService customerChatService;

    public CustomerChatController(CustomerChatService customerChatService) {
        this.customerChatService = customerChatService;
    }

    /**
     * GET /api/v1/chat/session/{sessionId}/messages
     * Returns the full chronological message history for a conversation.
     */
    @Operation(
            summary = "Get conversation messages",
            description = "Retrieve the full chronological message history for a given session ID.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Messages returned successfully"),
    })
    @GetMapping("/session/{sessionId}/messages")
    public ResponseEntity<List<MessageDto>> getMessages(@PathVariable String sessionId) {
        List<MessageDto> messages = customerChatService.getConversationHistory(sessionId);
        return ResponseEntity.ok(messages);
    }

    /**
     * POST /api/v1/chat/session/{sessionId}/feedback
     * Submit CSAT feedback for a conversation.
     */
    @Operation(
            summary = "Submit CSAT feedback",
            description = "Submit a CSAT score (1-5) and optional comment for a conversation. " +
                    "Closes the conversation after feedback is recorded.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Feedback submitted successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid request (validation error)"),
            @ApiResponse(responseCode = "404", description = "Conversation not found for session"),
    })
    @PostMapping("/session/{sessionId}/feedback")
    public ResponseEntity<Void> submitFeedback(
            @PathVariable String sessionId,
            @Valid @RequestBody CustomerFeedbackRequest request) {
        customerChatService.saveCsatFeedback(sessionId, request);
        log.info("CSAT feedback received for session {}", sessionId);
        return ResponseEntity.ok().build();
    }
}
