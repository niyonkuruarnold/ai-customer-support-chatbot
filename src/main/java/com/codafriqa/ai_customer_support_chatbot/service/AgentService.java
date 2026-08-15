package com.codafriqa.ai_customer_support_chatbot.service;

import com.codafriqa.ai_customer_support_chatbot.dto.AgentTicketDetailDto;
import com.codafriqa.ai_customer_support_chatbot.dto.AgentTicketDto;
import com.codafriqa.ai_customer_support_chatbot.dto.ChatMessageDto;
import com.codafriqa.ai_customer_support_chatbot.exception.ResourceNotFoundException;
import com.codafriqa.ai_customer_support_chatbot.model.ChatMessage;
import com.codafriqa.ai_customer_support_chatbot.model.SupportTicket;
import com.codafriqa.ai_customer_support_chatbot.repository.ChatMessageRepository;
import com.codafriqa.ai_customer_support_chatbot.repository.SupportTicketRepository;
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
    private static final List<String> ACTIVE_STATUSES = List.of("OPEN", "ESCALATED", "IN_PROGRESS");

    private final SupportTicketRepository ticketRepository;
    private final ChatMessageRepository messageRepository;

    public AgentService(SupportTicketRepository ticketRepository,
                        ChatMessageRepository messageRepository) {
        this.ticketRepository = ticketRepository;
        this.messageRepository = messageRepository;
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

    /** Assign the ticket to an agent and mark it in progress. */
    public AgentTicketDetailDto takeOver(Long id, String agentName) {
        SupportTicket ticket = findTicket(id);
        ticket.setAssignedAgent(agentName);
        ticket.setStatus("IN_PROGRESS");
        ticketRepository.save(ticket);
        return getTicket(id);
    }

    /** Send an agent reply to the customer (persisted in the chat transcript). */
    public AgentTicketDetailDto reply(Long id, String agentName, String message) {
        SupportTicket ticket = findTicket(id);
        messageRepository.save(new ChatMessage(ticket.getSessionId(), "AGENT", message));
        ticket.setUpdatedAt(LocalDateTime.now());
        ticketRepository.save(ticket);
        return getTicket(id);
    }

    /** Add an internal note visible only to agents. */
    public AgentTicketDetailDto addNote(Long id, String content) {
        SupportTicket ticket = findTicket(id);
        ticket.getInternalNotes().add(content);
        ticketRepository.save(ticket);
        return getTicket(id);
    }

    /** Mark the ticket resolved (completes the handoff workflow). */
    public AgentTicketDetailDto resolve(Long id, String agentName) {
        SupportTicket ticket = findTicket(id);
        ticket.setStatus("RESOLVED");
        if (ticket.getAssignedAgent() == null) {
            ticket.setAssignedAgent(agentName);
        }
        ticketRepository.save(ticket);
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
                t.getId(), t.getSessionId(), t.getUserId(), t.getSubject(), t.getDescription(),
                t.getStatus(), t.getPriority(), t.getAssignedAgent(), t.getAiSummary(), t.getSentiment(),
                lastMessage, t.getCreatedAt(), t.getUpdatedAt());
    }

    private AgentTicketDetailDto toDetailDto(SupportTicket t, List<ChatMessageDto> messages) {
        return new AgentTicketDetailDto(
                t.getId(), t.getSessionId(), t.getUserId(), t.getSubject(), t.getDescription(),
                t.getStatus(), t.getPriority(), t.getAssignedAgent(), t.getAiSummary(), t.getSentiment(),
                t.getCreatedAt(), t.getUpdatedAt(), messages, t.getInternalNotes());
    }
}
