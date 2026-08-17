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
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Verifies the chat handoff behavior: the AI answers while the session is
 * ACTIVE, but once the session is ESCALATED (a human agent is active)
 * automated AI responses are paused and a short acknowledgement is returned.
 */
@ExtendWith(MockitoExtension.class)
class ChatServiceTest {

    @Mock
    private ChatModel chatModel;

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

    @BeforeEach
    void setUp() {
        UserService userService = new UserService(userRepository);
        // KnowledgeBaseService.retrieveContext() is designed to never throw —
        // a null VectorStore makes it take the graceful "no context" path and
        // return "", which is exactly the behavior ChatService relies on.
        KnowledgeBaseService knowledgeBaseService = new KnowledgeBaseService(null, null, null);
        service = new ChatService(
                chatModel, sessionRepository, messageRepository,
                escalationService, knowledgeBaseService, userService);
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
        AssistantMessage assistant = new AssistantMessage(answer);
        Generation generation = new Generation(assistant);
        ChatResponse response = new ChatResponse(List.of(generation));
        when(chatModel.call(any(Prompt.class))).thenReturn(response);
    }

    @Test
    void activeSessionAnswersWithTheAi() {
        stubInfrastructure(false);
        stubAiAnswer("Here is your answer");

        var result = service.sendMessage("Where is my order?", null);

        assertEquals("Here is your answer", result.getResponse());
        assertEquals("ACTIVE", result.getStatus());
        verify(chatModel).call(any(Prompt.class));
        assertFalse(escalationService.escalateCalled);
    }

    @Test
    void escalatedSessionPausesAiAndAcksTheMessage() {
        stubInfrastructure(true);
        // chatModel is intentionally left unstubbed — it must never be called

        var result = service.sendMessage("Where is my order?", 7L);

        assertTrue(result.getResponse().contains("sent to the agent"));
        assertEquals("ESCALATED", result.getStatus());
        verify(chatModel, never()).call(any(Prompt.class));
        assertFalse(escalationService.escalateCalled);
    }

    @Test
    void escalationTriggerReturnsHandoffAckAndEscalates() {
        stubInfrastructure(false);
        when(messageRepository.findBySessionIdOrderByTimestampAsc(any()))
                .thenReturn(List.of());

        var result = service.sendMessage("I want to talk to a human agent", null);

        assertTrue(result.getResponse().contains("connected to a human support agent"));
        verify(chatModel, never()).call(any(Prompt.class));
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
        verify(chatModel, never()).call(any(Prompt.class));
    }
}
