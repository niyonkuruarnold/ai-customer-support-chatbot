package com.codafriqa.ai_customer_support_chatbot.service;

import com.codafriqa.ai_customer_support_chatbot.model.SupportTicket;
import com.codafriqa.ai_customer_support_chatbot.repository.SupportTicketRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link SystemActionTools}: the Spring AI function-calling
 * tool that lets the model query live ticket status.
 */
@ExtendWith(MockitoExtension.class)
class SystemActionToolsTest {

    @Mock
    private SupportTicketRepository ticketRepository;

    @Test
    void checkTicketStatusReturnsFormattedStatusWhenTicketExists() {
        SupportTicket ticket = new SupportTicket(1L, 1L, "Login issue", "Cannot log in");
        ticket.setId(7L);
        ticket.setStatus("IN_PROGRESS");
        ticket.setPriority("HIGH");

        when(ticketRepository.findById(7L)).thenReturn(Optional.of(ticket));

        SystemActionTools tools = new SystemActionTools(ticketRepository);
        String result = tools.checkTicketStatus("7");

        assertTrue(result.contains("Ticket #7"));
        assertTrue(result.contains("IN_PROGRESS"));
        assertTrue(result.contains("HIGH"));
        assertTrue(result.contains("Login issue"));
    }

    @Test
    void checkTicketStatusReturnsNotFoundWhenTicketMissing() {
        when(ticketRepository.findById(99L)).thenReturn(Optional.empty());

        SystemActionTools tools = new SystemActionTools(ticketRepository);
        String result = tools.checkTicketStatus("99");

        assertTrue(result.contains("not found"));
        assertTrue(result.contains("99"));
    }

    @Test
    void checkTicketStatusReturnsInvalidMessageForNonNumericId() {
        SystemActionTools tools = new SystemActionTools(ticketRepository);
        String result = tools.checkTicketStatus("abc");

        assertTrue(result.contains("Invalid ticket id"));
        assertTrue(result.contains("abc"));
    }

    @Test
    void checkTicketStatusReturnsNotFoundForEmptyString() {
        SystemActionTools tools = new SystemActionTools(ticketRepository);
        String result = tools.checkTicketStatus("");

        assertTrue(result.contains("Invalid ticket id"));
    }
}
