package com.codafriqa.ai_customer_support_chatbot.service;

import com.codafriqa.ai_customer_support_chatbot.dto.TicketDto;
import com.codafriqa.ai_customer_support_chatbot.exception.ResourceNotFoundException;
import com.codafriqa.ai_customer_support_chatbot.model.SupportTicket;
import com.codafriqa.ai_customer_support_chatbot.model.TicketPriority;
import com.codafriqa.ai_customer_support_chatbot.model.TicketStatus;
import com.codafriqa.ai_customer_support_chatbot.model.User;
import com.codafriqa.ai_customer_support_chatbot.repository.SupportTicketRepository;
import com.codafriqa.ai_customer_support_chatbot.repository.UserRepository;
import jakarta.persistence.criteria.Predicate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Service
public class SupportTicketService {

    private static final Logger log = LoggerFactory.getLogger(SupportTicketService.class);

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

    /**
     * Each mutator below runs in a single transaction so the ticket is loaded,
     * validated against the state machine, and saved from the same persistence
     * context — no detached/stale instances are written back.
     */
    @Transactional
    public SupportTicket open(Long userId, Long sessionId, String subject, String description) {
        SupportTicket ticket = new SupportTicket(userId, sessionId, subject, description);
        ticket.setStatus(TicketStatus.OPEN);
        ticket = ticketRepository.save(ticket);
        activityLogService.logCustom(ticket.getId(), userId, "CUSTOMER", "CREATED",
            "Ticket created: " + subject, true);
        emailAfterCommit(
                userEmail(userId), ticket, EmailNotificationService.TicketEvent.OPENED);
        return ticket;
    }

    @Transactional
    public SupportTicket takeOver(Long id, String agentName) {
        SupportTicket ticket = findTicket(id);
        TicketStatus oldStatus = ticket.getStatus();
        String oldAssignee = ticket.getAssignedAgent();
        transitionTo(ticket, TicketStatus.OPEN);
        ticket.setAssignedAgent(agentName);
        ticket = ticketRepository.save(ticket);
        activityLogService.logStatusChange(ticket.getId(), null, "AGENT", oldStatus.name(), "OPEN");
        activityLogService.logAssignment(ticket.getId(), null, "AGENT", oldAssignee, agentName);
        emailAfterCommit(
                userEmail(ticket.getUserId()), ticket, EmailNotificationService.TicketEvent.UPDATED);
        return ticket;
    }

    @Transactional
    public SupportTicket resolve(Long id, String agentName) {
        SupportTicket ticket = findTicket(id);
        TicketStatus oldStatus = ticket.getStatus();
        transitionTo(ticket, TicketStatus.RESOLVED);
        if (ticket.getAssignedAgent() == null) ticket.setAssignedAgent(agentName);
        ticket = ticketRepository.save(ticket);
        activityLogService.logStatusChange(ticket.getId(), null, "AGENT", oldStatus.name(), "RESOLVED");
        emailAfterCommit(
                userEmail(ticket.getUserId()), ticket, EmailNotificationService.TicketEvent.RESOLVED);
        return ticket;
    }

    @Transactional
    public SupportTicket close(Long id) {
        SupportTicket ticket = findTicket(id);
        TicketStatus oldStatus = ticket.getStatus();
        transitionTo(ticket, TicketStatus.CLOSED);
        ticket.setClosedAt(LocalDateTime.now());
        ticket = ticketRepository.save(ticket);
        activityLogService.logStatusChange(ticket.getId(), null, "SYSTEM", oldStatus.name(), "CLOSED");
        return ticket;
    }

