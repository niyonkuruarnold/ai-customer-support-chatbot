package com.codafriqa.ai_customer_support_chatbot.service;

import com.codafriqa.ai_customer_support_chatbot.controller.WebSocketChatController;
import com.codafriqa.ai_customer_support_chatbot.model.ChatMessage;
import com.codafriqa.ai_customer_support_chatbot.model.ChatSession;
import com.codafriqa.ai_customer_support_chatbot.model.SupportTicket;
import com.codafriqa.ai_customer_support_chatbot.model.TicketPriority;
import com.codafriqa.ai_customer_support_chatbot.repository.ChatSessionRepository;
import com.codafriqa.ai_customer_support_chatbot.repository.SupportTicketRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.prompt.SystemPromptTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Human handoff logic: detects when a customer requests a human agent,
 * escalates the session/ticket, and uses Spring AI to summarize the
 * transcript into bullet points with a sentiment label.
 */
@Service
public class EscalationService {

    private static final Logger log = LoggerFactory.getLogger(EscalationService.class);

    private static final List<String> CLOSED_STATUSES = List.of("RESOLVED", "CLOSED");

    private static final List<String> ESCALATION_PHRASES = List.of(
            "talk to a human", "talk to human", "speak to a human", "speak to human",
            "talk to an agent", "talk to agent", "speak to an agent", "speak to agent",
            "talk to a representative", "talk to representative",
            "talk to someone", "speak to someone", "talk to a person", "speak to a person",
            "connect me to an agent", "connect me to a human", "connect me to a person",
            "transfer me to an agent", "transfer me to a human",
            "connect me to support", "transfer me to support",
            "i want a human", "need a human", "i need a human",
            "i want to talk to", "i want to speak to", "i need to talk to",
            "i need to speak to",
            "escalate this ticket", "escalate my ticket",
            "please escalate", "please escalate this", "i want to escalate",
            "i need to escalate"
    );

    private final ChatModel chatModel;
    private final ChatSessionRepository sessionRepository;
    private final SupportTicketRepository ticketRepository;
    private final SupportTicketService supportTicketService;
    private final WebSocketChatController webSocketController;

    public EscalationService(ChatModel chatModel,
                             ChatSessionRepository sessionRepository,
                             SupportTicketRepository ticketRepository,
                             SupportTicketService supportTicketService,
                             WebSocketChatController webSocketController) {
        this.chatModel = chatModel;
        this.sessionRepository = sessionRepository;
        this.ticketRepository = ticketRepository;
        this.supportTicketService = supportTicketService;
        this.webSocketController = webSocketController;
    }

    public boolean isEscalationRequest(String message) {
        if (message == null) return false;
        String normalized = message.toLowerCase(Locale.ROOT);
        return ESCALATION_PHRASES.stream().anyMatch(normalized::contains);
    }

    public SupportTicket escalate(ChatSession session, String triggerMessage, List<ChatMessage> transcript) {
        session.setStatus("ESCALATED");
        sessionRepository.save(session);
        SummaryResult summary = generateSummary(transcript);
        SupportTicket ticket = ticketRepository
                .findFirstBySessionIdOrderByUpdatedAtDesc(session.getId())
                .filter(t -> !CLOSED_STATUSES.contains(t.getStatus().name()))
                .orElseGet(() -> supportTicketService.open(
                        session.getUserId(), session.getId(),
                        subjectFor(transcript, triggerMessage), triggerMessage));
        ticket.setStatus(com.codafriqa.ai_customer_support_chatbot.model.TicketStatus.OPEN);
        ticket.setPriority(priorityForSentiment(summary.sentiment()));
        ticket.setAiSummary(summary.summary());
        ticket.setSentiment(summary.sentiment());
        SupportTicket savedTicket = ticketRepository.save(ticket);
        try {
            webSocketController.broadcastSummary(session.getId(), summary.summary(), summary.sentiment());
        } catch (Exception e) {
            log.debug("WebSocket broadcast failed: {}", e.getMessage());
        }
        return savedTicket;
    }

    public SummaryResult generateSummary(List<ChatMessage> transcript) {
        String transcriptText = transcript.stream()
                .map(m -> "[" + m.getSender() + "] " + m.getContent())
                .reduce((a, b) -> a + "\n" + b)
                .orElse("");
        String instruction = "You are a customer support handoff summarizer. "
                + "Given the transcript, write 2-3 concise bullet points. "
                + "Then add: SENTIMENT: positive|neutral|negative. "
                + "Format: BULLETS:\\n- point\\nSENTIMENT: neutral";
        try {
            Message system = new SystemPromptTemplate(instruction).createMessage();
            Message user = new UserMessage("Transcript:\n" + transcriptText);
            String output = chatModel.call(new Prompt(List.of(system, user)))
                    .getResult().getOutput().getText();
            return parseSummary(output);
        } catch (Exception e) {
            log.warn("AI summary generation failed; using fallback", e);
            return new SummaryResult("Customer requested human assistance.", "neutral");
        }
    }

    private SummaryResult parseSummary(String output) {
        if (output == null || output.isBlank()) {
            return new SummaryResult("Customer requested human assistance.", "neutral");
        }
        List<String> bullets = new ArrayList<>();
        for (String line : output.split("\\R")) {
            String trimmed = line.trim();
            if (trimmed.startsWith("-") || trimmed.startsWith("*") || trimmed.startsWith("\u2022")) {
                bullets.add(trimmed.replaceFirst("^[-*\u2022]\\s*", ""));
            }
        }
        if (bullets.size() > 3) bullets = bullets.subList(0, 3);
        String sentiment = "neutral";
        Matcher m = Pattern.compile("(?i)sentiment[:\\s]+(positive|neutral|negative)").matcher(output);
        if (m.find()) sentiment = m.group(1).toLowerCase(Locale.ROOT);
        String summary = bullets.isEmpty()
                ? output.replaceAll("\\s+", " ").trim()
                : String.join("\n", bullets);
        return new SummaryResult(summary, sentiment);
    }

    private String subjectFor(List<ChatMessage> transcript, String triggerMessage) {
        return transcript.stream()
                .filter(m -> "USER".equals(m.getSender()))
                .findFirst()
                .map(m -> truncate(m.getContent(), 80))
                .orElseGet(() -> truncate(triggerMessage, 80));
    }

    private static String truncate(String text, int max) {
        if (text == null) return "";
        return text.length() <= max ? text : text.substring(0, max).trim() + "...";
    }

    private static TicketPriority priorityForSentiment(String sentiment) {
        return switch (sentiment == null ? "" : sentiment.toLowerCase(Locale.ROOT)) {
            case "negative" -> TicketPriority.HIGH;
            case "positive" -> TicketPriority.LOW;
            default -> TicketPriority.MEDIUM;
        };
    }

    public record SummaryResult(String summary, String sentiment) {}
}
