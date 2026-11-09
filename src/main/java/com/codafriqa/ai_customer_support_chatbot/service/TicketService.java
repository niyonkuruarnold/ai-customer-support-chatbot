package com.codafriqa.ai_customer_support_chatbot.service;

import com.codafriqa.ai_customer_support_chatbot.dto.TicketActivityLogDto;
import com.codafriqa.ai_customer_support_chatbot.dto.TicketDto;
import com.codafriqa.ai_customer_support_chatbot.exception.IllegalStateTransitionException;
import com.codafriqa.ai_customer_support_chatbot.exception.ResourceNotFoundException;
import com.codafriqa.ai_customer_support_chatbot.model.SupportTicket;
import com.codafriqa.ai_customer_support_chatbot.model.TicketActivityLog;
import com.codafriqa.ai_customer_support_chatbot.model.TicketPriority;
import com.codafriqa.ai_customer_support_chatbot.model.TicketStatus;
import com.codafriqa.ai_customer_support_chatbot.repository.SupportTicketRepository;
import com.codafriqa.ai_customer_support_chatbot.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;

/**
 * Ticket lifecycle management service with strict state machine enforcement.
 *
 * State Machine (Section 6.5):
 *   NEW -> OPEN
 *   OPEN -> PENDING_CUSTOMER / PENDING_INTERNAL / RESOLVED
 *   PENDING_CUSTOMER / PENDING_INTERNAL -> OPEN / RESOLVED
 *   RESOLVED -> CLOSED / REOPENED
 *   REOPENED -> OPEN
 *
 * Illegal transitions throw {@link IllegalStateTransitionException}.
 * Every status, priority, or assignee change writes an immutable entry
 * to the ticket_activity_logs table.
 */
@Service
public class TicketService {

    private static final Logger log = LoggerFactory.getLogger(TicketService.class);

    private final SupportTicketRepository ticketRepository;
    private final UserRepository userRepository;
    private final ActivityLogService activityLogService;

    public TicketService(SupportTicketRepository ticketRepository,
                         UserRepository userRepository,
                         ActivityLogService activityLogService) {
        this.ticketRepository = ticketRepository;
        this.userRepository = userRepository;
        this.activityLogService = activityLogService;
    }

    /** Allowed transitions per the Section 6.5 spec. */
    private static final Map<TicketStatus, Set<TicketStatus>> TRANSITIONS = Map.of(
        TicketStatus.NEW,              Set.of(TicketStatus.OPEN),
        TicketStatus.OPEN,             Set.of(TicketStatus.PENDING_CUSTOMER,
                                               TicketStatus.PENDING_INTERNAL,
                                               TicketStatus.RESOLVED),
        TicketStatus.PENDING_CUSTOMER, Set.of(TicketStatus.OPEN, TicketStatus.RESOLVED),
        TicketStatus.PENDING_INTERNAL, Set.of(TicketStatus.OPEN, TicketStatus.RESOLVED),
        TicketStatus.RESOLVED,         Set.of(TicketStatus.CLOSED, TicketStatus.REOPENED),
        TicketStatus.REOPENED,         Set.of(TicketStatus.OPEN)
    );

    private void validateTransition(TicketStatus from, TicketStatus to) {
        Set<TicketStatus> allowed = TRANSITIONS.getOrDefault(from, Set.of());
        if (!allowed.contains(to)) {
            throw new IllegalStateTransitionException(from.name(), to.name(), null);
        }
    }

    @Transactional
    public SupportTicket createTicket(Long userId, Long sessionId, String subject,
                                       String description, Long conversationId,
                                       String category, String priority) {
        SupportTicket ticket = new SupportTicket(userId, sessionId, subject, description);
        ticket.setStatus(TicketStatus.NEW);
        if (conversationId != null) ticket.setConversationId(conversationId);
        if (category != null) ticket.setCategory(category);
        if (priority != null) {
            try {
                ticket.setPriority(TicketPriority.valueOf(priority.trim().toUpperCase(Locale.ROOT)));
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException("Invalid priority: " + priority
                    + ". Must be one of: LOW, MEDIUM, HIGH, URGENT");
            }
        }
        SupportTicket saved = ticketRepository.save(ticket);
        activityLogService.logCustom(saved.getId(), userId, "CUSTOMER", "CREATED",
            "Ticket created: " + subject, true);
        log.info("Created ticket {} ({}) for user {}", saved.getId(),
                 saved.getTicketReference(), userId);
        return saved;
    }