    @Transactional
    public SupportTicket updateStatus(Long id, String targetStatus) {
        SupportTicket ticket = findTicket(id);
        String normalized = targetStatus.trim().toUpperCase(Locale.ROOT);
        TicketStatus newStatus;
        try {
            newStatus = TicketStatus.valueOf(normalized);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid ticket status: " + normalized);
        }
        TicketStatus oldStatus = ticket.getStatus();
        ticket.setStatus(newStatus);
        if (newStatus == TicketStatus.REOPENED) {
            ticket.setReopenedAt(LocalDateTime.now());
            ticket.setCustomerReplyCount(0);
        }
        if (newStatus == TicketStatus.PENDING_CUSTOMER) {
            ticket.setCustomerReplyCount(0);
        }
        SupportTicket saved = ticketRepository.save(ticket);
        activityLogService.logStatusChange(saved.getId(), null, "ADMIN", oldStatus.name(), newStatus.name());
        emailAfterCommit(
                userEmail(ticket.getUserId()), saved, EmailNotificationService.TicketEvent.UPDATED);
        return saved;
    }

    @Transactional
    public SupportTicket updateAssignedAgent(Long id, String agentName) {
        SupportTicket ticket = findTicket(id);
        String oldAssignee = ticket.getAssignedAgent();
        ticket.setAssignedAgent(agentName);
        ticket = ticketRepository.save(ticket);
        activityLogService.logAssignment(ticket.getId(), null, "ADMIN", oldAssignee, agentName);
        return ticket;
    }

    @PreAuthorize("hasRole('ADMIN')")
    @Transactional
    public void deleteTicket(Long id) {
        SupportTicket ticket = findTicket(id);
        ticketRepository.delete(ticket);
    }

    private void transitionTo(SupportTicket ticket, TicketStatus target) {
        TicketStatus current = ticket.getStatus() == null ? TicketStatus.NEW : ticket.getStatus();
        if (!canTransition(current, target)) {
            throw new IllegalArgumentException(
                    "Invalid ticket status transition: " + current + " -> " + target
                            + " (ticket " + ticket.getId() + ")");
        }
        ticket.setStatus(target);
    }

