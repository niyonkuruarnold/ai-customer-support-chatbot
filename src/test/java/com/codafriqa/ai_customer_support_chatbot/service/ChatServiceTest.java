package com.codafriqa.ai_customer_support_chatbot.service;

import com.codafriqa.ai_customer_support_chatbot.model.ChatMessage;
import com.codafriqa.ai_customer_support_chatbot.model.ChatSession;
import com.codafriqa.ai_customer_support_chatbot.model.SupportTicket;
import com.codafriqa.ai_customer_support_chatbot.model.User;
import com.codafriqa.ai_customer_support_chatbot.model.UserRole;
import com.codafriqa.ai_customer_support_chatbot.repository.ChatMessageRepository;
import com.codafriqa.ai_customer_support_chatbot.repository.ChatSessionRepository;
import com.codafriqa.ai_customer_support_chatbot.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.Filter;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Verifies the chat behavior around handoff and RAG:
 * <ul>
 *   <li>The AI answers (via ChatClient) while the session is ACTIVE, with the
 *       retrieved knowledge base context injected into the system prompt and
 *       surfaced as context references.</li>
 *   <li>Once the session is ESCALATED (a human agent is active) automated AI
 *       responses are PAUSED and a short acknowledgement is returned.</li>
 *   <li>An escalation trigger returns the handoff acknowledgement and
 *       escalates the session/ticket.</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class ChatServiceTest {

    @Mock
    private ChatClient.Builder chatClientBuilder;

    @Mock
    private ChatClient chatClient;

    @Mock
    private ChatClient.ChatClientRequestSpec requestSpec;

    @Mock
    private ChatClient.CallResponseSpec callResponseSpec;

    @Mock
    private ChatSessionRepository sessionRepository;

    @Mock
    private ChatMessageRepository messageRepository;

    @Mock
    private UserRepository userRepository;

    private final RecordingEscalationService escalationService = new RecordingEscalationService();

    private ChatService service;

    /** Real EscalationService subclass that records triggers instead of touching Spring AI/repos. */
    static class RecordingEscalationService extends EscalationService {
        boolean escalateCalled = false;

        RecordingEscalationService() {
            super(null, null, null, null);
        }

        @Override
        public boolean isEscalationRequest(String message) {
            return super.isEscalationRequest(message);
        }

        @Override
        public SupportTicket escalate(ChatSession session, String triggerMessage, List<ChatMessage> transcript) {
            escalateCalled = true;
            return null;
        }
    }

    /** Minimal VectorStore returning a fixed result list (Mockito can't mock this interface on this JDK). */
    static class StubVectorStore implements VectorStore {
        private final List<Document> results;

        StubVectorStore(List<Document> results) {
            this.results = results;
        }

        @Override
        public void add(List<Document> documents) {
        }

        @Override
        public void delete(List<String> ids) {
        }

        @Override
        public void delete(Filter.Expression expression) {
        }

        @Override
        public List<Document> similaritySearch(SearchRequest request) {
            return results;
        }
    }

    @BeforeEach
    void setUp() {
        when(chatClientBuilder.build()).thenReturn(chatClient);
        UserService userService = new UserService(userRepository);
        // RagService with a null VectorStore takes the graceful "no context"
        // path (retrieval never throws). The RAG test below swaps in a real
        // inline StubVectorStore.
        RagService ragService = new RagService(null, null, null, "test-api-key");
        service = new ChatService(
                chatClientBuilder, sessionRepository, messageRepository,
                escalationService, ragService, userService, "test-api-key");
    }

    private void stubInfrastructure(boolean escalated) {
        when(userRepository.findByEmail(any())).thenReturn(Optional.of(
                new User("customer@example.com", "hash", UserRole.CUSTOMER)));
        when(messageRepository.save(any(ChatMessage.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        if (escalated) {
            ChatSession session = new ChatSession(1L);
            session.setId(7L);
            session.setStatus("ESCALATED");
            when(sessionRepository.findById(7L)).thenReturn(Optional.of(session));
        } else {
            when(sessionRepository.save(any(ChatSession.class)))
                    .thenAnswer(inv -> inv.getArgument(0));
        }
    }

    private void stubAiAnswer(String answer) {
        when(chatClient.prompt()).thenReturn(requestSpec);
        when(requestSpec.system(any(String.class))).thenReturn(requestSpec);
        when(requestSpec.user(any(String.class))).thenReturn(requestSpec);
        when(requestSpec.call()).thenReturn(callResponseSpec);
        when(callResponseSpec.content()).thenReturn(answer);
    }

    @Test
    void activeSessionAnswersWithTheAi() {
        stubInfrastructure(false);
        stubAiAnswer("Here is your answer");

        var result = service.sendMessage("Where is my order?", null);

        assertEquals("Here is your answer", result.getResponse());
        assertEquals("ACTIVE", result.getStatus());
        verify(chatClient).prompt();
        assertFalse(result.isRagUsed());
        assertTrue(result.getContextReferences().isEmpty());
        assertFalse(escalationService.escalateCalled);
    }

    @Test
    void retrievedContextIsInjectedIntoTheSystemPromptAndReferenced() {
        stubInfrastructure(false);
        stubAiAnswer("Grounded answer");

        Document doc = new Document("Returns are accepted within 30 days of delivery.",
                Map.of("documentId", 42L, "title", "Returns Policy", "sourceType", "TEXT"));
        RagService ragService = new RagService(new StubVectorStore(List.of(doc)), null, null, "test-api-key");
        service = new ChatService(
                chatClientBuilder, sessionRepository, messageRepository,
                escalationService, ragService, new UserService(userRepository), "test-api-key");

        var result = service.sendMessage("What is your return policy?", null);

        assertTrue(result.isRagUsed());
        assertEquals(1, result.getContextReferences().size());
        assertEquals(42L, result.getContextReferences().get(0).documentId());
        assertEquals("Returns Policy", result.getContextReferences().get(0).title());
        assertEquals("TEXT", result.getContextReferences().get(0).sourceType());

        // Source citations should also be populated.
        assertEquals(1, result.getSourceCitations().size());
        assertEquals(42L, result.getSourceCitations().get(0).sourceId());
        assertEquals("Returns Policy", result.getSourceCitations().get(0).title());
        assertEquals("TEXT", result.getSourceCitations().get(0).sourceType());

        // The retrieved chunk must be injected into the system prompt.
        ArgumentCaptor<String> systemCaptor = ArgumentCaptor.forClass(String.class);
        verify(requestSpec).system(systemCaptor.capture());
        assertTrue(systemCaptor.getValue().contains("Returns are accepted within 30 days of delivery."));
    }

    /** VectorStore whose similarity search always fails (unreachable store / no embedding key). */
    static class ThrowingVectorStore implements VectorStore {
        @Override
        public void add(List<Document> documents) {
        }

        @Override
        public void delete(List<String> ids) {
        }

        @Override
        public void delete(Filter.Expression expression) {
        }

        @Override
        public List<Document> similaritySearch(SearchRequest request) {
            throw new IllegalStateException("vector store unavailable (no GEMINI_API_KEY?)");
        }
    }

    @Test
    void aiFailurePropagatesExceptionToController() {
        // Simulates a placeholder or invalid GEMINI_API_KEY: the model call
        // itself fails (401) at runtime — the service now re-throws so the
        // controller / GlobalExceptionHandler can return a structured error
        // with the exact failure detail (e.g. class name + message).
        stubInfrastructure(false);
        when(chatClient.prompt()).thenReturn(requestSpec);
        when(requestSpec.system(any(String.class))).thenReturn(requestSpec);
        when(requestSpec.user(any(String.class))).thenReturn(requestSpec);
        when(requestSpec.call())
                .thenThrow(new IllegalStateException("401 Unauthorized — bad API key"));

        var ex = assertThrows(
                IllegalStateException.class,
                () -> service.sendMessage("Where is my order?", null));
        assertTrue(ex.getMessage().contains("401 Unauthorized"));
    }

    @Test
    void retrievalFailureFallsBackToPlainPromptWithoutThrowing() {
        // Simulates a missing GEMINI_API_KEY / unreachable vector store: the
        // similarity search throws, RagService swallows it and returns empty
        // context, and ChatService answers from the base instruction alone.
        stubInfrastructure(false);
        stubAiAnswer("Answer without knowledge base context");
        RagService ragService = new RagService(new ThrowingVectorStore(), null, null, "test-api-key");
        service = new ChatService(
                chatClientBuilder, sessionRepository, messageRepository,
                escalationService, ragService, new UserService(userRepository), "test-api-key");

        var result = service.sendMessage("What is your return policy?", null);

        assertEquals("Answer without knowledge base context", result.getResponse());
        assertFalse(result.isRagUsed());
        assertTrue(result.getContextReferences().isEmpty());
        assertTrue(result.getSourceCitations().isEmpty());

        // The system prompt must NOT contain the knowledge base section.
        ArgumentCaptor<String> systemCaptor = ArgumentCaptor.forClass(String.class);
        verify(requestSpec).system(systemCaptor.capture());
        assertFalse(systemCaptor.getValue().contains("RETRIEVED SYSTEM CONTEXT"));
        assertTrue(systemCaptor.getValue().contains("CODAFRIQA Support Assistant")
                || systemCaptor.getValue().contains("CODAFRIQA AI Assistant"));
    }

    @Test
    void escalatedSessionPausesAiAndAcksTheMessage() {
        stubInfrastructure(true);
        // chatClient is intentionally left unstubbed — it must never be called

        var result = service.sendMessage("Where is my order?", 7L);

        assertTrue(result.getResponse().contains("sent to the agent"));
        assertEquals("ESCALATED", result.getStatus());
        verify(chatClient, never()).prompt();
        assertTrue(result.getSourceCitations().isEmpty());
        assertFalse(escalationService.escalateCalled);
    }

    @Test
    void escalationTriggerReturnsHandoffAckAndEscalates() {
        stubInfrastructure(false);
        when(messageRepository.findBySessionIdOrderByTimestampAsc(any()))
                .thenReturn(List.of());

        var result = service.sendMessage("I want to talk to a human agent", null);

        assertTrue(result.getResponse().contains("connected to a human support agent"));
        verify(chatClient, never()).prompt();
        assertTrue(escalationService.escalateCalled);
    }

    @Test
    void escalationTriggerWhileAgentAlreadyActiveStaysPaused() {
        stubInfrastructure(true);
        when(messageRepository.findBySessionIdOrderByTimestampAsc(any()))
                .thenReturn(List.of());

        var result = service.sendMessage("Talk to a human please", 7L);

        assertTrue(result.getResponse().contains("sent to the agent"));
        assertEquals("ESCALATED", result.getStatus());
        verify(chatClient, never()).prompt();
    }

    /**
     * RAG-first routing: when the knowledge base has relevant context,
     * informational questions about human agents are answered directly
     * from the RAG context without triggering escalation.
     */
    @Test
    void informationalQuestionAboutHumanAgentHoursAnsweredFromRagNotEscalated() {
        stubInfrastructure(false);
        stubAiAnswer("Our human support agents are available Mon-Fri 9am-5pm.");

        Document doc = new Document(
                "Human support agents are available Monday to Friday, 9am to 5pm.",
                Map.of("documentId", 10L, "title", "Support Hours", "sourceType", "TEXT"));
        RagService ragService = new RagService(new StubVectorStore(List.of(doc)), null, null, "test-api-key");
        service = new ChatService(
                chatClientBuilder, sessionRepository, messageRepository,
                escalationService, ragService, new UserService(userRepository), "test-api-key");

        var result = service.sendMessage("What are the human agent support hours?", null);

        // RAG context was found and used to answer the question
        assertTrue(result.isRagUsed());
        assertFalse(result.getContextReferences().isEmpty());
        assertEquals("Our human support agents are available Mon-Fri 9am-5pm.", result.getResponse());
        // MUST NOT escalate — the question is informational, not a request for a human
        assertFalse(escalationService.escalateCalled);
    }

    /**
     * Explicit escalation request: even when the knowledge base has relevant
     * context, an explicit "talk to a human" request must always trigger
     * escalation — the customer clearly wants a live person, not an AI answer.
     */
    @Test
    void escalationRequestWithRelevantRagContextStillEscalates() {
        stubInfrastructure(false);
        when(messageRepository.findBySessionIdOrderByTimestampAsc(any()))
                .thenReturn(List.of());

        Document doc = new Document(
                "To talk to a human agent, call our support line at +1-555-0123.",
                Map.of("documentId", 20L, "title", "Contact Info", "sourceType", "TEXT"));
        RagService ragService = new RagService(new StubVectorStore(List.of(doc)), null, null, "test-api-key");
        service = new ChatService(
                chatClientBuilder, sessionRepository, messageRepository,
                escalationService, ragService, new UserService(userRepository), "test-api-key");

        var result = service.sendMessage("I want to talk to a human agent", null);

        // Explicit escalation request → handoff ack, NOT a RAG response
        assertTrue(result.getResponse().contains("connected to a human support agent"));
        assertFalse(result.isRagUsed());
        // Session MUST be escalated regardless of RAG context
        assertTrue(escalationService.escalateCalled);
    }

    /**
     * RAG-first routing: when the knowledge base has NO relevant context
     * AND the user explicitly requests a human, the session IS escalated.
     */
    @Test
    void escalationRequestWithNoRagContextTriggersHandoff() {
        stubInfrastructure(false);
        when(messageRepository.findBySessionIdOrderByTimestampAsc(any()))
                .thenReturn(List.of());

        // Empty vector store — no relevant context
        RagService ragService = new RagService(new StubVectorStore(List.of()), null, null, "test-api-key");
        service = new ChatService(
                chatClientBuilder, sessionRepository, messageRepository,
                escalationService, ragService, new UserService(userRepository), "test-api-key");

        var result = service.sendMessage("I want to talk to a human agent", null);

        // No RAG context + explicit escalation request → handoff
        assertTrue(result.getResponse().contains("connected to a human support agent"));
        assertFalse(result.isRagUsed());
        assertTrue(escalationService.escalateCalled);
    }

    /**
     * When GEMINI_API_KEY is missing, the mock response must still carry
     * citations from the RagService mock context so the frontend citation
     * UI can be exercised end-to-end.
     */
    @Test
    void mockModeReturnsResponseWithCitationsForCitationFlowTesting() {
        stubInfrastructure(false);
        // Use null API key to trigger mock mode in both RagService and ChatService.
        RagService ragService = new RagService(new StubVectorStore(List.of()), null, null, null);
        service = new ChatService(
                chatClientBuilder, sessionRepository, messageRepository,
                escalationService, ragService, new UserService(userRepository), null);

        var result = service.sendMessage("Tell me about your products", null);

        // The response should contain the local-dev prefix.
        assertTrue(result.getResponse().contains("GEMINI_API_KEY") || result.getResponse().contains("local"));
        assertEquals("ACTIVE", result.getStatus());

        // RAG was used (mock context is non-blank).
        assertTrue(result.isRagUsed());

        // Mock context carries citations — the frontend citation UI must have data.
        assertFalse(result.getContextReferences().isEmpty(),
                "Mock mode must provide context references for citation flow testing");
        assertFalse(result.getSourceCitations().isEmpty(),
                "Mock mode must provide source citations for citation flow testing");
        assertEquals(3, result.getContextReferences().size());
        assertEquals(3, result.getSourceCitations().size());

        // The OpenAI chat client must NOT have been called.
        verify(chatClient, never()).prompt();
    }
}
