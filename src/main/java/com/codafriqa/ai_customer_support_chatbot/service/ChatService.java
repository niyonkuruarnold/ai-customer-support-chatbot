package com.codafriqa.ai_customer_support_chatbot.service;

import com.codafriqa.ai_customer_support_chatbot.dto.ChatMessageDto;
import com.codafriqa.ai_customer_support_chatbot.dto.ChatResponseDto;
import com.codafriqa.ai_customer_support_chatbot.dto.SessionInfoDto;
import com.codafriqa.ai_customer_support_chatbot.exception.ResourceNotFoundException;
import com.codafriqa.ai_customer_support_chatbot.model.ChatMessage;
import com.codafriqa.ai_customer_support_chatbot.model.ChatSession;
import com.codafriqa.ai_customer_support_chatbot.repository.ChatMessageRepository;
import com.codafriqa.ai_customer_support_chatbot.repository.ChatSessionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.prompt.SystemPromptTemplate;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ChatService {

    private static final Logger log = LoggerFactory.getLogger(ChatService.class);

    /** The frontend has no registration yet, so anonymous sessions use this user id. */
    private static final Long ANONYMOUS_USER_ID = 1L;

    /** Deterministic acknowledgement returned when the customer escalates. */
    private static final String HANDOFF_ACK =
            "You've been connected to a human support agent. 🎧 They can see our conversation history " +
            "and will join this chat shortly. Is there anything else you'd like to mention while you wait?";

    private final ChatModel chatModel;
    private final ChatSessionRepository sessionRepository;
    private final ChatMessageRepository messageRepository;
    private final EscalationService escalationService;

    public ChatService(ChatModel chatModel,
                       ChatSessionRepository sessionRepository,
                       ChatMessageRepository messageRepository,
                       EscalationService escalationService) {
        this.chatModel = chatModel;
        this.sessionRepository = sessionRepository;
        this.messageRepository = messageRepository;
        this.escalationService = escalationService;
    }

    /**
     * Persist the user message, generate a response, and detect escalation.
     * On escalation the session and ticket are marked ESCALATED and an AI
     * handoff summary is generated from the transcript.
     *
     * @param userMessage the customer's message
     * @param sessionId   existing session id, or null to create a new session
     */
    public ChatResponseDto sendMessage(String userMessage, Long sessionId) {
        ChatSession session = resolveSession(sessionId);
        messageRepository.save(new ChatMessage(session.getId(), "USER", userMessage));

        boolean escalated = escalationService.isEscalationRequest(userMessage);
        String responseText = escalated ? HANDOFF_ACK : generateResponse(userMessage);
        messageRepository.save(new ChatMessage(session.getId(), "AI", responseText));

        if (escalated) {
            List<ChatMessage> transcript = messageRepository.findBySessionIdOrderByTimestampAsc(session.getId());
            escalationService.escalate(session, userMessage, transcript);
        }

        return new ChatResponseDto(responseText, session.getId(), session.getStatus());
    }

    /** Full session state (status + transcript) for the customer-facing frontend. */
    public SessionInfoDto getSessionInfo(Long sessionId) {
        ChatSession session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new ResourceNotFoundException("Chat session not found: " + sessionId));
        List<ChatMessageDto> messages = messageRepository.findBySessionIdOrderByTimestampAsc(sessionId)
                .stream()
                .map(this::toMessageDto)
                .toList();
        return new SessionInfoDto(session.getId(), session.getStatus(), messages);
    }

    private ChatSession resolveSession(Long sessionId) {
        if (sessionId != null) {
            return sessionRepository.findById(sessionId)
                    .orElseGet(() -> sessionRepository.save(new ChatSession(ANONYMOUS_USER_ID)));
        }
        return sessionRepository.save(new ChatSession(ANONYMOUS_USER_ID));
    }

    /**
     * AI response generation with a graceful fallback when the model is
     * unavailable (e.g. missing OPENAI_API_KEY) so the chat never 500s.
     */
    private String generateResponse(String userMessage) {
        String systemInstruction = """
            You are a helpful, polite, and efficient AI Customer Support Agent for Code of Africa. 
            Your primary goal is to answer user inquiries accurately, clearly, and concisely.
            Always maintain a professional, empathetic tone.
            If you do not know the answer to a specific question, politely let the user know and offer to connect them with a human support representative. 
            Do not make up information or make promises regarding pricing or policies unless explicitly stated in your context.
            """;

        try {
            Message systemMessage = new SystemPromptTemplate(systemInstruction).createMessage();
            Message userMsg = new UserMessage(userMessage);
            Prompt prompt = new Prompt(List.of(systemMessage, userMsg));
            return chatModel.call(prompt).getResult().getOutput().getText();
        } catch (Exception e) {
            log.warn("AI response generation failed (is OPENAI_API_KEY set?); returning fallback message", e);
            return "I'm sorry, I'm having trouble processing your request right now. " +
                   "Please try again shortly, or ask to speak with a human support agent.";
        }
    }

    private ChatMessageDto toMessageDto(ChatMessage message) {
        return new ChatMessageDto(message.getId(), message.getSender(), message.getContent(), message.getTimestamp());
    }
}
