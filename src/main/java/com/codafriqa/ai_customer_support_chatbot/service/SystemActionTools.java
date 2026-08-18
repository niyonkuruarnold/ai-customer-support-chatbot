package com.codafriqa.ai_customer_support_chatbot.service;

import com.codafriqa.ai_customer_support_chatbot.model.SupportTicket;
import com.codafriqa.ai_customer_support_chatbot.repository.SupportTicketRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Spring AI function-calling tools that the model can invoke during a RAG
 * chat to perform live system actions.
 *
 * <p>Each method annotated with {@link Tool} is automatically exposed as a
 * callable function when the {@code ChatClient} is configured with a
 * {@link org.springframework.ai.chat.client.ChatClient.Builder#defaultTools(Object...)}
 * or {@code .tools()} call.
 *
 * <p>Example registration in {@code ChatService}:
 * <pre>{@code
 * this.chatClient = chatClientBuilder
 *         .defaultTools(new SystemActionTools(ticketRepository))
 *         .build();
 * }</pre>
 */
@Component
public class SystemActionTools {

    private static final Logger log = LoggerFactory.getLogger(SystemActionTools.class);

    private final SupportTicketRepository ticketRepository;

    public SystemActionTools(SupportTicketRepository ticketRepository) {
        this.ticketRepository = ticketRepository;
    }

    /**
     * Look up the current status and priority of a support ticket by its id.
     *
     * <p>The AI model calls this tool when the customer asks about an
     * existing ticket (e.g. "What's the status of ticket 5?").
     *
     * @param ticketId the support ticket id to look up
     * @return a human-readable status summary, or a "not found" message
     */
    @Tool(description = "Check the current status and priority of a support ticket by its id")
    public String checkTicketStatus(@ToolParam(description = "The support ticket id to look up") String ticketId) {
        log.info("Function call: checkTicketStatus({})", ticketId);
        try {
            Long id = Long.parseLong(ticketId);
            Optional<SupportTicket> ticket = ticketRepository.findById(id);
            if (ticket.isEmpty()) {
                return "Ticket #" + id + " not found. Please double-check the ticket number and try again.";
            }
            SupportTicket t = ticket.get();
            return String.format(
                    "Ticket #%d — Status: %s, Priority: %s, Subject: %s",
                    t.getId(), t.getStatus(), t.getPriority(), t.getSubject());
        } catch (NumberFormatException e) {
            return "Invalid ticket id: '" + ticketId + "'. Please provide a numeric ticket id.";
        }
    }
}
