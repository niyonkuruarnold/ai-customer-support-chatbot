package com.codafriqa.ai_customer_support_chatbot.service;

import com.codafriqa.ai_customer_support_chatbot.model.SupportTicket;
import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

/**
 * Automated email notifications for ticket lifecycle events (opened,
 * updated, resolved) via JavaMailSender — configured for Mailtrap-style
 * SMTP (see application.properties / .env.example).
 *
 * Sending is best-effort: any failure (missing SMTP credentials, offline
 * network, bad address) is logged as a warning and never propagates, so a
 * mail outage can never break a ticket operation.
 */
@Service
public class EmailNotificationService {

    private static final Logger log = LoggerFactory.getLogger(EmailNotificationService.class);

    /** Lifecycle events that produce a customer email. */
    public enum TicketEvent {
        OPENED,
        UPDATED,
        RESOLVED
    }

    private final JavaMailSender mailSender;
    private final String from;

    public EmailNotificationService(JavaMailSender mailSender,
                                    @Value("${app.mail.from:no-reply@codafriqa.local}") String from) {
        this.mailSender = mailSender;
        this.from = from;
    }

    /**
     * Send a ticket notification email to the customer. No-op (logged at
     * debug) when the recipient is unknown; never throws.
     */
    public void sendTicketNotification(String to, SupportTicket ticket, TicketEvent event) {
        if (to == null || to.isBlank()) {
            log.debug("Skipping {} email for ticket #{}: no customer email on record",
                    event, ticket.getId());
            return;
        }
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom(from);
            helper.setTo(to);
            helper.setSubject(subjectFor(event, ticket));
            helper.setText(textFor(event, ticket), htmlFor(event, ticket));
            mailSender.send(message);
            log.info("Sent ticket #{} '{}' notification to {}", ticket.getId(), event, to);
        } catch (Exception e) {
            log.warn("Could not send {} email for ticket #{} to {}: {}: {}",
                    event, ticket.getId(), to, e.getClass().getSimpleName(), e.getMessage());
        }
    }

    private String subjectFor(TicketEvent event, SupportTicket ticket) {
        return switch (event) {
            case OPENED -> "Your support ticket #" + ticket.getId() + " has been opened";
            case UPDATED -> "Update on your support ticket #" + ticket.getId();
            case RESOLVED -> "Your support ticket #" + ticket.getId() + " has been resolved";
        };
    }

    private String textFor(TicketEvent event, SupportTicket ticket) {
        String status = ticket.getStatus() == null ? "OPEN" : ticket.getStatus();
        String body = switch (event) {
            case OPENED ->
                    "A support ticket has been opened for your conversation.\n\n"
                    + "  Ticket:  #" + ticket.getId() + "\n"
                    + "  Subject: " + ticket.getSubject() + "\n"
                    + "  Status:  " + status + "\n\n"
                    + "Our team will follow up shortly. You can reply in the chat at any time.";
            case UPDATED ->
                    "Your support ticket has been updated.\n\n"
                    + "  Ticket:  #" + ticket.getId() + "\n"
                    + "  Subject: " + ticket.getSubject() + "\n"
                    + "  Status:  " + status + "\n\n"
                    + (ticket.getAssignedAgent() == null
                            ? ""
                            : "  Assigned agent: " + ticket.getAssignedAgent() + "\n\n")
                    + "A human agent is now handling your conversation.";
            case RESOLVED ->
                    "Great news — your support ticket has been resolved.\n\n"
                    + "  Ticket:  #" + ticket.getId() + "\n"
                    + "  Subject: " + ticket.getSubject() + "\n"
                    + "  Status:  RESOLVED\n\n"
                    + "If your issue is not fully addressed, start a new chat and we will be happy to help.";
        };
        return body + "\n\n— CODAFRIQA Customer Support";
    }

    private String htmlFor(TicketEvent event, SupportTicket ticket) {
        String status = ticket.getStatus() == null ? "OPEN" : ticket.getStatus();
        String headline = switch (event) {
            case OPENED -> "Your support ticket has been opened";
            case UPDATED -> "Your support ticket has been updated";
            case RESOLVED -> "Your support ticket has been resolved";
        };
        String assigned = ticket.getAssignedAgent() == null
                ? ""
                : "<p><strong>Assigned agent:</strong> " + escape(ticket.getAssignedAgent()) + "</p>";
        return "<div style=\"font-family:Arial,sans-serif;max-width:520px;margin:0 auto;color:#1e293b\">"
                + "<div style=\"background:#0f172a;color:#fff;padding:16px 20px;border-radius:8px 8px 0 0\">"
                + "<strong>CODAFRIQA Customer Support</strong></div>"
                + "<div style=\"border:1px solid #e2e8f0;border-top:none;padding:20px;border-radius:0 0 8px 8px\">"
                + "<h2 style=\"margin:0 0 12px;font-size:18px\">" + headline + "</h2>"
                + "<p><strong>Ticket:</strong> #" + ticket.getId() + "</p>"
                + "<p><strong>Subject:</strong> " + escape(ticket.getSubject()) + "</p>"
                + "<p><strong>Status:</strong> " + escape(status) + "</p>"
                + assigned
                + "<p style=\"margin-top:16px;color:#64748b;font-size:13px\">You can reply in the chat at any time.</p>"
                + "</div></div>";
    }

    private static String escape(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
