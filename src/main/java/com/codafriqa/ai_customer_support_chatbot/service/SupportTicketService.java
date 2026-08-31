package com.codafriqa.ai_customer_support_chatbot.service;

import com.codafriqa.ai_customer_support_chatbot.dto.TicketDto;
import com.codafriqa.ai_customer_support_chatbot.exception.ResourceNotFoundException;
import com.codafriqa.ai_customer_support_chatbot.model.SupportTicket;
import com.codafriqa.ai_customer_support_chatbot.model.User;
import com.codafriqa.ai_customer_support_chatbot.repository.SupportTicketRepository;
import com.codafriqa.ai_customer_support_chatbot.repository.UserRepository;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Ticket lifecycle management: the single source of truth for status
 * transitions (OPEN -&gt; IN_PROGRESS -&gt; RESOLVED -&gt; CLOSED, with ESCALATED
 * as a handoff flavor of OPEN/IN_PROGRESS) and for the automated customer
 * emails fired on opened / updated / resolved events.
 *
 * Illegal transitions throw IllegalArgumentException (mapped to a 400 by the
 * global exception handler) instead of corrupting the ticket state.
 */
@Service
public class SupportTicketService {

    private final SupportTicketRepository ticketRepository;
    private final UserRepository userRepository;
    private final EmailNotificationService emailService;

    public SupportTicketService(SupportTicketRepository ticketRepository,
                                UserRepository userRepository,
                                EmailNotificationService emailService) {
        this.ticketRepository = ticketRepository;
        this.userRepository = userRepository;
        this.emailService = emailService;
    }

    // ------------------------------------------------------------------
    // Lifecycle (state machine)
    // ------------------------------------------------------------------

    /** Create a new ticket in OPEN status and notify the customer. */
    public SupportTicket open(Long userId, Long sessionId, String subject, String description) {
        SupportTicket ticket = ticketRepository.save(
                new SupportTicket(userId, sessionId, subject, description));
        emailService.sendTicketNotification(
                userEmail(userId), ticket, EmailNotificationService.TicketEvent.OPENED);
        return ticket;
    }

    /** Assign an open/escalated ticket to an agent -> IN_PROGRESS (+ email). */
    public SupportTicket takeOver(Long id, String agentName) {
        SupportTicket ticket = findTicket(id);
        transitionTo(ticket, "IN_PROGRESS");
        ticket.setAssignedAgent(agentName);
        ticketRepository.save(ticket);
        emailService.sendTicketNotification(
                userEmail(ticket.getUserId()), ticket, EmailNotificationService.TicketEvent.UPDATED);
        return ticket;
    }

    /** Resolve an open/escalated/in-progress ticket -> RESOLVED (+ email). */
    public SupportTicket resolve(Long id, String agentName) {
        SupportTicket ticket = findTicket(id);
        transitionTo(ticket, "RESOLVED");
        if (ticket.getAssignedAgent() == null) {
            ticket.setAssignedAgent(agentName);
        }
        ticketRepository.save(ticket);
        emailService.sendTicketNotification(
                userEmail(ticket.getUserId()), ticket, EmailNotificationService.TicketEvent.RESOLVED);
        return ticket;
    }

    /** Close a resolved ticket -> CLOSED (terminal; no email per spec). */
    public SupportTicket close(Long id) {
        SupportTicket ticket = findTicket(id);
        transitionTo(ticket, "CLOSED");
        return ticketRepository.save(ticket);
    }

    /**
     * Admin override: set any valid status on a ticket.
     * Allowed target statuses: OPEN, IN_PROGRESS, ESCALATED, RESOLVED, CLOSED.
     * Throws IllegalArgumentException for invalid targets.
     */
    public SupportTicket updateStatus(Long id, String targetStatus) {
        SupportTicket ticket = findTicket(id);
        String normalized = targetStatus.trim().toUpperCase(Locale.ROOT);
        if (!List.of("OPEN", "IN_PROGRESS", "ESCALATED", "RESOLVED", "CLOSED").contains(normalized)) {
            throw new IllegalArgumentException("Invalid ticket status: " + normalized);
        }
        ticket.setStatus(normalized);
        SupportTicket saved = ticketRepository.save(ticket);
        emailService.sendTicketNotification(
                userEmail(ticket.getUserId()), saved, EmailNotificationService.TicketEvent.UPDATED);
        return saved;
    }

