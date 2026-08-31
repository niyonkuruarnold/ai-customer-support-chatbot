package com.codafriqa.ai_customer_support_chatbot.controller;

import com.codafriqa.ai_customer_support_chatbot.dto.PageResponse;
import com.codafriqa.ai_customer_support_chatbot.dto.TicketDto;
import com.codafriqa.ai_customer_support_chatbot.service.SupportTicketService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/**
 * Admin dashboard endpoints for ticket lifecycle management.
 *
 * Available under both /api/tickets and /api/v1/tickets (the codebase uses
 * unprefixed paths, but the /api/v1 alias matches the API spec). Requires
 * authentication (see SecurityConfig).
 *
 * Pagination: page is 0-based (first page = 0), sort by any ticket field,
 * e.g. GET /api/v1/tickets?status=ESCALATED&priority=HIGH&page=0&size=10&sort=updatedAt,desc
 */
@RestController
@RequestMapping({"/api/tickets", "/api/v1/tickets"})
@Tag(name = "Ticket Management", description = "Admin dashboard endpoints for ticket lifecycle management (requires Basic auth)")
public class TicketController {

    private final SupportTicketService ticketService;

    public TicketController(SupportTicketService ticketService) {
        this.ticketService = ticketService;
    }

    /**
     * List tickets with optional status / priority / assignedAgentId filters
     * and pagination.
     */
    @Operation(
            summary = "List tickets",
            description = "Retrieve a paginated list of support tickets with optional filtering by status, " +
                    "priority, or assigned agent. Results are sorted by updatedAt descending by default. " +
                    "Requires HTTP Basic authentication.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Paginated ticket list returned"),
            @ApiResponse(responseCode = "401", description = "Authentication required")
    })
    @GetMapping
    public ResponseEntity<PageResponse<TicketDto>> listTickets(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String priority,
            @RequestParam(required = false) Long assignedAgentId,
            @PageableDefault(size = 10, sort = "updatedAt", direction = Sort.Direction.DESC) Pageable pageable) {
        Page<TicketDto> result = ticketService.list(status, priority, assignedAgentId, pageable);
        return ResponseEntity.ok(PageResponse.from(result));
    }

    /** Transition a resolved ticket to CLOSED (terminal state). */
    @Operation(
            summary = "Close a resolved ticket",
            description = "Transition a ticket from RESOLVED to CLOSED (terminal state). " +
                    "Only tickets in RESOLVED status can be closed. Requires HTTP Basic authentication.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Ticket closed successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid status transition (ticket is not RESOLVED)"),
            @ApiResponse(responseCode = "401", description = "Authentication required"),
            @ApiResponse(responseCode = "404", description = "Ticket not found")
    })
    @PostMapping("/{id}/close")
    public ResponseEntity<TicketDto> close(@PathVariable Long id) {
        return ResponseEntity.ok(ticketService.toDto(ticketService.close(id)));
    }

    /**
     * Admin: update a ticket's status and/or assigned agent in a single call.
     * Accepts optional "status" and "assignedAgent" fields in the request body.
     */
    @Operation(
            summary = "Update ticket",
            description = "Admin update to set status and/or assigned agent on a ticket in a single call. " +
                    "Allowed status values: OPEN, IN_PROGRESS, ESCALATED, RESOLVED, CLOSED. " +
                    "Requires HTTP Basic authentication.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Ticket updated"),
            @ApiResponse(responseCode = "400", description = "Invalid status value"),
            @ApiResponse(responseCode = "401", description = "Authentication required"),
            @ApiResponse(responseCode = "404", description = "Ticket not found")
    })
    @PatchMapping("/{id}")
    public ResponseEntity<TicketDto> updateTicket(
            @PathVariable Long id,
            @RequestBody java.util.Map<String, String> body) {
        String status = body.get("status");
        String agent = body.get("assignedAgent");
        com.codafriqa.ai_customer_support_chatbot.model.SupportTicket ticket = null;
        if (status != null && !status.isBlank()) {
            ticket = ticketService.updateStatus(id, status);
        }
        if (agent != null) {
            ticket = ticketService.updateAssignedAgent(id, agent.isBlank() ? null : agent);
        }
        if (ticket == null) {
            return ResponseEntity.badRequest().build();
        }
        return ResponseEntity.ok(ticketService.toDto(ticket));
    }

    /**
     * Admin: update a ticket's status to any valid value.
     */
    @Operation(
            summary = "Update ticket status",
            description = "Admin override to set any valid status on a ticket. " +
                    "Allowed values: OPEN, IN_PROGRESS, ESCALATED, RESOLVED, CLOSED. " +
                    "Requires HTTP Basic authentication.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Ticket status updated"),
            @ApiResponse(responseCode = "400", description = "Invalid status value"),
            @ApiResponse(responseCode = "401", description = "Authentication required"),
            @ApiResponse(responseCode = "404", description = "Ticket not found")
    })
    @PatchMapping("/{id}/status")
    public ResponseEntity<TicketDto> updateStatus(
            @PathVariable Long id,
            @RequestBody java.util.Map<String, String> body) {
        String status = body.get("status");
        if (status == null || status.isBlank()) {
            return ResponseEntity.badRequest().build();
        }
        return ResponseEntity.ok(ticketService.toDto(ticketService.updateStatus(id, status)));
    }

    /**
     * Admin: reassign a ticket to a different agent.
     */
    @Operation(
            summary = "Update ticket agent assignment",
            description = "Reassign a ticket to a different support agent by name. " +
                    "Requires HTTP Basic authentication.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Agent assignment updated"),
            @ApiResponse(responseCode = "401", description = "Authentication required"),
            @ApiResponse(responseCode = "404", description = "Ticket not found")
    })
    @PatchMapping("/{id}/agent")
    public ResponseEntity<TicketDto> updateAgent(
            @PathVariable Long id,
            @RequestBody java.util.Map<String, String> body) {
        String agentName = body.getOrDefault("assignedAgent", "");
        return ResponseEntity.ok(ticketService.toDto(ticketService.updateAssignedAgent(id, agentName.isBlank() ? null : agentName)));
    }

    /**
     * Admin: permanently delete a ticket.
     */
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(
            summary = "Delete ticket",
            description = "Permanently remove a ticket and its associated data. " +
                    "Requires HTTP Basic authentication and ADMIN role.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Ticket deleted successfully"),
            @ApiResponse(responseCode = "403", description = "Insufficient permissions (ADMIN role required)"),
            @ApiResponse(responseCode = "401", description = "Authentication required"),
            @ApiResponse(responseCode = "404", description = "Ticket not found")
    })
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteTicket(@PathVariable Long id) {
        ticketService.deleteTicket(id);
        return ResponseEntity.noContent().build();
    }
}
