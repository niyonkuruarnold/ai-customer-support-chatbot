package com.codafriqa.ai_customer_support_chatbot.service;

import com.codafriqa.ai_customer_support_chatbot.dto.AgentTicketDetailDto;
import com.codafriqa.ai_customer_support_chatbot.dto.AgentTicketDto;
import com.codafriqa.ai_customer_support_chatbot.dto.ChatMessageDto;
import com.codafriqa.ai_customer_support_chatbot.exception.ResourceNotFoundException;
import com.codafriqa.ai_customer_support_chatbot.model.ChatMessage;
import com.codafriqa.ai_customer_support_chatbot.model.SupportTicket;
import com.codafriqa.ai_customer_support_chatbot.model.TicketStatus;
import com.codafriqa.ai_customer_support_chatbot.model.User;
import com.codafriqa.ai_customer_support_chatbot.repository.ChatMessageRepository;
import com.codafriqa.ai_customer_support_chatbot.repository.SupportTicketRepository;
import com.codafriqa.ai_customer_support_chatbot.repository.UserRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Support agent operations for the handoff workflow: ticket queue, takeover,
 * replies (persisted into the chat transcript), internal notes, and resolution.
 */
@Service
public class AgentService {

    /** Tickets shown in the agent workspace queue. */
        private static final List<TicketStatus> ACTIVE_STATUSES = List.of(
            TicketStatus.OPEN,
            TicketStatus.PENDING_CUSTOMER,
            TicketStatus.PENDING_INTERNAL);

    private final SupportTicketRepository ticketRepository;
    private final ChatMessageRepository messageRepository;
    private final UserRepository userRepository;
    private final SupportTicketService supportTicketService;
    private final TicketUpdateGuard ticketUpdateGuard;

    public AgentService(SupportTicketRepository ticketRepository,
                        ChatMessageRepository messageRepository,
                        UserRepository userRepository,
                        SupportTicketService supportTicketService,
                        TicketUpdateGuard ticketUpdateGuard) {
        this.ticketRepository = ticketRepository;
        this.messageRepository = messageRepository;
        this.userRepository = userRepository;
        this.supportTicketService = supportTicketService;
        this.ticketUpdateGuard = ticketUpdateGuard;
    }

    public List<AgentTicketDto> listTickets() {
        return ticketRepository.findByStatusInOrderByUpdatedAtDesc(ACTIVE_STATUSES).stream()
                .map(t -> toListDto(t, lastMessagePreview(t.getSessionId())))
                .toList();
    }

    public AgentTicketDetailDto getTicket(Long id) {
        SupportTicket ticket = findTicket(id);
        return toDetailDto(ticket, messages(ticket.getSessionId()));
    }

    /** Assign the ticket to an agent and mark it in progress (state machine). */
    public AgentTicketDetailDto takeOver(Long id, String agentName) {
        // Retried because the chat pipeline may concurrently escalate/update
        // the same ticket; each attempt re-enters the transactional service.
        ticketUpdateGuard.retry(() -> supportTicketService.takeOver(id, agentName));
        return getTicket(id);
    }

    /** Send an agent reply to the customer (persisted in the chat transcript). */
    public AgentTicketDetailDto reply(Long id, String agentName, String message) {
        SupportTicket ticket = findTicket(id);
        messageRepository.save(new ChatMessage(ticket.getSessionId(), "AGENT", message));
        // Reload + touch the latest row state in one transaction (retried on conflict).
        ticketUpdateGuard.mutate(id, latest -> latest.setUpdatedAt(LocalDateTime.now()));
        return getTicket(id);
    }

    /** Add an internal note visible only to agents. */
    public AgentTicketDetailDto addNote(Long id, String content) {
        findTicket(id);
        // The notes element collection is reloaded and mutated inside a single
        // transaction per attempt — concurrent chat updates to the same ticket
        // no longer flush against a stale collection snapshot.
        ticketUpdateGuard.mutate(id, latest -> latest.getInternalNotes().add(content));
        return getTicket(id);
    }

    /** Mark the ticket resolved (state machine + customer email). */
    public AgentTicketDetailDto resolve(Long id, String agentName) {
        ticketUpdateGuard.retry(() -> supportTicketService.resolve(id, agentName));
        return getTicket(id);
    }

    private SupportTicket findTicket(Long id) {
        return ticketRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Support ticket not found: " + id));
    }

    private List<ChatMessageDto> messages(Long sessionId) {
        return messageRepository.findBySessionIdOrderByTimestampAsc(sessionId).stream()
                .map(m -> new ChatMessageDto(m.getId(), m.getSender(), m.getContent(), m.getTimestamp()))
                .toList();
    }

    private String lastMessagePreview(Long sessionId) {
        List<ChatMessage> messages = messageRepository.findBySessionIdOrderByTimestampAsc(sessionId);
        if (messages.isEmpty()) {
            return null;
        }
        String content = messages.get(messages.size() - 1).getContent();
        return content.length() <= 120 ? content : content.substring(0, 120).trim() + "…";
    }

    private AgentTicketDto toListDto(SupportTicket t, String lastMessage) {
        return new AgentTicketDto(
                t.getId(), t.getSessionId(), t.getUserId(), userEmail(t.getUserId()), t.getSubject(),
                t.getDescription(), t.getStatus().name(), t.getPriority().name(), t.getAssignedAgent(),
                t.getAiSummary(), t.getSentiment(), lastMessage, t.getCreatedAt(), t.getUpdatedAt());
    }

    private AgentTicketDetailDto toDetailDto(SupportTicket t, List<ChatMessageDto> messages) {
        return new AgentTicketDetailDto(
                t.getId(), t.getSessionId(), t.getUserId(), userEmail(t.getUserId()), t.getSubject(),
                t.getDescription(), t.getStatus().name(), t.getPriority().name(), t.getAssignedAgent(),
                t.getAiSummary(), t.getSentiment(), t.getCreatedAt(), t.getUpdatedAt(),
                messages, t.getInternalNotes());
    }

    /** Customer contact (email) for a ticket, resolved once per DTO. */
    private String userEmail(Long userId) {
        if (userId == null) {
            return null;
        }
        return userRepository.findById(userId)
                .map(User::getEmail)
                .orElse(null);
    }
}
