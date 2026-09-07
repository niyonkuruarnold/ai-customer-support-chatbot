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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

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

    public SupportTicket open(Long userId, Long sessionId, String subject, String description) {
        SupportTicket ticket = new SupportTicket(userId, sessionId, subject, description);
        ticket.setStatus(TicketStatus.OPEN);
        ticket = ticketRepository.save(ticket);
        activityLogService.logCustom(ticket.getId(), userId, "CUSTOMER", "CREATED",
            "Ticket created: " + subject, true);
        emailService.sendTicketNotification(
                userEmail(userId), ticket, EmailNotificationService.TicketEvent.OPENED);
        return ticket;
    }

    public SupportTicket takeOver(Long id, String agentName) {
        SupportTicket ticket = findTicket(id);
        TicketStatus oldStatus = ticket.getStatus();
        String oldAssignee = ticket.getAssignedAgent();
        transitionTo(ticket, TicketStatus.OPEN);
        ticket.setAssignedAgent(agentName);
        ticket = ticketRepository.save(ticket);
        activityLogService.logStatusChange(ticket.getId(), null, "AGENT", oldStatus.name(), "OPEN");
        activityLogService.logAssignment(ticket.getId(), null, "AGENT", oldAssignee, agentName);
        emailService.sendTicketNotification(
                userEmail(ticket.getUserId()), ticket, EmailNotificationService.TicketEvent.UPDATED);
        return ticket;
    }

    public SupportTicket resolve(Long id, String agentName) {
        SupportTicket ticket = findTicket(id);
        TicketStatus oldStatus = ticket.getStatus();
        transitionTo(ticket, TicketStatus.RESOLVED);
        if (ticket.getAssignedAgent() == null) ticket.setAssignedAgent(agentName);
        ticket = ticketRepository.save(ticket);
        activityLogService.logStatusChange(ticket.getId(), null, "AGENT", oldStatus.name(), "RESOLVED");
        emailService.sendTicketNotification(
                userEmail(ticket.getUserId()), ticket, EmailNotificationService.TicketEvent.RESOLVED);
        return ticket;
    }

    public SupportTicket close(Long id) {
        SupportTicket ticket = findTicket(id);
        TicketStatus oldStatus = ticket.getStatus();
        transitionTo(ticket, TicketStatus.CLOSED);
        ticket.setClosedAt(LocalDateTime.now());
        ticket = ticketRepository.save(ticket);
        activityLogService.logStatusChange(ticket.getId(), null, "SYSTEM", oldStatus.name(), "CLOSED");
        return ticket;
    }

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
        emailService.sendTicketNotification(
                userEmail(ticket.getUserId()), saved, EmailNotificationService.TicketEvent.UPDATED);
        return saved;
    }

    public SupportTicket updateAssignedAgent(Long id, String agentName) {
        SupportTicket ticket = findTicket(id);
        String oldAssignee = ticket.getAssignedAgent();
        ticket.setAssignedAgent(agentName);
        ticket = ticketRepository.save(ticket);
        activityLogService.logAssignment(ticket.getId(), null, "ADMIN", oldAssignee, agentName);
        return ticket;
    }

    @PreAuthorize("hasRole('ADMIN')")
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
                predicates.add(cb.equal(root.get("status"), TicketStatus.valueOf(status.trim().toUpperCase(Locale.ROOT))));
            }
            if (priority != null && !priority.isBlank()) {
                predicates.add(cb.equal(root.get("priority"), TicketPriority.valueOf(priority.trim().toUpperCase(Locale.ROOT))));
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
        return ticketRepository.countByStatus(status);
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