    @Transactional
    public SupportTicket updateStatus(Long ticketId, String newStatus,
                                       Long actorId, String actorRole) {
        SupportTicket ticket = findTicket(ticketId);
        TicketStatus targetStatus;
        try {
            targetStatus = TicketStatus.valueOf(newStatus.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid ticket status: " + newStatus);
        }
        TicketStatus currentStatus = ticket.getStatus();
        validateTransition(currentStatus, targetStatus);
        ticket.setStatus(targetStatus);
        if (targetStatus == TicketStatus.REOPENED) {
            ticket.setReopenedAt(LocalDateTime.now());
            ticket.setCustomerReplyCount(0);
        }
        if (targetStatus == TicketStatus.CLOSED) {
            ticket.setClosedAt(LocalDateTime.now());
        }
        SupportTicket saved = ticketRepository.save(ticket);
        activityLogService.logStatusChange(saved.getId(), actorId, actorRole,
            currentStatus.name(), targetStatus.name());
        log.info("Ticket {} status: {} -> {} (actor: {})", ticketId,
                 currentStatus, targetStatus, actorRole);
        return saved;
    }

    @Transactional
    public SupportTicket updatePriority(Long ticketId, String newPriority,
                                         Long actorId, String actorRole) {
        SupportTicket ticket = findTicket(ticketId);
        TicketPriority targetPriority;
        try {
            targetPriority = TicketPriority.valueOf(newPriority.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid priority: " + newPriority);
        }
        TicketPriority oldPriority = ticket.getPriority();
        ticket.setPriority(targetPriority);
        SupportTicket saved = ticketRepository.save(ticket);
        activityLogService.logPriorityChange(saved.getId(), actorId, actorRole,
            oldPriority.name(), targetPriority.name());
        log.info("Ticket {} priority: {} -> {} (actor: {})", ticketId,
                 oldPriority, targetPriority, actorRole);
        return saved;
    }

    @Transactional
    public SupportTicket assignTicket(Long ticketId, String agentName,
                                       Long agentId, Long actorId, String actorRole) {
        SupportTicket ticket = findTicket(ticketId);
        String oldAssignee = ticket.getAssignedAgent();
        ticket.setAssignedAgent(agentName);
        ticket.setAssignedAgentId(agentId);
        SupportTicket saved = ticketRepository.save(ticket);
        activityLogService.logAssignment(saved.getId(), actorId, actorRole,
            oldAssignee, agentName);
        log.info("Ticket {} assigned: {} -> {} (actor: {})", ticketId,
                 oldAssignee, agentName, actorRole);
        return saved;
    }

    public List<TicketActivityLogDto> getActivityLogs(Long ticketId) {
        findTicket(ticketId);
        List<TicketActivityLog> logs = activityLogService.getTicketActivityLogs(ticketId);
        return logs.stream().map(this::toActivityLogDto).toList();
    }

    public List<TicketActivityLogDto> getCustomerVisibleActivityLogs(Long ticketId) {
        findTicket(ticketId);
        List<TicketActivityLog> logs = activityLogService.getCustomerVisibleLogs(ticketId);
        return logs.stream().map(this::toActivityLogDto).toList();
    }

    public TicketDto toDto(SupportTicket ticket) {
        return new TicketDto(
            ticket.getId(), ticket.getTicketReference(), ticket.getSessionId(),
            ticket.getUserId(), userEmail(ticket.getUserId()),
            ticket.getSubject(), ticket.getDescription(), ticket.getStatus().name(),
            ticket.getPriority().name(), ticket.getCategory(), ticket.getAssignedAgent(),
            ticket.getSentiment(), ticket.getCreatedAt(), ticket.getUpdatedAt());
    }

    private TicketActivityLogDto toActivityLogDto(TicketActivityLog log) {
        return new TicketActivityLogDto(
            log.getId(), log.getTicketId(), log.getActorId(), log.getActorRole(),
            log.getActionType(), log.getOldValue(), log.getNewValue(),
            log.getNote(), log.getTimestamp());
    }

    private SupportTicket findTicket(Long id) {
        return ticketRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Ticket not found: " + id));
    }

    private String userEmail(Long userId) {
        if (userId == null) return null;
        return userRepository.findById(userId).map(u -> u.getEmail()).orElse(null);
    }
}
