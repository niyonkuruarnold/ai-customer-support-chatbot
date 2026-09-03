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

import java.time.LocalDateTime;
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
    private final ActivityLogService activityLogService;

    public SupportTicketService(SupportTicketRepository ticketRepository,
                                UserRepository userRepository,
                                EmailNotificationService emailService,
                                ActivityLogService activityLogService) {
        this.ticketRepository = ticketRepository;
        this.userRepository = userRepository;
        this.emailService = emailService;
        this.activityLogService = activityLogService;
    }

    // ------------------------------------------------------------------
    // Lifecycle (state machine)
    // ------------------------------------------------------------------

    /** Create a new ticket in OPEN status and notify the customer. */
    public SupportTicket open(Long userId, Long sessionId, String subject, String description) {
        SupportTicket ticket = new SupportTicket(userId, sessionId, subject, description);
        ticket.setStatus("OPEN");
        ticket = ticketRepository.save(ticket);
        
        // Log ticket creation
        activityLogService.logCustom(ticket.getId(), userId, "System", "CREATED",
            "Ticket created: " + subject, true);
        
        emailService.sendTicketNotification(
                userEmail(userId), ticket, EmailNotificationService.TicketEvent.OPENED);
        return ticket;
    }

    /** Assign an open/escalated ticket to an agent -> IN_PROGRESS (+ email). */
    public SupportTicket takeOver(Long id, String agentName) {
        SupportTicket ticket = findTicket(id);
        String oldStatus = ticket.getStatus();
        String oldAssignee = ticket.getAssignedAgent();
        
        transitionTo(ticket, "IN_PROGRESS");
        ticket.setAssignedAgent(agentName);
        ticket = ticketRepository.save(ticket);
        
        // Log status change and assignment
        activityLogService.logStatusChange(ticket.getId(), null, agentName, oldStatus, "IN_PROGRESS");
        activityLogService.logAssignment(ticket.getId(), null, agentName, oldAssignee, agentName);
        
        emailService.sendTicketNotification(
                userEmail(ticket.getUserId()), ticket, EmailNotificationService.TicketEvent.UPDATED);
        return ticket;
    }

    /** Resolve an open/escalated/in-progress ticket -> RESOLVED (+ email). */
    public SupportTicket resolve(Long id, String agentName) {
        SupportTicket ticket = findTicket(id);
        String oldStatus = ticket.getStatus();
        
        transitionTo(ticket, "RESOLVED");
        if (ticket.getAssignedAgent() == null) {
            ticket.setAssignedAgent(agentName);
        }
        ticket = ticketRepository.save(ticket);
        
        // Log status change
        activityLogService.logStatusChange(ticket.getId(), null, agentName, oldStatus, "RESOLVED");
        
        emailService.sendTicketNotification(
                userEmail(ticket.getUserId()), ticket, EmailNotificationService.TicketEvent.RESOLVED);
        return ticket;
    }

    /** Close a resolved ticket -> CLOSED (terminal; no email per spec). */
    public SupportTicket close(Long id) {
        SupportTicket ticket = findTicket(id);
        String oldStatus = ticket.getStatus();
        
        transitionTo(ticket, "CLOSED");
        ticket.setClosedAt(LocalDateTime.now());
        ticket = ticketRepository.save(ticket);
        
        // Log status change
        activityLogService.logStatusChange(ticket.getId(), null, "System", oldStatus, "CLOSED");
        
        return ticket;
    }

    /**
     * Admin override: set any valid status on a ticket.
     * Allowed target statuses: NEW, OPEN, PENDING_CUSTOMER, PENDING_INTERNAL, RESOLVED, CLOSED, REOPENED.
     * Throws IllegalArgumentException for invalid targets.
     */
    public SupportTicket updateStatus(Long id, String targetStatus) {
        SupportTicket ticket = findTicket(id);
        String normalized = targetStatus.trim().toUpperCase(Locale.ROOT);
        if (!List.of("NEW", "OPEN", "PENDING_CUSTOMER", "PENDING_INTERNAL", 
                     "RESOLVED", "CLOSED", "REOPENED").contains(normalized)) {
            throw new IllegalArgumentException("Invalid ticket status: " + normalized);
        }
        
        String oldStatus = ticket.getStatus();
        ticket.setStatus(normalized);
        
        // Handle reopen logic
        if ("REOPENED".equals(normalized)) {
            ticket.setReopenedAt(LocalDateTime.now());
            ticket.setCustomerReplyCount(0);
        }
        
        // Handle pending customer status
        if ("PENDING_CUSTOMER".equals(normalized)) {
            ticket.setCustomerReplyCount(0);
        }
        
        SupportTicket saved = ticketRepository.save(ticket);
        
        // Log status change
        activityLogService.logStatusChange(saved.getId(), null, "Admin", oldStatus, normalized);
        
        emailService.sendTicketNotification(
                userEmail(ticket.getUserId()), saved, EmailNotificationService.TicketEvent.UPDATED);
        return saved;
    }

    /** Admin: reassign a ticket to a different agent. */
    public SupportTicket updateAssignedAgent(Long id, String agentName) {
        SupportTicket ticket = findTicket(id);
        String oldAssignee = ticket.getAssignedAgent();
        
        ticket.setAssignedAgent(agentName);
        ticket = ticketRepository.save(ticket);
        
        // Log assignment change
        activityLogService.logAssignment(ticket.getId(), null, "Admin", oldAssignee, agentName);
        
        return ticket;
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
     * 
     * State Machine:
     * NEW -> OPEN
     * OPEN -> PENDING_CUSTOMER, PENDING_INTERNAL, IN_PROGRESS, RESOLVED, CLOSED
     * PENDING_CUSTOMER -> OPEN, IN_PROGRESS, RESOLVED, CLOSED (on customer reply)
     * PENDING_INTERNAL -> OPEN, IN_PROGRESS, RESOLVED, CLOSED (when internal work done)
     * IN_PROGRESS -> PENDING_CUSTOMER, PENDING_INTERNAL, RESOLVED, CLOSED
     * RESOLVED -> CLOSED, REOPENED
     * CLOSED -> REOPENED
     * REOPENED -> OPEN, IN_PROGRESS, PENDING_CUSTOMER, PENDING_INTERNAL
     */
    private void transitionTo(SupportTicket ticket, String target) {
        String current = ticket.getStatus() == null ? "NEW" : ticket.getStatus().toUpperCase(Locale.ROOT);
        if (!canTransition(current, target)) {
            throw new IllegalArgumentException(
                    "Invalid ticket status transition: " + current + " -> " + target
                            + " (ticket " + ticket.getId() + ")");
        }
        ticket.setStatus(target);
    }

    private static boolean canTransition(String from, String to) {
        return switch (to) {
            case "OPEN" -> from.equals("NEW") || from.equals("PENDING_CUSTOMER") || 
                          from.equals("PENDING_INTERNAL") || from.equals("REOPENED");
            case "PENDING_CUSTOMER" -> from.equals("OPEN") || from.equals("IN_PROGRESS") || 
                                     from.equals("PENDING_INTERNAL") || from.equals("REOPENED");
            case "PENDING_INTERNAL" -> from.equals("OPEN") || from.equals("IN_PROGRESS") || 
                                    from.equals("PENDING_CUSTOMER") || from.equals("REOPENED");
            case "IN_PROGRESS" -> from.equals("OPEN") || from.equals("PENDING_CUSTOMER") || 
                                from.equals("PENDING_INTERNAL") || from.equals("REOPENED");
            case "RESOLVED" -> from.equals("OPEN") || from.equals("IN_PROGRESS") || 
                             from.equals("PENDING_CUSTOMER") || from.equals("PENDING_INTERNAL");
            case "CLOSED" -> from.equals("RESOLVED");
            case "REOPENED" -> from.equals("RESOLVED") || from.equals("CLOSED");
            default -> false;
        };
    }

    /**
     * Update ticket priority with logging.
     */
    public SupportTicket updatePriority(Long id, String newPriority) {
        SupportTicket ticket = findTicket(id);
        String oldPriority = ticket.getPriority();
        
        if (!List.of("LOW", "MEDIUM", "HIGH", "URGENT").contains(newPriority.toUpperCase(Locale.ROOT))) {
            throw new IllegalArgumentException("Invalid priority: " + newPriority);
        }
        
        ticket.setPriority(newPriority.toUpperCase(Locale.ROOT));
        ticket = ticketRepository.save(ticket);
        
        // Log priority change
        activityLogService.logPriorityChange(ticket.getId(), null, "System", oldPriority, newPriority);
        
        return ticket;
    }

    /**
     * Reopen a resolved or closed ticket.
     */
    public SupportTicket reopen(Long id, String actorName, String reason) {
        SupportTicket ticket = findTicket(id);
        String oldStatus = ticket.getStatus();
        
        transitionTo(ticket, "REOPENED");
        ticket.setReopenedAt(LocalDateTime.now());
        ticket.setCustomerReplyCount(0);
        ticket = ticketRepository.save(ticket);
        
        // Log reopen
        activityLogService.logReopen(ticket.getId(), null, actorName, reason);
        
        return ticket;
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
                ticket.getId(), ticket.getTicketReference(), ticket.getSessionId(), ticket.getUserId(),
                userEmail(ticket.getUserId()),
                ticket.getSubject(), ticket.getDescription(), ticket.getStatus(),
                ticket.getPriority(), ticket.getCategory(), ticket.getAssignedAgent(), ticket.getSentiment(),
                ticket.getCreatedAt(), ticket.getUpdatedAt());
    }

    /**
     * Count tickets by status.
     */
    public long countByStatus(String status) {
        return ticketRepository.countByStatus(status);
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
