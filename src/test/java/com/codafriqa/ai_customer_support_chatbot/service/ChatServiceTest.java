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
            return message != null && message.toLowerCase().contains("human");
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
        RagService ragService = new RagService(null, chatClientBuilder, null);
        service = new ChatService(
                chatClientBuilder, sessionRepository, messageRepository,
                escalationService, ragService, userService);
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
        RagService ragService = new RagService(new StubVectorStore(List.of(doc)), chatClientBuilder, null);
        service = new ChatService(
                chatClientBuilder, sessionRepository, messageRepository,
                escalationService, ragService, new UserService(userRepository));

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
            throw new IllegalStateException("vector store unavailable (no OPENAI_API_KEY?)");
        }
    }

    @Test
    void aiFailureReturnsStructuredFallbackResponseInsteadOfThrowing() {
        // Simulates a placeholder or invalid OPENAI_API_KEY: the model call
        // itself fails (401) at runtime — the service must not let that
        // exception escape; it returns a friendly fallback response instead.
        stubInfrastructure(false);
        when(chatClient.prompt()).thenReturn(requestSpec);
        when(requestSpec.system(any(String.class))).thenReturn(requestSpec);
        when(requestSpec.user(any(String.class))).thenReturn(requestSpec);
        when(requestSpec.call())
                .thenThrow(new IllegalStateException("401 Unauthorized — bad API key"));

        var result = service.sendMessage("Where is my order?", null);

        assertTrue(result.getResponse().contains("having trouble processing"));
        assertEquals("ACTIVE", result.getStatus());
        assertFalse(result.isRagUsed());
        assertTrue(result.getContextReferences().isEmpty());
        assertFalse(escalationService.escalateCalled);
    }

    @Test
    void retrievalFailureFallsBackToPlainPromptWithoutThrowing() {
        // Simulates a missing OPENAI_API_KEY / unreachable vector store: the
        // similarity search throws, RagService swallows it and returns empty
        // context, and ChatService answers from the base instruction alone.
        stubInfrastructure(false);
        stubAiAnswer("Answer without knowledge base context");
        RagService ragService = new RagService(new ThrowingVectorStore(), chatClientBuilder, null);
        service = new ChatService(
                chatClientBuilder, sessionRepository, messageRepository,
                escalationService, ragService, new UserService(userRepository));

        var result = service.sendMessage("What is your return policy?", null);

        assertEquals("Answer without knowledge base context", result.getResponse());
        assertFalse(result.isRagUsed());
        assertTrue(result.getContextReferences().isEmpty());
        assertTrue(result.getSourceCitations().isEmpty());

        // The system prompt must NOT contain the knowledge base section.
        ArgumentCaptor<String> systemCaptor = ArgumentCaptor.forClass(String.class);
        verify(requestSpec).system(systemCaptor.capture());
        assertFalse(systemCaptor.getValue().contains("Relevant knowledge base context"));
        assertTrue(systemCaptor.getValue().contains("AI Customer Support Agent"));
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
}
