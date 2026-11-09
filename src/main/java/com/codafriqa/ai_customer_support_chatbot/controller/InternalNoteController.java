package com.codafriqa.ai_customer_support_chatbot.controller;

import com.codafriqa.ai_customer_support_chatbot.dto.InternalNoteDto;
import com.codafriqa.ai_customer_support_chatbot.model.ChatMessage;
import com.codafriqa.ai_customer_support_chatbot.service.InternalNoteService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * REST endpoint for agents to submit internal notes on conversations.
 * Internal notes are broadcast only to agent-specific WebSocket channels
 * and never appear in the customer-facing chat.
 */
@RestController
@RequestMapping({"/api/agent", "/api/v1/agent"})
@Tag(name = "Internal Notes", description = "Agent-only internal notes for conversations")
public class InternalNoteController {

    private final InternalNoteService internalNoteService;

    public InternalNoteController(InternalNoteService internalNoteService) {
        this.internalNoteService = internalNoteService;
    }

    /**
     * POST /api/v1/agent/session/{sessionId}/note
     * Submit an internal note visible only to agents.
     */
    @Operation(
            summary = "Submit internal note",
            description = "Create an internal agent note for a conversation. " +
                    "Broadcast to agent-only WebSocket channels; never sent to the customer.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Note created and broadcast"),
            @ApiResponse(responseCode = "400", description = "Invalid request (blank content)"),
    })
    @PostMapping("/session/{sessionId}/note")
    public ResponseEntity<ChatMessage> submitNote(
            @PathVariable Long sessionId,
            @Valid @RequestBody InternalNoteDto request) {
        ChatMessage saved = internalNoteService.createInternalNote(sessionId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(saved);
    }
}
