package com.codafriqa.ai_customer_support_chatbot.service;

import com.codafriqa.ai_customer_support_chatbot.exception.ResourceNotFoundException;
import com.codafriqa.ai_customer_support_chatbot.model.ChatMessage;
import com.codafriqa.ai_customer_support_chatbot.model.Conversation;
import com.codafriqa.ai_customer_support_chatbot.repository.ChatMessageRepository;
import com.codafriqa.ai_customer_support_chatbot.repository.ConversationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Gemini AI-powered conversation summarizer.
 *
 * When a conversation is escalated to a human agent, this service fetches
 * the prior message history, formats a prompt, and calls the Gemini API
 * (via Spring AI ChatModel) to produce a 2-3 sentence structured summary
 * of the customer's issue.  The summary is persisted to
 * {@code conversations.escalation_summary}.
 */
@Service
public class GeminiSummaryService {

    private static final Logger log = LoggerFactory.getLogger(GeminiSummaryService.class);

    private final ChatModel chatModel;
    private final ConversationRepository conversationRepository;
    private final ChatMessageRepository messageRepository;

    @Value("${spring.ai.google.genai.model:gemini-1.5-flash}")
    private String modelName;

    public GeminiSummaryService(ChatModel chatModel,
                                ConversationRepository conversationRepository,
                                ChatMessageRepository messageRepository) {
        this.chatModel = chatModel;
        this.conversationRepository = conversationRepository;
        this.messageRepository = messageRepository;
    }

    /**
     * Generate an AI summary for a conversation and persist it.
     *
     * @param sessionId the chat session ID to summarize
     * @return the generated summary text
     * @throws ResourceNotFoundException if no conversation exists for the session
     */
    @Transactional
    public String summarizeOnEscalation(Long sessionId) {
        Conversation conversation = conversationRepository.findBySessionId(sessionId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Conversation not found for session: " + sessionId));

        List<ChatMessage> transcript = messageRepository.findBySessionIdOrderByTimestampAsc(sessionId);
        String summary = generateSummary(transcript);

        conversation.setEscalationSummary(summary);
        conversationRepository.save(conversation);

        log.info("Escalation summary saved for session {}: {}", sessionId, summary);
        return summary;
    }

    /**
     * Generate a 2-3 sentence structured summary from the conversation transcript.
     *
     * @param transcript ordered list of chat messages
     * @return the AI-generated summary
     */
    public String generateSummary(List<ChatMessage> transcript) {
        String transcriptText = transcript.stream()
                .map(m -> "[" + m.getSender() + "] " + m.getContent())
                .reduce((a, b) -> a + "\n" + b)
                .orElse("");

        if (transcriptText.isBlank()) {
            return "No conversation history available to summarize.";
        }

        String systemPrompt = """
                You are a customer support escalation summarizer.
                Given the conversation transcript below, write exactly 2-3 concise
                sentences that capture:
                1. The customer's core issue or request
                2. Key facts or details the human agent should know immediately
                3. Any actions already taken or promised

                Be factual and specific. Do not invent details not in the transcript.
                """;

        try {
            Message system = new SystemMessage(systemPrompt);
            Message user = new UserMessage("Conversation transcript:\n\n" + transcriptText);
            String output = chatModel.call(new Prompt(List.of(system, user)))
                    .getResult().getOutput().getText();

            if (output == null || output.isBlank()) {
                log.warn("Gemini returned empty summary for transcript of {} messages", transcript.size());
                return fallbackSummary(transcript);
            }

            return output.trim();
        } catch (Exception e) {
            log.warn("Gemini summary generation failed ({}: {}); using fallback",
                    e.getClass().getSimpleName(), e.getMessage());
            return fallbackSummary(transcript);
        }
    }

    private String fallbackSummary(List<ChatMessage> transcript) {
        long userMessages = transcript.stream()
                .filter(m -> "USER".equals(m.getSender()))
                .count();
        return "Customer engaged in a support conversation with " + userMessages
                + " message(s). The conversation was escalated to a human agent for further assistance.";
    }
}