    /** Admin: reassign a ticket to a different agent. */
    public SupportTicket updateAssignedAgent(Long id, String agentName) {
        SupportTicket ticket = findTicket(id);
        ticket.setAssignedAgent(agentName);
        return ticketRepository.save(ticket);
    }

    /** Admin: permanently delete a ticket and its associated notes. */
    @PreAuthorize("hasRole('ADMIN')")
    public void deleteTicket(Long id) {
        SupportTicket ticket = findTicket(id);
        ticketRepository.delete(ticket);
    }

    /**
     * Enforce the ticket state machine. Throws IllegalArgumentException on
     * any transition not allowed by the graph below.
     */
    private void transitionTo(SupportTicket ticket, String target) {
        String current = ticket.getStatus() == null ? "OPEN" : ticket.getStatus().toUpperCase(Locale.ROOT);
        if (!canTransition(current, target)) {
            throw new IllegalArgumentException(
                    "Invalid ticket status transition: " + current + " -> " + target
                            + " (ticket " + ticket.getId() + ")");
        }
        ticket.setStatus(target);
    }

    private static boolean canTransition(String from, String to) {
        return switch (to) {
            case "IN_PROGRESS" -> from.equals("OPEN") || from.equals("ESCALATED");
            case "RESOLVED" -> from.equals("OPEN") || from.equals("ESCALATED") || from.equals("IN_PROGRESS");
            case "CLOSED" -> from.equals("RESOLVED");
            default -> false;
        };
    }

    // ------------------------------------------------------------------
    // Admin dashboard: filtering + pagination
    // ------------------------------------------------------------------

    /**
     * List tickets with optional filters (status, priority, assignedAgentId)
     * and Spring Data pagination/sorting.
     *
     * @param assignedAgentId filters by the assigned agent's user account;
     *                        resolved to that account's email (the value
     *                        stored in assignedAgent). Unknown ids match
     *                        nothing.
     */
    public Page<TicketDto> list(String status, String priority, Long assignedAgentId, Pageable pageable) {
        Specification<SupportTicket> spec = buildSpec(status, priority, assignedAgentId);
        return ticketRepository.findAll(spec, pageable).map(this::toDto);
    }

    private Specification<SupportTicket> buildSpec(String status, String priority, Long assignedAgentId) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (status != null && !status.isBlank()) {
                predicates.add(cb.equal(root.get("status"), status.trim().toUpperCase(Locale.ROOT)));
            }
            if (priority != null && !priority.isBlank()) {
                predicates.add(cb.equal(root.get("priority"), priority.trim().toUpperCase(Locale.ROOT)));
            }
            if (assignedAgentId != null) {
                String agentEmail = userRepository.findById(assignedAgentId)
                        .map(User::getEmail)
                        .orElse(null);
                // Unknown id -> match nothing (no ticket is assigned to it)
                predicates.add(agentEmail == null
                        ? cb.disjunction()
                        : cb.equal(root.get("assignedAgent"), agentEmail));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    public TicketDto toDto(SupportTicket ticket) {
        return new TicketDto(
                ticket.getId(), ticket.getSessionId(), ticket.getUserId(),
                userEmail(ticket.getUserId()),
                ticket.getSubject(), ticket.getDescription(), ticket.getStatus(),
                ticket.getPriority(), ticket.getAssignedAgent(), ticket.getSentiment(),
                ticket.getCreatedAt(), ticket.getUpdatedAt());
    }

    private SupportTicket findTicket(Long id) {
        return ticketRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Support ticket not found: " + id));
    }

    private String userEmail(Long userId) {
        if (userId == null) {
            return null;
        }
        return userRepository.findById(userId).map(User::getEmail).orElse(null);
    }
}
