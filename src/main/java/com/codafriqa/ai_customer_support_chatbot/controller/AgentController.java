package com.codafriqa.ai_customer_support_chatbot.controller;

import com.codafriqa.ai_customer_support_chatbot.dto.AgentNoteRequestDto;
import com.codafriqa.ai_customer_support_chatbot.dto.AgentReplyRequestDto;
import com.codafriqa.ai_customer_support_chatbot.dto.AgentTicketDetailDto;
import com.codafriqa.ai_customer_support_chatbot.dto.AgentTicketDto;
import com.codafriqa.ai_customer_support_chatbot.service.AgentService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * REST endpoints for the support agent workspace.
 * All endpoints require authentication (see SecurityConfig); the
 * authenticated principal is used as the "current agent".
 *
 * Available under both /api/agent and /api/v1/agent (the codebase uses
 * unprefixed paths, but the /api/v1 alias matches the API spec).
 */
@RestController
@RequestMapping({"/api/agent", "/api/v1/agent"})
public class AgentController {

    private final AgentService agentService;

    public AgentController(AgentService agentService) {
        this.agentService = agentService;
    }

    /** List escalated/open/in-progress tickets -> GET /api/agent/tickets */
    @GetMapping("/tickets")
    public ResponseEntity<List<AgentTicketDto>> listTickets() {
        return ResponseEntity.ok(agentService.listTickets());
    }

    /** Full ticket detail with transcript and internal notes -> GET /api/agent/tickets/{id} */
    @GetMapping("/tickets/{id}")
    public ResponseEntity<AgentTicketDetailDto> getTicket(@PathVariable Long id) {
        return ResponseEntity.ok(agentService.getTicket(id));
    }

    /** Assign the ticket to the current agent -> POST /api/agent/tickets/{id}/takeover */
    @PostMapping("/tickets/{id}/takeover")
    public ResponseEntity<AgentTicketDetailDto> takeOver(@PathVariable Long id,
                                                         Authentication authentication) {
        return ResponseEntity.ok(agentService.takeOver(id, currentAgent(authentication)));
    }

    /** Send an agent reply to the customer -> POST /api/agent/tickets/{id}/reply */
    @PostMapping("/tickets/{id}/reply")
    public ResponseEntity<AgentTicketDetailDto> reply(@PathVariable Long id,
                                                      @Valid @RequestBody AgentReplyRequestDto request,
                                                      Authentication authentication) {
        return ResponseEntity.ok(agentService.reply(id, currentAgent(authentication), request.message()));
    }

    /** Add an internal note -> POST /api/agent/tickets/{id}/notes */
    @PostMapping("/tickets/{id}/notes")
    public ResponseEntity<AgentTicketDetailDto> addNote(@PathVariable Long id,
                                                        @Valid @RequestBody AgentNoteRequestDto request) {
        return ResponseEntity.ok(agentService.addNote(id, request.content()));
    }

    /** Resolve the ticket -> POST /api/agent/tickets/{id}/resolve */
    @PostMapping("/tickets/{id}/resolve")
    public ResponseEntity<AgentTicketDetailDto> resolve(@PathVariable Long id,
                                                        Authentication authentication) {
        return ResponseEntity.ok(agentService.resolve(id, currentAgent(authentication)));
    }

    private String currentAgent(Authentication authentication) {
        return authentication != null && authentication.getName() != null
                ? authentication.getName()
                : "agent";
    }
}
