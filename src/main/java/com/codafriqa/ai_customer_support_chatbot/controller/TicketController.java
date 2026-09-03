package com.codafriqa.ai_customer_support_chatbot.controller;

import com.codafriqa.ai_customer_support_chatbot.dto.*;
import com.codafriqa.ai_customer_support_chatbot.model.TicketActivityLog;
import com.codafriqa.ai_customer_support_chatbot.service.ActivityLogService;
import com.codafriqa.ai_customer_support_chatbot.service.SupportTicketService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * REST endpoints for ticket and case management.
 * Provides comprehensive ticket lifecycle management with activity logging.
 */
@RestController
@RequestMapping({"/api/tickets", "/api/v1/tickets"})
@Tag(name = "Tickets", description = "Ticket and case management endpoints")
public class TicketController {

    private final SupportTicketService ticketService;
    private final ActivityLogService activityLogService;

    public TicketController(SupportTicketService ticketService, ActivityLogService activityLogService) {
        this.ticketService = ticketService;
        this.activityLogService = activityLogService;
    }

    /**
     * Update ticket status with logging.
     * POST /api/tickets/{id}/status
     */
    @Operation(summary = "Update ticket status", description = "Update the status of a ticket with full state machine validation.")
    @PostMapping("/{id}/status")
    public ResponseEntity<TicketDto> updateStatus(
            @PathVariable Long id,
            @Valid @RequestBody TicketStatusUpdateDto request,
            Authentication authentication) {
        String actorName = authentication != null ? authentication.getName() : "System";
        var ticket = ticketService.updateStatus(id, request.status());
        return ResponseEntity.ok(ticketService.toDto(ticket));
    }

    /**
     * Update ticket priority with logging.
     * POST /api/tickets/{id}/priority
     */
    @Operation(summary = "Update ticket priority", description = "Update the priority of a ticket.")
    @PostMapping("/{id}/priority")
    public ResponseEntity<TicketDto> updatePriority(
            @PathVariable Long id,
            @Valid @RequestBody TicketPriorityUpdateDto request,
            Authentication authentication) {
        String actorName = authentication != null ? authentication.getName() : "System";
        var ticket = ticketService.updatePriority(id, request.priority());
        return ResponseEntity.ok(ticketService.toDto(ticket));
    }

    /**
     * Reassign ticket to a different agent with logging.
     * POST /api/tickets/{id}/assign
     */
    @Operation(summary = "Reassign ticket", description = "Reassign a ticket to a different agent.")
    @PostMapping("/{id}/assign")
    public ResponseEntity<TicketDto> reassignTicket(
            @PathVariable Long id,
            @Valid @RequestBody TicketAssignmentDto request,
            Authentication authentication) {
        String actorName = authentication != null ? authentication.getName() : "System";
        var ticket = ticketService.updateAssignedAgent(id, request.assignedAgent());
        return ResponseEntity.ok(ticketService.toDto(ticket));
    }

    /**
     * Add a note (public or internal) to a ticket with logging.
     * POST /api/tickets/{id}/notes
     */
    @Operation(summary = "Add ticket note", description = "Add a public reply or internal note to a ticket.")
    @PostMapping("/{id}/notes")
    public ResponseEntity<Map<String, Object>> addNote(
            @PathVariable Long id,
            @Valid @RequestBody TicketNoteDto request,
            Authentication authentication) {
        String actorName = authentication != null ? authentication.getName() : "System";
        Long actorId = null; // Would need to extract from authentication
        
        // Log the note/reply
        activityLogService.logReply(id, actorId, actorName, request.content(), request.isInternal());
        
        return ResponseEntity.ok(Map.of(
            "success", true,
            "message", request.isInternal() ? "Internal note added" : "Reply added",
            "ticketId", id
        ));
    }

    /**
     * Reopen a resolved or closed ticket.
     * POST /api/tickets/{id}/reopen
     */
    @Operation(summary = "Reopen ticket", description = "Reopen a resolved or closed ticket.")
    @PostMapping("/{id}/reopen")
    public ResponseEntity<TicketDto> reopenTicket(
            @PathVariable Long id,
            @RequestBody(required = false) Map<String, String> body,
            Authentication authentication) {
        String actorName = authentication != null ? authentication.getName() : "System";
        String reason = body != null ? body.get("reason") : null;
        var ticket = ticketService.reopen(id, actorName, reason);
        return ResponseEntity.ok(ticketService.toDto(ticket));
    }

    /**
     * Get ticket activity logs (timeline).
     * GET /api/tickets/{id}/activity
     */
    @Operation(summary = "Get ticket activity logs", description = "Get the chronological activity log for a ticket.")
    @GetMapping("/{id}/activity")
    public ResponseEntity<List<TicketActivityLogDto>> getActivityLogs(
            @PathVariable Long id,
            @RequestParam(defaultValue = "false") boolean customerOnly) {
        List<TicketActivityLog> logs;
        if (customerOnly) {
            logs = activityLogService.getCustomerVisibleLogs(id);
        } else {
            logs = activityLogService.getTicketActivityLogs(id);
        }
        
        List<TicketActivityLogDto> dtos = logs.stream()
            .map(log -> new TicketActivityLogDto(
                log.getId(),
                log.getTicketId(),
                log.getActorId(),
                log.getActorName(),
                log.getActionType(),
                log.getPreviousValue(),
                log.getNewValue(),
                log.getDescription(),
                log.getTimestamp(),
                log.isCustomerVisible()
            ))
            .toList();
        
        return ResponseEntity.ok(dtos);
    }

    /**
     * Get ticket by reference.
     * GET /api/tickets/reference/{reference}
     */
    @Operation(summary = "Get ticket by reference", description = "Get a ticket by its unique reference number.")
    @GetMapping("/reference/{reference}")
    public ResponseEntity<TicketDto> getByReference(@PathVariable String reference) {
        // This would need to be implemented in the service
        return ResponseEntity.notFound().build();
    }

    /**
     * Get ticket statistics.
     * GET /api/tickets/stats
     */
    @Operation(summary = "Get ticket statistics", description = "Get ticket counts by status, priority, and agent.")
    @GetMapping("/stats")
    public ResponseEntity<Map<String, Object>> getStats() {
        Map<String, Object> stats = Map.of(
            "totalNew", ticketService.countByStatus("NEW"),
            "totalOpen", ticketService.countByStatus("OPEN"),
            "totalInProgress", ticketService.countByStatus("IN_PROGRESS"),
            "totalPendingCustomer", ticketService.countByStatus("PENDING_CUSTOMER"),
            "totalPendingInternal", ticketService.countByStatus("PENDING_INTERNAL"),
            "totalResolved", ticketService.countByStatus("RESOLVED"),
            "totalClosed", ticketService.countByStatus("CLOSED"),
            "totalReopened", ticketService.countByStatus("REOPENED")
        );
        return ResponseEntity.ok(stats);
    }
}
