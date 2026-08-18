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
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ChatService {

    private static final Logger log = LoggerFactory.getLogger(ChatService.class);

    /** Deterministic acknowledgement returned when the customer escalates. */
    private static final String HANDOFF_ACK =
            "You've been connected to a human support agent. 🎧 They can see our conversation history " +
            "and will join this chat shortly. Is there anything else you'd like to mention while you wait?";

    /**
     * Returned while a human agent is active (session ESCALATED): automated
     * AI responses are paused so the agent owns the conversation.
     */
    private static final String AGENT_ACTIVE_ACK =
            "Your message has been sent to the agent — they'll reply right here shortly.";

    /** Base system instruction; the retrieved knowledge base context is appended when available. */
    private static final String BASE_SYSTEM_INSTRUCTION = """
            You are a helpful, polite, and efficient AI Customer Support Agent for Code of Africa. 
            Your primary goal is to answer user inquiries accurately, clearly, and concisely.
            Always maintain a professional, empathetic tone.
            If you do not know the answer to a specific question, politely let the user know and offer to connect them with a human support representative. 
            Do not make up information or make promises regarding pricing or policies unless explicitly stated in your context.
            """;

    private static final String KNOWLEDGE_SECTION = """

            Relevant knowledge base context (use it to answer accurately when it applies; do not invent facts beyond it):

            """;

    private final ChatClient chatClient;
    private final ChatSessionRepository sessionRepository;
    private final ChatMessageRepository messageRepository;
    private final EscalationService escalationService;
    private final RagService ragService;
    private final UserService userService;

    public ChatService(ChatClient.Builder chatClientBuilder,
                       ChatSessionRepository sessionRepository,
                       ChatMessageRepository messageRepository,
                       EscalationService escalationService,
                       RagService ragService,
                       UserService userService) {
        this.chatClient = chatClientBuilder.build();
        this.sessionRepository = sessionRepository;
        this.messageRepository = messageRepository;
        this.escalationService = escalationService;
        this.ragService = ragService;
        this.userService = userService;
    }

    /**
     * Persist the user message, generate a response, and detect escalation.
     *
     * Handoff behavior: when the customer requests a human agent the session
     * and ticket are marked ESCALATED and an AI handoff summary is generated
     * from the transcript. While the session is ESCALATED (a human agent is
     * active), automated AI responses are PAUSED — the message is persisted
     * and a short "sent to the agent" acknowledgement is returned instead, so
     * the agent owns the conversation.
     *
     * @param userMessage the customer's message
     * @param sessionId   existing session id, or null to create a new session
     */
    public ChatResponseDto sendMessage(String userMessage, Long sessionId) {
        ChatSession session = resolveSession(sessionId);
        messageRepository.save(new ChatMessage(session.getId(), "USER", userMessage));

        boolean escalationTrigger = escalationService.isEscalationRequest(userMessage);
        GenerationResult generation;
        if ("ESCALATED".equals(session.getStatus())) {
            // Human agent active — no AI generation
            generation = new GenerationResult(AGENT_ACTIVE_ACK, false, List.of());
        } else if (escalationTrigger) {
            generation = new GenerationResult(HANDOFF_ACK, false, List.of());
        } else {
            generation = generateResponse(userMessage);
        }
        messageRepository.save(new ChatMessage(session.getId(), "AI", generation.text()));

        if (escalationTrigger) {
            List<ChatMessage> transcript = messageRepository.findBySessionIdOrderByTimestampAsc(session.getId());
            escalationService.escalate(session, userMessage, transcript);
        }

        ChatResponseDto response = new ChatResponseDto(generation.text(), session.getId(), session.getStatus());
        response.setRagUsed(generation.ragUsed());
        response.setContextReferences(generation.references());
        return response;
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
        // The chat has no registration, so every session is backed by the
        // anonymous customer account (resolved by email, created if missing)
        // — its email is the contact detail shown in the agent workspace.
        Long userId = userService.ensureAnonymousUser().getId();
        if (sessionId != null) {
            return sessionRepository.findById(sessionId)
                    .orElseGet(() -> sessionRepository.save(new ChatSession(userId)));
        }
        return sessionRepository.save(new ChatSession(userId));
    }

    /**
     * AI response generation with RAG: the top-K relevant knowledge base
     * chunks are retrieved for the user's message (RagService.retrieveContext)
     * and appended to the system prompt. Falls back to a plain (context-free)
     * prompt when retrieval is unavailable, and to a friendly fallback message
     * when the model itself cannot be reached (e.g. missing OPENAI_API_KEY).
     */
    private GenerationResult generateResponse(String userMessage) {
        // Retrieval never throws: RagService.retrieveContext returns an empty
        // context when the vector store is unavailable or has nothing relevant.
        // (Plain concatenation, not String.formatted, so KB text containing '%'
        // cannot raise an UnknownFormatConversionException outside the try below.)
        RagService.RagContext rag = ragService.retrieveContext(userMessage);
        boolean ragUsed = !rag.contextText().isBlank();
        String systemInstruction = ragUsed
                ? BASE_SYSTEM_INSTRUCTION + KNOWLEDGE_SECTION + rag.contextText()
                : BASE_SYSTEM_INSTRUCTION;

        try {
            String answer = chatClient.prompt()
                    .system(systemInstruction)
                    .user(userMessage)
                    .call()
                    .content();
            List<ChatResponseDto.ContextReference> references = rag.references().stream()
                    .map(r -> new ChatResponseDto.ContextReference(
                            r.documentId(), r.title(), r.sourceType()))
                    .toList();
            return new GenerationResult(answer, ragUsed, references);
        } catch (Exception e) {
            // Log the exact failure (class + message + full stack trace via the
            // throwable argument) so the root cause is visible in the app log,
            // e.g. a 401 from a missing or invalid OPENAI_API_KEY.
            log.warn("AI response generation failed ({}: {}); returning fallback message",
                    e.getClass().getSimpleName(), e.getMessage(), e);
            return new GenerationResult(
                    "I'm sorry, I'm having trouble processing your request right now. " +
                    "Please try again shortly, or ask to speak with a human support agent.",
                    false, List.of());
        }
    }

    /** Response text + whether it was grounded in retrieved context (+ source references). */
    private record GenerationResult(String text, boolean ragUsed,
                                    List<ChatResponseDto.ContextReference> references) {
    }

    private ChatMessageDto toMessageDto(ChatMessage message) {
        return new ChatMessageDto(message.getId(), message.getSender(), message.getContent(), message.getTimestamp());
    }
}