    /**
     * Send the customer notification only AFTER the surrounding transaction
     * commits. SMTP is slow network I/O and may block for seconds when the
     * mail server is unreachable — running it inside the transaction would
     * hold a Hikari pool connection for the duration, and a few concurrent
     * ticket operations could exhaust the pool so that every other endpoint
     * (agent activity timeline, chat, …) stops responding. If the transaction
     * rolls back, no misleading email is sent either.
     */
    private void emailAfterCommit(String to, SupportTicket ticket,
                                  EmailNotificationService.TicketEvent event) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    sendEmailQuietly(to, ticket, event);
                }
            });
        } else {
            // No active transaction (e.g. plain unit tests) — send immediately.
            sendEmailQuietly(to, ticket, event);
        }
    }

    /** Never let a notification problem (e.g. a rejected async task) fail the caller. */
    private void sendEmailQuietly(String to, SupportTicket ticket,
                                  EmailNotificationService.TicketEvent event) {
        try {
            // @Async: hands the message to the task executor and returns at
            // once — no SMTP I/O on the request thread or commit path.
            emailService.sendTicketNotification(to, ticket, event);
        } catch (Exception e) {
            log.warn("Could not schedule {} email for ticket #{}: {}: {}",
                    event, ticket.getId(), e.getClass().getSimpleName(), e.getMessage());
        }
    }

    private static boolean canTransition(TicketStatus from, TicketStatus to) {
        return switch (to) {
            case OPEN -> from == TicketStatus.NEW || from == TicketStatus.PENDING_CUSTOMER
                          || from == TicketStatus.PENDING_INTERNAL || from == TicketStatus.REOPENED;
            case PENDING_CUSTOMER -> from == TicketStatus.OPEN || from == TicketStatus.RESOLVED;
            case PENDING_INTERNAL -> from == TicketStatus.OPEN || from == TicketStatus.RESOLVED;
            case RESOLVED -> from == TicketStatus.OPEN || from == TicketStatus.PENDING_CUSTOMER
                             || from == TicketStatus.PENDING_INTERNAL;
            case CLOSED -> from == TicketStatus.RESOLVED;
            case REOPENED -> from == TicketStatus.RESOLVED || from == TicketStatus.CLOSED;
            default -> false;
        };
    }

    @Transactional
    public SupportTicket updatePriority(Long id, String newPriority) {
        SupportTicket ticket = findTicket(id);
        TicketPriority oldPriority = ticket.getPriority();
        TicketPriority targetPriority;
        try {
            targetPriority = TicketPriority.valueOf(newPriority.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid priority: " + newPriority);
        }
        ticket.setPriority(targetPriority);
        ticket = ticketRepository.save(ticket);
        activityLogService.logPriorityChange(ticket.getId(), null, "SYSTEM",
            oldPriority.name(), targetPriority.name());
        return ticket;
    }

    @Transactional
    public SupportTicket reopen(Long id, String actorName, String reason) {
        SupportTicket ticket = findTicket(id);
        TicketStatus oldStatus = ticket.getStatus();
        transitionTo(ticket, TicketStatus.REOPENED);
        ticket.setReopenedAt(LocalDateTime.now());
        ticket.setCustomerReplyCount(0);
        ticket = ticketRepository.save(ticket);
        activityLogService.logReopen(ticket.getId(), null, actorName, reason);
        return ticket;
    }

    public Page<TicketDto> list(String status, String priority, Long assignedAgentId, Pageable pageable) {
        Specification<SupportTicket> spec = buildSpec(status, priority, assignedAgentId);
        return ticketRepository.findAll(spec, pageable).map(this::toDto);
    }

    private Specification<SupportTicket> buildSpec(String status, String priority, Long assignedAgentId) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (status != null && !status.isBlank()) {
                // Unknown status names (e.g. legacy IN_PROGRESS) match nothing
                // instead of failing the request — the dashboard then shows
                // its "No tickets match" empty state with HTTP 200.
                TicketStatus statusValue = parseStatus(status);
                predicates.add(statusValue == null
                        ? cb.disjunction()
                        : cb.equal(root.get("status"), statusValue));
            }
            if (priority != null && !priority.isBlank()) {
                TicketPriority priorityValue = parsePriority(priority);
                predicates.add(priorityValue == null
                        ? cb.disjunction()
                        : cb.equal(root.get("priority"), priorityValue));
            }
            if (assignedAgentId != null) {
                String agentEmail = userRepository.findById(assignedAgentId).map(User::getEmail).orElse(null);
                predicates.add(agentEmail == null ? cb.disjunction() : cb.equal(root.get("assignedAgent"), agentEmail));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    public TicketDto toDto(SupportTicket ticket) {
        return new TicketDto(
                ticket.getId(), ticket.getTicketReference(), ticket.getSessionId(), ticket.getUserId(),
                userEmail(ticket.getUserId()),
                ticket.getSubject(), ticket.getDescription(), ticket.getStatus().name(),
                ticket.getPriority().name(), ticket.getCategory(), ticket.getAssignedAgent(), ticket.getSentiment(),
                ticket.getCreatedAt(), ticket.getUpdatedAt());
    }

    public long countByStatus(String status) {
        TicketStatus statusValue = parseStatus(status);
        return statusValue == null ? 0 : ticketRepository.countByStatus(statusValue);
    }

    /** Parse a status name; returns null (match nothing) when unknown. */
    private TicketStatus parseStatus(String status) {
        try {
            return TicketStatus.valueOf(status.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    /** Parse a priority name; returns null (match nothing) when unknown. */
    private TicketPriority parsePriority(String priority) {
        try {
            return TicketPriority.valueOf(priority.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private SupportTicket findTicket(Long id) {
        return ticketRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Support ticket not found: " + id));
    }

    private String userEmail(Long userId) {
        if (userId == null) return null;
        return userRepository.findById(userId).map(User::getEmail).orElse(null);
    }
}
