package com.codafriqa.ai_customer_support_chatbot.service;

import com.codafriqa.ai_customer_support_chatbot.controller.WebSocketChatController;
import com.codafriqa.ai_customer_support_chatbot.dto.ChatMessageDto;
import com.codafriqa.ai_customer_support_chatbot.dto.ChatResponseDto;
import com.codafriqa.ai_customer_support_chatbot.dto.SourceCitationDto;
import com.codafriqa.ai_customer_support_chatbot.dto.SessionInfoDto;
import com.codafriqa.ai_customer_support_chatbot.exception.ResourceNotFoundException;
import com.codafriqa.ai_customer_support_chatbot.model.ChatMessage;
import com.codafriqa.ai_customer_support_chatbot.model.ChatSession;
import com.codafriqa.ai_customer_support_chatbot.repository.ChatMessageRepository;
import com.codafriqa.ai_customer_support_chatbot.repository.ChatSessionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Value;
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

        /** Base system instruction for the CODAFRIQA AI Assistant. */
        private static final String BASE_SYSTEM_INSTRUCTION = """
            You are the official CODAFRIQA Support Assistant. Answer the user's inquiry accurately and professionally using the provided knowledge base context as your primary source of truth.

            Instructions:
            - Prioritize information found in the Context.
            - If the Context answers the question, answer directly and concisely.
            - If the Context does NOT contain the answer, answer politely based on general assistance standards and explicitly mention: "Note: This is based on general guidance as I couldn't locate specific details in our local documentation."
            - Do NOT invent or assume any details outside the Context.
            - Only suggest connecting to a human agent when the customer explicitly requests live support (e.g., "I want to talk to a human", "Connect me to an agent").
            """;

    /**
     * Resolves the {@code {context}} placeholder in {@link #BASE_SYSTEM_INSTRUCTION}
     * and returns the fully-rendered system prompt string.
     *
     * @param contextText the retrieved vector-store context, or {@code null}
     */
    private String buildSystemPrompt(String contextText) {
        String safeContext = contextText != null ? contextText : "";
                return BASE_SYSTEM_INSTRUCTION + "\n\nContext:\n" + safeContext;
    }

    /**
     * Mock response returned when GEMINI_API_KEY is not configured. Allows
     * the full chat pipeline to be exercised in local development without
     * a paid API quota.
     */
    private static final String MOCK_RESPONSE_PREFIX =
            "[Local dev — GEMINI_API_KEY not configured] ";

    private final ChatClient chatClient;
    private final ChatSessionRepository sessionRepository;
    private final ChatMessageRepository messageRepository;
    private final EscalationService escalationService;
    private final RagService ragService;
    private final UserService userService;
    private final WebSocketChatController webSocketController;
    private final String geminiApiKey;

    public ChatService(ChatClient.Builder chatClientBuilder,
                       ChatSessionRepository sessionRepository,
                       ChatMessageRepository messageRepository,
                       EscalationService escalationService,
                       RagService ragService,
                       UserService userService,
                       WebSocketChatController webSocketController,
                       @Value("${spring.ai.google.genai.api-key:}") String geminiApiKey) {
        this.chatClient = chatClientBuilder.build();
        this.sessionRepository = sessionRepository;
        this.messageRepository = messageRepository;
        this.escalationService = escalationService;
        this.ragService = ragService;
        this.userService = userService;
        this.webSocketController = webSocketController;
        this.geminiApiKey = geminiApiKey;
    }

    /**
     * Returns true when the Gemini API key is missing or is a placeholder.
     */
    private boolean isApiKeyMissing() {
        return geminiApiKey == null || geminiApiKey.isBlank()
                || geminiApiKey.contains("your-") || geminiApiKey.contains("placeholder");
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

        // ── RAG-first intent routing ──────────────────────────────────────
        // Always run the vector search FIRST. If the knowledge base contains
        // relevant information answering the question, reply directly using
        // the RAG context — even if the user also mentioned escalation
        // phrases.  Only escalate when the user explicitly requests a human
        // AND the vector store has no relevant context to answer the query.

        boolean explicitEscalationRequest = escalationService.isEscalationRequest(userMessage);
        GenerationResult generation;

        if ("ESCALATED".equals(session.getStatus())) {
            // Human agent active — no AI generation
            generation = new GenerationResult(AGENT_ACTIVE_ACK, false, List.of(), List.of());
        } else {
            // Run RAG retrieval for ALL incoming queries
            RagService.RagContext rag = ragService.retrieveContext(userMessage);
            boolean ragHasContext = !rag.contextText().isBlank();

            if (!ragHasContext) {
                log.warn("No knowledge base context retrieved for query: {} — "
                        + "the bot will answer without RAG grounding. "
                        + "Check that documents are ingested and the vector_store table has data.",
                        userMessage.length() > 80 ? userMessage.substring(0, 80) + "..." : userMessage);
            }

            if (explicitEscalationRequest) {
                // User explicitly wants a human — always acknowledge and
                // escalate, even when the knowledge base has relevant context.
                // The AI model may mention escalation in a RAG response, but
                // without the HANDOFF_ACK + ticket creation the customer just
                // gets a confusing reply with no actual handoff.
                generation = new GenerationResult(HANDOFF_ACK, false, List.of(), List.of());
            } else if (ragHasContext) {
                // Knowledge base has relevant info and no escalation request
                // → answer directly from RAG context.
                generation = generateResponseWithContext(userMessage, rag);
            } else {
                // No RAG context and no escalation request — let the AI
                // answer from its base instruction. The system prompt tells
                // it to say "I couldn't find that" and offer escalation
                // only when the customer explicitly asks.
                generation = generateResponse(userMessage, rag);
            }
        }

        messageRepository.save(new ChatMessage(session.getId(), "AI", generation.text()));

        // Broadcast AI response via WebSocket
        try {
            webSocketController.broadcastStatusChange(session.getId(), session.getStatus());
        } catch (Exception e) {
            log.debug("WebSocket broadcast failed (client may not be connected): {}", e.getMessage());
        }

        // Escalate whenever the customer explicitly asks for a human agent.
        // This must fire regardless of RAG context so that a support ticket
        // is always created and the session moves to ESCALATED status.
        if (explicitEscalationRequest) {
            List<ChatMessage> transcript = messageRepository.findBySessionIdOrderByTimestampAsc(session.getId());
            escalationService.escalate(session, userMessage, transcript);
            
            // Broadcast status change to all subscribers
            try {
                webSocketController.broadcastStatusChange(session.getId(), "ESCALATED");
            } catch (Exception e) {
                log.debug("WebSocket broadcast failed: {}", e.getMessage());
            }
        }

        ChatResponseDto response = new ChatResponseDto(generation.text(), session.getId(), session.getStatus());
        response.setRagUsed(generation.ragUsed());
        response.setContextReferences(generation.references());
        response.setSourceCitations(generation.citations());
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

    /**
     * Close/end a chat session by marking it as CLOSED.
     * Called when the customer starts a new conversation so the previous
     * session is properly archived.
     */
    public void closeSession(Long sessionId) {
        ChatSession session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new ResourceNotFoundException("Chat session not found: " + sessionId));
        session.setStatus("CLOSED");
        sessionRepository.save(session);
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
     * Generate an AI response using an already-retrieved RAG context.
     *
     * <p>Used by the RAG-first routing in {@link #sendMessage} when the
     * vector store returned relevant chunks.  This avoids a redundant
     * retrieval call and ensures the answer is grounded in the knowledge
     * base context.
     */
    private GenerationResult generateResponseWithContext(String userMessage, RagService.RagContext rag) {
        String systemInstruction = buildSystemPrompt(rag.contextText());

        if (isApiKeyMissing()) {
            log.info("GEMINI_API_KEY is not configured — returning mock chat response for local development");
            String mockAnswer = MOCK_RESPONSE_PREFIX
                    + "Thank you for your message! I'm your AI support assistant for Code of Africa. "
                    + "In a production environment I would answer your question using our knowledge base. "
                    + "To enable live AI responses, set the GEMINI_API_KEY environment variable. "
                    + "Is there anything else I can help with?";
            List<ChatResponseDto.ContextReference> references = rag.references().stream()
                    .map(r -> new ChatResponseDto.ContextReference(
                            r.documentId(), r.title(), r.sourceType()))
                    .toList();
            List<SourceCitationDto> citations = rag.toCitations();
            return new GenerationResult(mockAnswer, true, references, citations);
        }

                try {
                        log.debug("Calling Spring AI ChatModel through ChatClient for RAG response");
                        String answer = chatClient.prompt()
                    .system(systemInstruction)
                    .user(userMessage)
                    .call()
                    .content();
                        if (answer == null || answer.isBlank()) {
                                throw new IllegalStateException("Spring AI returned an empty response");
                        }
            List<ChatResponseDto.ContextReference> references = rag.references().stream()
                    .map(r -> new ChatResponseDto.ContextReference(
                            r.documentId(), r.title(), r.sourceType()))
                    .toList();
            List<SourceCitationDto> citations = rag.toCitations();
            return new GenerationResult(answer, true, references, citations);
        } catch (Exception e) {
            log.error("AI response generation failed ({}: {})",
                    e.getClass().getSimpleName(), e.getMessage(), e);
            throw e;
        }
    }

    /**
     * AI response generation without RAG context: the caller already ran
     * vector retrieval and found nothing relevant, so this method builds
     * a context-free system prompt and lets the LLM answer from its base
     * knowledge. Falls back to a friendly message when the model is
     * unreachable (e.g. missing GEMINI_API_KEY).
     *
     * <p>When the Gemini API key is not configured the method short-circuits
     * with a mock response so the full chat pipeline can be tested locally
     * without a paid API quota.
     */
    private GenerationResult generateResponse(String userMessage, RagService.RagContext rag) {
        String systemInstruction = buildSystemPrompt(rag.contextText());

        // When the API key is missing, skip the AI call entirely and
        // return a structured mock response so the pipeline works end-to-end.
        if (isApiKeyMissing()) {
            log.info("GEMINI_API_KEY is not configured — returning mock chat response for local development");
            String mockAnswer = MOCK_RESPONSE_PREFIX
                    + "Thank you for your message! I'm your AI support assistant for Code of Africa. "
                    + "In a production environment I would answer your question using our knowledge base. "
                    + "To enable live AI responses, set the GEMINI_API_KEY environment variable. "
                    + "Is there anything else I can help with?";
            List<ChatResponseDto.ContextReference> references = rag.references().stream()
                    .map(r -> new ChatResponseDto.ContextReference(
                            r.documentId(), r.title(), r.sourceType()))
                    .toList();
            List<SourceCitationDto> citations = rag.toCitations();
            return new GenerationResult(mockAnswer, false, references, citations);
        }

                try {
                        log.debug("Calling Spring AI ChatModel through ChatClient for response");
                        String answer = chatClient.prompt()
                    .system(systemInstruction)
                    .user(userMessage)
                    .call()
                    .content();
                        if (answer == null || answer.isBlank()) {
                                throw new IllegalStateException("Spring AI returned an empty response");
                        }
            List<ChatResponseDto.ContextReference> references = rag.references().stream()
                    .map(r -> new ChatResponseDto.ContextReference(
                            r.documentId(), r.title(), r.sourceType()))
                    .toList();
            List<SourceCitationDto> citations = rag.toCitations();
            return new GenerationResult(answer, false, references, citations);
        } catch (Exception e) {
            log.error("AI response generation failed ({}: {})",
                    e.getClass().getSimpleName(), e.getMessage(), e);
            throw e;
        }
    }

    /** Response text + whether it was grounded in retrieved context (+ source references + citations). */
    private record GenerationResult(String text, boolean ragUsed,
                                    List<ChatResponseDto.ContextReference> references,
                                    List<SourceCitationDto> citations) {
    }

    private ChatMessageDto toMessageDto(ChatMessage message) {
        return new ChatMessageDto(
            message.getId(), 
            message.getSender(), 
            message.getContent(), 
            message.getTimestamp(),
            message.isInternal()
        );
    }
}
