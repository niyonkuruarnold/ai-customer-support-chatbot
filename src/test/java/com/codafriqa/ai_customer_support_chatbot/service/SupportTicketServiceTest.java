package com.codafriqa.ai_customer_support_chatbot.service;

import com.codafriqa.ai_customer_support_chatbot.exception.ResourceNotFoundException;
import com.codafriqa.ai_customer_support_chatbot.model.SupportTicket;
import com.codafriqa.ai_customer_support_chatbot.model.User;
import com.codafriqa.ai_customer_support_chatbot.repository.SupportTicketRepository;
import com.codafriqa.ai_customer_support_chatbot.repository.UserRepository;
import com.codafriqa.ai_customer_support_chatbot.service.EmailNotificationService.TicketEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SupportTicketServiceTest {

    @Mock
    private SupportTicketRepository ticketRepository;

    @Mock
    private UserRepository userRepository;

    private final RecordingEmailService emailService = new RecordingEmailService();

    private SupportTicketService service;

    @BeforeEach
    void setUp() {
        service = new SupportTicketService(ticketRepository, userRepository, emailService);
    }

    /**
     * Real EmailNotificationService subclass that records sends instead of
     * hitting SMTP. (Mockito cannot mock the concrete EmailNotificationService
     * class on JDK 26, so we substitute a recording instance.)
     */
    static class RecordingEmailService extends EmailNotificationService {
        final List<String> events = new ArrayList<>();
        final List<String> recipients = new ArrayList<>();

        RecordingEmailService() {
            super(null, "no-reply@test.local");
        }

        @Override
        public void sendTicketNotification(String to, SupportTicket ticket, TicketEvent event) {
            recipients.add(to);
            events.add(event.name());
        }
    }

    private SupportTicket ticket(String status) {
        SupportTicket t = new SupportTicket(1L, 10L, "Refund request", "I need a refund");
        t.setId(5L);
        t.setStatus(status);
        return t;
    }

    private void stubSaveAndCustomer() {
        when(ticketRepository.save(any(SupportTicket.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        when(userRepository.findById(1L))
                .thenReturn(Optional.of(new User("customer@example.com", "hash", null)));
    }

    @Test
    void openCreatesTicketAndSendsOpenedEmail() {
        stubSaveAndCustomer();

        SupportTicket created = service.open(1L, 10L, "Refund request", "I need a refund");

        assertEquals("OPEN", created.getStatus());
        assertEquals(List.of(TicketEvent.OPENED.name()), emailService.events);
        assertEquals(List.of("customer@example.com"), emailService.recipients);
    }

    @Test
    void takeOverMovesOpenToInProgressAndEmailsUpdate() {
        stubSaveAndCustomer();
        when(ticketRepository.findById(5L)).thenReturn(Optional.of(ticket("OPEN")));

        SupportTicket result = service.takeOver(5L, "sarah");

        assertEquals("IN_PROGRESS", result.getStatus());
        assertEquals("sarah", result.getAssignedAgent());
        assertEquals(List.of(TicketEvent.UPDATED.name()), emailService.events);
        assertEquals(List.of("customer@example.com"), emailService.recipients);
    }

    @Test
    void takeOverAcceptsEscalatedTickets() {
        when(ticketRepository.findById(5L)).thenReturn(Optional.of(ticket("ESCALATED")));
        when(ticketRepository.save(any(SupportTicket.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        SupportTicket result = service.takeOver(5L, "sarah");

        assertEquals("IN_PROGRESS", result.getStatus());
    }

    @Test
    void resolveMovesInProgressToResolvedAndEmailsResolution() {
        stubSaveAndCustomer();
        SupportTicket t = ticket("IN_PROGRESS");
        t.setAssignedAgent("sarah");
        when(ticketRepository.findById(5L)).thenReturn(Optional.of(t));

        SupportTicket result = service.resolve(5L, "sarah");

        assertEquals("RESOLVED", result.getStatus());
        assertEquals(List.of(TicketEvent.RESOLVED.name()), emailService.events);
    }

    @Test
    void closeMovesResolvedToClosedWithoutEmail() {
        when(ticketRepository.findById(5L)).thenReturn(Optional.of(ticket("RESOLVED")));
        when(ticketRepository.save(any(SupportTicket.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        SupportTicket result = service.close(5L);

        assertEquals("CLOSED", result.getStatus());
        assertEquals(List.of(), emailService.events);
    }

    @Test
    void illegalTransitionsThrowIllegalArgumentException() {
        // CLOSED requires RESOLVED
        when(ticketRepository.findById(5L)).thenReturn(Optional.of(ticket("OPEN")));
        assertThrows(IllegalArgumentException.class, () -> service.close(5L));

        // RESOLVED cannot come from CLOSED (terminal)
        when(ticketRepository.findById(5L)).thenReturn(Optional.of(ticket("CLOSED")));
        assertThrows(IllegalArgumentException.class, () -> service.resolve(5L, "sarah"));

        // IN_PROGRESS cannot come from RESOLVED
        when(ticketRepository.findById(5L)).thenReturn(Optional.of(ticket("RESOLVED")));
        assertThrows(IllegalArgumentException.class, () -> service.takeOver(5L, "sarah"));
    }

    @Test
    void illegalTransitionLeavesStatusUntouched() {
        SupportTicket t = ticket("CLOSED");
        when(ticketRepository.findById(5L)).thenReturn(Optional.of(t));

        assertThrows(IllegalArgumentException.class, () -> service.resolve(5L, "sarah"));

        assertEquals("CLOSED", t.getStatus());
        verify(ticketRepository, never()).save(any());
        assertEquals(List.of(), emailService.events);
    }

    @Test
    void missingTicketThrowsNotFound() {
        when(ticketRepository.findById(999L)).thenReturn(Optional.empty());
        assertThrows(ResourceNotFoundException.class, () -> service.takeOver(999L, "sarah"));
    }

    @Test
    void listAppliesFiltersAndPagination() {
        SupportTicket t = ticket("ESCALATED");
        t.setAssignedAgent("sarah");
        Page<SupportTicket> page = new PageImpl<>(List.of(t));
        when(ticketRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(page);
        when(userRepository.findById(1L))
                .thenReturn(Optional.of(new User("customer@example.com", "hash", null)));

        var result = service.list("ESCALATED", "HIGH", null, PageRequest.of(0, 10));

        assertEquals(1, result.getTotalElements());
        assertEquals("customer@example.com", result.getContent().get(0).userEmail());
        assertEquals("ESCALATED", result.getContent().get(0).status());
    }

    @Test
    void listWithFiltersReturnsEmptyPage() {
        when(ticketRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));

        var result = service.list(null, null, 999L, PageRequest.of(0, 10));

        assertEquals(0, result.getTotalElements());
        assertEquals(0, result.getContent().size());
    }
}
