package com.codafriqa.ai_customer_support_chatbot.controller;

import com.codafriqa.ai_customer_support_chatbot.dto.PageResponse;
import com.codafriqa.ai_customer_support_chatbot.dto.TicketDto;
import com.codafriqa.ai_customer_support_chatbot.service.SupportTicketService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
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
public class TicketController {

    private final SupportTicketService ticketService;

    public TicketController(SupportTicketService ticketService) {
        this.ticketService = ticketService;
    }

    /**
     * List tickets with optional status / priority / assignedAgentId filters
     * and pagination.
     */
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
    @PostMapping("/{id}/close")
    public ResponseEntity<TicketDto> close(@PathVariable Long id) {
        return ResponseEntity.ok(ticketService.toDto(ticketService.close(id)));
    }
}
