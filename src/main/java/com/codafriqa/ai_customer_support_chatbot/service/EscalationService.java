package com.codafriqa.ai_customer_support_chatbot.service;

import com.codafriqa.ai_customer_support_chatbot.model.ChatMessage;
import com.codafriqa.ai_customer_support_chatbot.model.ChatSession;
import com.codafriqa.ai_customer_support_chatbot.model.SupportTicket;
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

    /** Tickets in these states are considered closed and won't be re-opened. */
    private static final List<String> CLOSED_STATUSES = List.of("RESOLVED", "CLOSED");

    /**
     * Phrase-based escalation triggers (matched case-insensitively).
     *
     * <p>Only phrases that express a clear intent to <em>request</em> or
     * <em>connect with</em> a live person are included.  Generic references
     * to human agents (e.g. "human agent", "customer support agent",
     * "support representative") are intentionally excluded so that
     * informational questions like "What are the human agent support hours?"
     * are answered from the knowledge base instead of triggering handoff.
     */
    private static final List<String> ESCALATION_PHRASES = List.of(
            // Explicit requests to talk / speak
            "talk to a human", "talk to human", "speak to a human", "speak to human",
            "talk to an agent", "talk to agent", "speak to an agent", "speak to agent",
            "talk to a representative", "talk to representative",
            "talk to someone", "speak to someone", "talk to a person",
            "speak to a person",
            // Connect / transfer requests
            "connect me to an agent", "connect me to a human", "connect me to a person",
            "transfer me to an agent", "transfer me to a human",
            "connect me to support", "transfer me to support",
            // Direct intent declarations
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

    public EscalationService(ChatModel chatModel,
                             ChatSessionRepository sessionRepository,
                             SupportTicketRepository ticketRepository,
                             SupportTicketService supportTicketService) {
        this.chatModel = chatModel;
        this.sessionRepository = sessionRepository;
        this.ticketRepository = ticketRepository;
        this.supportTicketService = supportTicketService;
    }

    /** Whether the customer's message requests a human agent. */
    public boolean isEscalationRequest(String message) {
        if (message == null) {
            return false;
        }
        String normalized = message.toLowerCase(Locale.ROOT);
        return ESCALATION_PHRASES.stream().anyMatch(normalized::contains);
    }

    /**
     * Escalate a session: mark it ESCALATED, (re)create the support ticket,
     * and generate the AI handoff summary + sentiment from the transcript.
     *
     * @return the (updated) support ticket
     */
    public SupportTicket escalate(ChatSession session, String triggerMessage, List<ChatMessage> transcript) {
        session.setStatus("ESCALATED");
        sessionRepository.save(session);

        SummaryResult summary = generateSummary(transcript);

        // New tickets go through SupportTicketService.open() so the lifecycle
        // state machine owns creation and fires the "opened" customer email.
        SupportTicket ticket = ticketRepository
                .findFirstBySessionIdOrderByUpdatedAtDesc(session.getId())
                .filter(t -> !CLOSED_STATUSES.contains(t.getStatus()))
                .orElseGet(() -> supportTicketService.open(
                        session.getUserId(),
                        session.getId(),
                        subjectFor(transcript, triggerMessage),
                        triggerMessage));

        ticket.setStatus("ESCALATED");
        ticket.setPriority(priorityForSentiment(summary.sentiment()));
        ticket.setAiSummary(summary.summary());
        ticket.setSentiment(summary.sentiment());
        return ticketRepository.save(ticket);
    }

    /** Generate a 2-3 bullet handoff summary plus customer sentiment via Spring AI. */
    public SummaryResult generateSummary(List<ChatMessage> transcript) {
        String transcriptText = transcript.stream()
                .map(m -> "[" + m.getSender() + "] " + m.getContent())
                .reduce((a, b) -> a + "\n" + b)
                .orElse("");

        String instruction = """
                You are a customer support handoff summarizer for a support team.
                Given the customer support transcript, write exactly 2-3 concise bullet points covering:
                - what the customer needs or the core issue
                - key facts or details the agent should know
                - anything promised or pending
                Then add a single final line judging the customer's tone: SENTIMENT: positive|neutral|negative

                Format exactly:
                BULLETS:
                - point one
                - point two
                SENTIMENT: neutral
                """;

        try {
            Message system = new SystemPromptTemplate(instruction).createMessage();
            Message user = new UserMessage("Transcript:\n" + transcriptText);
            String output = chatModel.call(new Prompt(List.of(system, user)))
                    .getResult().getOutput().getText();
            return parseSummary(output);
        } catch (Exception e) {
            log.warn("AI summary generation failed (is GEMINI_API_KEY set?); using fallback summary", e);
            return new SummaryResult(
                    "• Customer requested human assistance during the support conversation.\n" +
                    "• The conversation was handed off to a support agent.",
                    "neutral");
        }
    }

    private SummaryResult parseSummary(String output) {
        if (output == null || output.isBlank()) {
            return new SummaryResult("• Customer requested human assistance.", "neutral");
        }

        List<String> bullets = new ArrayList<>();
        for (String line : output.split("\\R")) {
            String trimmed = line.trim();
            if (trimmed.startsWith("-") || trimmed.startsWith("*") || trimmed.startsWith("•")) {
                bullets.add(trimmed.replaceFirst("^[-*•]\\s*", ""));
            }
        }
        if (bullets.size() > 3) {
            bullets = bullets.subList(0, 3);
        }

        String sentiment = "neutral";
        Matcher m = Pattern.compile("(?i)sentiment[:\\s]+(positive|neutral|negative)").matcher(output);
        if (m.find()) {
            sentiment = m.group(1).toLowerCase(Locale.ROOT);
        }

        String summary;
        if (bullets.isEmpty()) {
            summary = "• " + output.replaceAll("\\s+", " ").trim();
        } else {
            summary = bullets.stream()
                    .map(b -> "• " + b)
                    .reduce((a, b) -> a + "\n" + b)
                    .orElse("");
        }
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
        if (text == null) {
            return "";
        }
        return text.length() <= max ? text : text.substring(0, max).trim() + "…";
    }

    private static String priorityForSentiment(String sentiment) {
        return switch (sentiment == null ? "" : sentiment.toLowerCase(Locale.ROOT)) {
            case "negative" -> "HIGH";
            case "positive" -> "LOW";
            default -> "MEDIUM";
        };
    }

    /** Result of AI handoff summarization. */
    public record SummaryResult(String summary, String sentiment) {}
}
