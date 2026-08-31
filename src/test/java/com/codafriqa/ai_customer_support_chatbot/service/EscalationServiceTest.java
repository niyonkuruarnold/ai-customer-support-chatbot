package com.codafriqa.ai_customer_support_chatbot.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies that {@link EscalationService#isEscalationRequest(String)} only
 * triggers on explicit requests to talk to a live person, and does NOT
 * trigger on informational questions that merely mention human agents,
 * support hours, or related topics.
 */
@ExtendWith(MockitoExtension.class)
class EscalationServiceTest {

    // The real EscalationService needs collaborators, but we only test
    // the static phrase-matching logic so nulls are fine for this unit test.
    private final EscalationService service = new EscalationService(null, null, null, null);

    // ── Should trigger escalation (explicit live-support requests) ──

    @Test
    void triggersOnTalkToHuman() {
        assertTrue(service.isEscalationRequest("I want to talk to a human"));
    }

    @Test
    void triggersOnSpeakToAgent() {
        assertTrue(service.isEscalationRequest("Can I speak to an agent?"));
    }

    @Test
    void triggersOnConnectMe() {
        assertTrue(service.isEscalationRequest("Connect me to a human, please"));
    }

    @Test
    void triggersOnTransferMe() {
        assertTrue(service.isEscalationRequest("Transfer me to an agent"));
    }

    @Test
    void triggersOnINeedAHuman() {
        assertTrue(service.isEscalationRequest("I need a human to help me"));
    }

    @Test
    void triggersOnEscalateTicket() {
        assertTrue(service.isEscalationRequest("Please escalate this ticket"));
    }

    @Test
    void triggersOnTalkToSomeone() {
        assertTrue(service.isEscalationRequest("I want to talk to someone"));
    }

    @Test
    void triggersOnISpeakToSomeone() {
        assertTrue(service.isEscalationRequest("I need to speak to someone"));
    }

    @Test
    void triggersOnConnectToSupport() {
        assertTrue(service.isEscalationRequest("Connect me to support"));
    }

    // ── Should NOT trigger escalation (informational questions) ──

    @Test
    void doesNotTriggerOnHumanAgentHours() {
        assertFalse(service.isEscalationRequest("What are the human agent support hours?"));
    }

    @Test
    void doesNotTriggerOnHumanSupportPolicy() {
        assertFalse(service.isEscalationRequest("What is your human support policy?"));
    }

    @Test
    void doesNotTriggerOnCustomerServiceAgentContact() {
        assertFalse(service.isEscalationRequest("How do I contact a customer service agent?"));
    }

    @Test
    void doesNotTriggerOnSupportRepresentativeAvailability() {
        assertFalse(service.isEscalationRequest("Is there a support representative available right now?"));
    }

    @Test
    void doesNotTriggerOnHumanAssistantRole() {
        assertFalse(service.isEscalationRequest("What does the human assistant do?"));
    }

    @Test
    void doesNotTriggerOnCustomerSupportAgentHours() {
        assertFalse(service.isEscalationRequest("What are the customer support agent hours?"));
    }

    @Test
    void doesNotTriggerOnEscalationInformation() {
        assertFalse(service.isEscalationRequest("What is the escalation process?"));
    }

    @Test
    void doesNotTriggerOnNormalQuestions() {
        assertFalse(service.isEscalationRequest("Where is my order?"));
    }

    @Test
    void doesNotTriggerOnThankYou() {
        assertFalse(service.isEscalationRequest("Thanks for the help!"));
    }

    @Test
    void doesNotTriggerOnNull() {
        assertFalse(service.isEscalationRequest(null));
    }

    @Test
    void doesNotTriggerOnEmptyString() {
        assertFalse(service.isEscalationRequest(""));
    }
}
