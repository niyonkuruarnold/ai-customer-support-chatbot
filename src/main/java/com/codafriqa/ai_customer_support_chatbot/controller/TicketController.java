package com.codafriqa.ai_customer_support_chatbot.controller;

import com.codafriqa.ai_customer_support_chatbot.dto.*;
import com.codafriqa.ai_customer_support_chatbot.service.SupportTicketService;
import com.codafriqa.ai_customer_support_chatbot.service.TicketService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * REST endpoints for ticket and case management.
 *
 * Provides the Section 6.5 ticket lifecycle API:
 *   GET    /api/v1/tickets                          — List/filter tickets (paginated)
 *   POST   /api/v1/tickets                          — Create ticket from conversation
 *   PATCH  /api/v1/tickets/{id}/status               — Update status (state machine enforced)
 *   GET    /api/v1/tickets/{id}/activity-logs         — Fetch chronological activity logs
 */
@RestController
@RequestMapping({"/api/tickets", "/api/v1/tickets"})
@Tag(name = "Tickets", description = "Ticket and case management endpoints")
public class TicketController {

    private final TicketService ticketService;
    private final SupportTicketService supportTicketService;

    public TicketController(TicketService ticketService,
                            SupportTicketService supportTicketService) {
        this.ticketService = ticketService;
        this.supportTicketService = supportTicketService;
    }

    // ---------------------------------------------------------------
    // GET /api/v1/tickets — Paginated ticket list (admin dashboard)
    // ---------------------------------------------------------------

    @Operation(summary = "List tickets",
               description = "Paginated ticket list with optional status/priority/agent filters. "
                           + "Returns an empty page when nothing matches (HTTP 200).")
    @GetMapping
    public ResponseEntity<Page<TicketDto>> listTickets(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String priority,
            @RequestParam(required = false) Long assignedAgentId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {

        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 1), 200);

        return ResponseEntity.ok(supportTicketService.list(
                status, priority, assignedAgentId, PageRequest.of(safePage, safeSize)));
    }

    // ---------------------------------------------------------------
    // POST /api/v1/tickets — Create ticket from conversation
    // ---------------------------------------------------------------

    @Operation(summary = "Create ticket",
               description = "Create a new support ticket from a conversation context.")
    @PostMapping
    public ResponseEntity<TicketDto> createTicket(
            @Valid @RequestBody CreateTicketRequest request,
            Authentication authentication) {
        Long userId = request.userId();
        Long sessionId = request.sessionId();

        var ticket = ticketService.createTicket(
            userId,
            sessionId,
            request.subject(),
            request.description(),
            request.conversationId(),
            request.category(),
            request.priority()
        );

        return ResponseEntity.status(HttpStatus.CREATED).body(ticketService.toDto(ticket));
    }

    // ---------------------------------------------------------------
    // PATCH /api/v1/tickets/{id}/status — Update status (state machine)
    // ---------------------------------------------------------------

    @Operation(summary = "Update ticket status",
               description = "Update the status of a ticket with full state machine validation. "
                           + "Invalid transitions return 409 Conflict.")
    @PatchMapping("/{id}/status")
    public ResponseEntity<TicketDto> updateStatus(
            @PathVariable Long id,
            @Valid @RequestBody TicketStatusUpdateDto request,
            Authentication authentication) {
        String actorRole = resolveActorRole(authentication);
        Long actorId = resolveActorId(authentication);

        var ticket = ticketService.updateStatus(id, request.status(), actorId, actorRole);
        return ResponseEntity.ok(ticketService.toDto(ticket));
    }

    // ---------------------------------------------------------------
    // GET /api/v1/tickets/{id}/activity-logs — Activity timeline
    // (legacy alias /{id}/activity kept for the admin/agent frontend
    //  helper getTicketActivityLogs, which previously 404'd)
    // ---------------------------------------------------------------

    @Operation(summary = "Get ticket activity logs",
               description = "Fetch the full chronological activity log for a ticket. "
                       + "Returns an empty list when no activity is recorded; "
                       + "404 if the ticket does not exist; 401 if unauthenticated.")
    @GetMapping({"/{id}/activity-logs", "/{id}/activity"})
    public ResponseEntity<List<TicketActivityLogDto>> getActivityLogs(
            @PathVariable Long id,
            @RequestParam(defaultValue = "false") boolean customerOnly) {
        List<TicketActivityLogDto> logs;
        if (customerOnly) {
            logs = ticketService.getCustomerVisibleActivityLogs(id);
        } else {
            logs = ticketService.getActivityLogs(id);
        }
        return ResponseEntity.ok(logs);
    }

    // ---------------------------------------------------------------
    // Helper methods
    // ---------------------------------------------------------------

    private String resolveActorRole(Authentication auth) {
        if (auth == null) return "SYSTEM";
        // In a real app, extract role from authorities
        return "AGENT";
    }

    private Long resolveActorId(Authentication auth) {
        if (auth == null) return null;
        // In a real app, extract user ID from JWT claims
        return null;
    }
}
