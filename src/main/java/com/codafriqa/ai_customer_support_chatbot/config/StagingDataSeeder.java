package com.codafriqa.ai_customer_support_chatbot.config;

import com.codafriqa.ai_customer_support_chatbot.model.*;
import com.codafriqa.ai_customer_support_chatbot.repository.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Multi-role staging data seeder (Sections 14 & 15).
 *
 * <p>Creates default seed accounts with BCrypt-hashed passwords for testing:
 * <ul>
 *   <li>System Administrator — admin@codafriqa.local (ROLE_ADMIN)</li>
 *   <li>Support Manager — manager@codafriqa.local (ROLE_MANAGER)</li>
 *   <li>Support Agent — agent@codafriqa.local (ROLE_AGENT)</li>
 *   <li>Knowledge Editor — editor@codafriqa.local (ROLE_EDITOR)</li>
 *   <li>Customer — customer@codafriqa.local (ROLE_CUSTOMER)</li>
 * </ul>
 *
 * <p>Also seeds sample knowledge base articles with realistic FAQ content
 * for vector search / RAG testing, sample conversations, and open tickets.
 *
 * <p>Idempotent: no-ops when users already exist. Runs with
 * {@link Ordered#LOWEST_PRECEDENCE} to execute after all other runners
 * (i.e. after Hibernate DDL has created every table).
 *
 * <p>Includes a retry loop (3 attempts, 2-second delay) to handle the
 * case where Hibernate DDL hasn't finished when the runner fires.
 * Seeding failures are logged at ERROR level and never prevent the
 * application from starting.
 */
@Component
@Order(Ordered.LOWEST_PRECEDENCE)
public class StagingDataSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(StagingDataSeeder.class);

    /** Maximum number of attempts to seed if the DB isn't ready yet. */
    private static final int MAX_RETRIES = 3;

    /** Milliseconds to wait between retry attempts. */
    private static final long RETRY_DELAY_MS = 2000;

    /** Default password for all seed accounts (BCrypt hashed). */
    private static final String DEFAULT_PASSWORD = "Password123!";

    private final UserRepository userRepository;
    private final KnowledgeDocumentRepository knowledgeDocumentRepository;
    private final KnowledgeChunkRepository knowledgeChunkRepository;
    private final ChatSessionRepository chatSessionRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final ChatFeedbackRepository chatFeedbackRepository;
    private final ConversationRepository conversationRepository;
    private final SupportTicketRepository supportTicketRepository;
    private final BCryptPasswordEncoder passwordEncoder;

    public StagingDataSeeder(UserRepository userRepository,
                             KnowledgeDocumentRepository knowledgeDocumentRepository,
                             KnowledgeChunkRepository knowledgeChunkRepository,
                             ChatSessionRepository chatSessionRepository,
                             ChatMessageRepository chatMessageRepository,
                             ChatFeedbackRepository chatFeedbackRepository,
                             ConversationRepository conversationRepository,
                             SupportTicketRepository supportTicketRepository) {
        this.userRepository = userRepository;
        this.knowledgeDocumentRepository = knowledgeDocumentRepository;
        this.knowledgeChunkRepository = knowledgeChunkRepository;
        this.chatSessionRepository = chatSessionRepository;
        this.chatMessageRepository = chatMessageRepository;
        this.chatFeedbackRepository = chatFeedbackRepository;
        this.conversationRepository = conversationRepository;
        this.supportTicketRepository = supportTicketRepository;
        this.passwordEncoder = new BCryptPasswordEncoder();
    }

    @Override
    public void run(ApplicationArguments args) {
        log.info("StagingDataSeeder: ApplicationRunner fired — checking if seeding is needed");

        for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
            try {
                int usersSeeded = seedUsers();
                int kbSeeded = seedKnowledgeBase();
                int convSeeded = seedConversations();
                int ticketSeeded = seedTickets();

                log.info("StagingDataSeeder: SUCCESS — seeded {} users, {} KB articles, {} conversations, {} tickets",
                        usersSeeded, kbSeeded, convSeeded, ticketSeeded);
                return;
            } catch (Exception e) {
                if (attempt < MAX_RETRIES) {
                    log.warn("StagingDataSeeder: attempt {}/{} failed ({}: {}) — retrying in {}ms",
                            attempt, MAX_RETRIES,
                            e.getClass().getSimpleName(), e.getMessage(), RETRY_DELAY_MS);
                    sleep(RETRY_DELAY_MS);
                } else {
                    log.error("StagingDataSeeder: FAILED after {} attempts — last error: {}: {}",
                            MAX_RETRIES, e.getClass().getSimpleName(), e.getMessage(), e);
                }
            }
        }
    }

    // ─── User Seeding ─────────────────────────────────────────────────

    /**
     * Seed all test users with BCrypt-hashed passwords.
     * Returns the number of users seeded (0 if already populated).
     */
    @Transactional
    private int seedUsers() {
        long existingCount = userRepository.count();
        if (existingCount > 0) {
            log.info("StagingDataSeeder: users table already has {} rows — skipping user seed", existingCount);
            return 0;
        }

        log.info("StagingDataSeeder: inserting seed users into the users table");

        String hashedPassword = passwordEncoder.encode(DEFAULT_PASSWORD);

        List<User> users = List.of(
                new User("admin@codafriqa.local", hashedPassword, UserRole.ADMIN),
                new User("manager@codafriqa.local", hashedPassword, UserRole.MANAGER),
                new User("agent@codafriqa.local", hashedPassword, UserRole.AGENT),
                new User("editor@codafriqa.local", hashedPassword, UserRole.EDITOR),
                new User("customer@codafriqa.local", hashedPassword, UserRole.CUSTOMER)
        );

        int count = 0;
        for (User user : users) {
            try {
                userRepository.save(user);
                count++;
                log.debug("StagingDataSeeder: created user {} with role {}", user.getEmail(), user.getRole());
            } catch (Exception e) {
                log.warn("StagingDataSeeder: failed to create user {}: {}", user.getEmail(), e.getMessage());
            }
        }

        log.info("StagingDataSeeder: user seeding complete — {} users inserted", count);
        return count;
    }

    // ─── Knowledge Base Seeding ───────────────────────────────────────

    /**
     * Seed sample knowledge base articles with realistic FAQ content
     * for vector search / RAG testing.
     * Returns the number of documents seeded (0 if already populated).
     */
    @Transactional
    private int seedKnowledgeBase() {
        long existingCount = knowledgeDocumentRepository.count();
        if (existingCount > 0) {
            log.info("StagingDataSeeder: knowledge_documents table already has {} rows — skipping KB seed", existingCount);
            return 0;
        }

        log.info("StagingDataSeeder: inserting sample knowledge base articles");

        int docCount = 0;

        // Document 1: Shipping & Delivery FAQ
        KnowledgeDocument shippingDoc = knowledgeDocumentRepository.save(
                new KnowledgeDocument("Shipping & Delivery FAQ", "TEXT", null));
        String[] shippingChunks = {
                "How long does shipping take? Standard shipping within Rwanda takes 2-3 business days. International shipping takes 7-14 business days depending on the destination country.",
                "How can I track my order? You can track your order using the tracking number sent to your email after shipment. Visit our tracking page at codafriqa.local/track and enter your tracking number.",
                "Do you offer free shipping? Yes, we offer free standard shipping on orders over 50,000 RWF within Rwanda. International shipping rates vary by destination.",
                "What is your return policy? We accept returns within 30 days of delivery. Items must be unused and in original packaging. Contact support to initiate a return.",
                "Can I change my shipping address after ordering? You can change your shipping address within 2 hours of placing an order by contacting our support team. After that, the order may have already been processed."
        };
        for (int i = 0; i < shippingChunks.length; i++) {
            knowledgeChunkRepository.save(new KnowledgeChunk(shippingDoc.getId(), i, shippingChunks[i]));
        }
        docCount++;

        // Document 2: Product Warranty & Returns
        KnowledgeDocument warrantyDoc = knowledgeDocumentRepository.save(
                new KnowledgeDocument("Product Warranty & Returns Policy", "TEXT", null));
        String[] warrantyChunks = {
                "All products come with a 1-year manufacturer warranty covering defects in materials and workmanship. This does not cover normal wear and tear or accidental damage.",
                "To file a warranty claim, contact our support team with your order number and a description of the issue. We may request photos of the defect.",
                "Refunds are processed within 5-7 business days after we receive the returned item. Refunds are issued to the original payment method.",
                "Exchange requests can be made within 30 days of delivery. If the replacement item is out of stock, we will offer a full refund or store credit.",
                "Products damaged during shipping should be reported within 48 hours of delivery. We will arrange a free pickup and send a replacement at no extra cost."
        };
        for (int i = 0; i < warrantyChunks.length; i++) {
            knowledgeChunkRepository.save(new KnowledgeChunk(warrantyDoc.getId(), i, warrantyChunks[i]));
        }
        docCount++;

        // Document 3: Account & Billing
        KnowledgeDocument billingDoc = knowledgeDocumentRepository.save(
                new KnowledgeDocument("Account & Billing Support", "TEXT", null));
        String[] billingChunks = {
                "How do I create an account? Visit codafriqa.local/register and fill in your email, name, and password. You will receive a verification email to activate your account.",
                "I forgot my password. Click 'Forgot Password' on the login page and enter your email. You will receive a password reset link valid for 24 hours.",
                "What payment methods do we accept? We accept Mobile Money (MTN, Airtel), Visa, Mastercard, and bank transfers. All transactions are secured with 256-bit encryption.",
                "How do I update my billing information? Go to Account Settings > Billing and update your payment method or billing address. Changes take effect immediately.",
                "Can I get an invoice for my order? Yes, invoices are automatically sent to your email after each purchase. You can also download invoices from your Order History page."
        };
        for (int i = 0; i < billingChunks.length; i++) {
            knowledgeChunkRepository.save(new KnowledgeChunk(billingDoc.getId(), i, billingChunks[i]));
        }
        docCount++;

        // Document 4: Technical Support
        KnowledgeDocument techDoc = knowledgeDocumentRepository.save(
                new KnowledgeDocument("Technical Support Guide", "TEXT", null));
        String[] techChunks = {
                "If you are experiencing issues with the mobile app, try these steps: 1) Force close and reopen the app. 2) Clear the app cache. 3) Uninstall and reinstall the latest version.",
                "Browser compatibility: We support Chrome 90+, Firefox 88+, Safari 14+, and Edge 90+. Clear your browser cache if you experience display issues.",
                "Two-factor authentication (2FA) is available in Account Settings > Security. We recommend enabling 2FA for all admin and agent accounts.",
                "API rate limits: The public API allows 100 requests per minute per API key. Enterprise plans have higher limits. Contact support to request a limit increase.",
                "If your account is locked, it may be due to 5 failed login attempts. Wait 15 minutes and try again, or contact support to unlock your account immediately."
        };
        for (int i = 0; i < techChunks.length; i++) {
            knowledgeChunkRepository.save(new KnowledgeChunk(techDoc.getId(), i, techChunks[i]));
        }
        docCount++;

        log.info("StagingDataSeeder: KB seeding complete — {} documents with {} total chunks inserted",
                docCount, knowledgeChunkRepository.count());
        return docCount;
    }

    // ─── Conversation Seeding ─────────────────────────────────────────

    /**
     * Seed sample conversations with chat messages and feedback.
     * Returns the number of conversations seeded (0 if already populated).
     */
    @Transactional
    private int seedConversations() {
        long existingCount = conversationRepository.count();
        if (existingCount > 0) {
            log.info("StagingDataSeeder: conversations table already has {} rows — skipping conversation seed", existingCount);
            return 0;
        }

        // Ensure we have at least a customer and agent user
        User customer = userRepository.findByEmail("customer@codafriqa.local")
                .orElseGet(() -> userRepository.save(
                        new User("customer@codafriqa.local",
                                passwordEncoder.encode(DEFAULT_PASSWORD),
                                UserRole.CUSTOMER)));
        User agent = userRepository.findByEmail("agent@codafriqa.local")
                .orElseGet(() -> userRepository.save(
                        new User("agent@codafriqa.local",
                                passwordEncoder.encode(DEFAULT_PASSWORD),
                                UserRole.AGENT)));

        log.info("StagingDataSeeder: inserting sample conversations");

        int convCount = 0;

        // Conversation 1: AI-only resolved (no escalation)
        ChatSession session1 = chatSessionRepository.save(new ChatSession(customer.getId()));
        session1.setStatus("CLOSED");
        chatSessionRepository.save(session1);

        chatMessageRepository.save(new ChatMessage(session1.getId(), "USER", "Hi, I need help with my recent order #ORD-2024-1234. It hasn't arrived yet."));
        chatMessageRepository.save(new ChatMessage(session1.getId(), "AI", "I'd be happy to help you with your order. Let me look up order #ORD-2024-1234. According to our records, your order was shipped on January 15th and is currently in transit. The estimated delivery date is January 18th."));
        chatMessageRepository.save(new ChatMessage(session1.getId(), "USER", "That's 3 days from now. Can I get an update on the tracking?"));
        chatMessageRepository.save(new ChatMessage(session1.getId(), "AI", "Your tracking number is RW-789012345. You can track it at codafriqa.local/track. The package is currently at the Kigali distribution center and will be delivered to your address tomorrow."));
        chatMessageRepository.save(new ChatMessage(session1.getId(), "USER", "Thank you, that's helpful!"));

        Conversation conv1 = conversationRepository.save(
                new Conversation(session1.getId(), customer.getId(), null));
        conv1.setStatus(ConversationStatus.CLOSED);
        conv1.setCsatScore(5);
        conv1.setCsatComment("Quick and helpful response!");
        conv1.setCsatSubmittedAt(java.time.LocalDateTime.now().minusDays(2));
        conversationRepository.save(conv1);

        chatFeedbackRepository.save(new ChatFeedback(session1.getId(), 5, "Quick and helpful response!"));
        convCount++;

        // Conversation 2: Escalated to agent
        ChatSession session2 = chatSessionRepository.save(new ChatSession(customer.getId()));
        session2.setStatus("ESCALATED");
        chatSessionRepository.save(session2);

        chatMessageRepository.save(new ChatMessage(session2.getId(), "USER", "I want to return a defective product but the return form keeps giving me an error."));
        chatMessageRepository.save(new ChatMessage(session2.getId(), "AI", "I'm sorry to hear about the issue with the return form. Let me help you with that. Could you tell me what error message you're seeing?"));
        chatMessageRepository.save(new ChatMessage(session2.getId(), "USER", "It says 'Invalid order number' but I'm entering the correct number from my email confirmation."));
        chatMessageRepository.save(new ChatMessage(session2.getId(), "AI", "This could be a system issue. Let me connect you with a human agent who can process your return directly. They'll have access to more tools to resolve this."));
        chatMessageRepository.save(new ChatMessage(session2.getId(), "AGENT", "Hello! I can see the issue with your return request. The order number format in our system has changed. Let me process this manually for you. Your order #ORD-2024-0891 is eligible for return. I've initiated the return process and you'll receive a prepaid shipping label via email within the hour."));

        Conversation conv2 = conversationRepository.save(
                new Conversation(session2.getId(), customer.getId(), agent.getId()));
        conv2.setStatus(ConversationStatus.CONNECTED_TO_AGENT);
        conv2.setEscalationSummary("Customer unable to submit return form due to order number validation error. Agent processed return manually.");
        conversationRepository.save(conv2);
        convCount++;

        // Conversation 3: Active AI conversation
        ChatSession session3 = chatSessionRepository.save(new ChatSession(customer.getId()));
        session3.setStatus("ACTIVE");
        chatSessionRepository.save(session3);

        chatMessageRepository.save(new ChatMessage(session3.getId(), "USER", "Do you have the XYZ Router in stock? I need it for my office setup."));
        chatMessageRepository.save(new ChatMessage(session3.getId(), "AI", "Let me check our inventory for the XYZ Router. Yes, we have it in stock! It's available for 125,000 RWF. Would you like me to help you place an order?"));

        Conversation conv3 = conversationRepository.save(
                new Conversation(session3.getId(), customer.getId(), null));
        conv3.setStatus(ConversationStatus.AI_ASSISTANT);
        conversationRepository.save(conv3);
        convCount++;

        log.info("StagingDataSeeder: conversation seeding complete — {} conversations inserted", convCount);
        return convCount;
    }

    // ─── Ticket Seeding ───────────────────────────────────────────────

    /**
     * Seed sample support tickets in various lifecycle states.
     * Returns the number of tickets seeded (0 if already populated).
     */
    @Transactional
    private int seedTickets() {
        long existingCount = supportTicketRepository.count();
        if (existingCount > 0) {
            log.info("StagingDataSeeder: support_tickets table already has {} rows — skipping ticket seed", existingCount);
            return 0;
        }

        // Ensure users exist
        User customer = userRepository.findByEmail("customer@codafriqa.local")
                .orElseGet(() -> userRepository.save(
                        new User("customer@codafriqa.local",
                                passwordEncoder.encode(DEFAULT_PASSWORD),
                                UserRole.CUSTOMER)));
        User agent = userRepository.findByEmail("agent@codafriqa.local")
                .orElseGet(() -> userRepository.save(
                        new User("agent@codafriqa.local",
                                passwordEncoder.encode(DEFAULT_PASSWORD),
                                UserRole.AGENT)));

        // Get or create sessions for tickets
        List<ChatSession> sessions = chatSessionRepository.findByUserId(customer.getId());

        log.info("StagingDataSeeder: inserting sample support tickets");

        int ticketCount = 0;

        // Ticket 1: OPEN — billing inquiry, assigned to agent
        ChatSession tSession1 = sessions.isEmpty()
                ? chatSessionRepository.save(new ChatSession(customer.getId()))
                : sessions.get(0);
        SupportTicket ticket1 = new SupportTicket(customer.getId(), tSession1.getId(),
                "Billing discrepancy on last invoice",
                "I was charged 75,000 RWF but my order total was 65,000 RWF. Please investigate the extra 10,000 RWF charge on my account.");
        ticket1.setStatus(TicketStatus.OPEN);
        ticket1.setPriority(TicketPriority.HIGH);
        ticket1.setCategory("BILLING");
        ticket1.setAssignedAgent("agent@codafriqa.local");
        ticket1.setAssignedAgentId(agent.getId());
        ticket1.setSentiment("negative");
        supportTicketRepository.save(ticket1);
        ticketCount++;

        // Ticket 2: PENDING_CUSTOMER — waiting for customer response
        ChatSession tSession2 = sessions.size() > 1
                ? sessions.get(1)
                : chatSessionRepository.save(new ChatSession(customer.getId()));
        SupportTicket ticket2 = new SupportTicket(customer.getId(), tSession2.getId(),
                "Product not as described",
                "The product I received looks different from the photos on the website. The color is wrong and the size is smaller than expected.");
        ticket2.setStatus(TicketStatus.PENDING_CUSTOMER);
        ticket2.setPriority(TicketPriority.MEDIUM);
        ticket2.setCategory("TECHNICAL");
        ticket2.setAssignedAgent("agent@codafriqa.local");
        ticket2.setAssignedAgentId(agent.getId());
        ticket2.setSentiment("neutral");
        ticket2.setAiSummary("- Customer received wrong color/size product\n- Requesting exchange or refund\n- Order #ORD-2024-0567");
        supportTicketRepository.save(ticket2);
        ticketCount++;

        // Ticket 3: RESOLVED — issue fixed
        ChatSession tSession3 = sessions.size() > 2
                ? sessions.get(2)
                : chatSessionRepository.save(new ChatSession(customer.getId()));
        SupportTicket ticket3 = new SupportTicket(customer.getId(), tSession3.getId(),
                "Cannot access my account",
                "I'm locked out of my account after too many failed login attempts. I need to access my order history.");
        ticket3.setStatus(TicketStatus.RESOLVED);
        ticket3.setPriority(TicketPriority.LOW);
        ticket3.setCategory("GENERAL");
        ticket3.setAssignedAgent("agent@codafriqa.local");
        ticket3.setAssignedAgentId(agent.getId());
        ticket3.setSentiment("positive");
        supportTicketRepository.save(ticket3);
        ticketCount++;

        // Ticket 4: NEW — just created, unassigned
        ChatSession tSession4 = chatSessionRepository.save(new ChatSession(customer.getId()));
        SupportTicket ticket4 = new SupportTicket(customer.getId(), tSession4.getId(),
                "Request for bulk order discount",
                "I'm looking to order 50 units of the XYZ Router for my company. Do you offer bulk pricing?");
        ticket4.setStatus(TicketStatus.NEW);
        ticket4.setPriority(TicketPriority.MEDIUM);
        ticket4.setCategory("GENERAL");
        ticket4.setSentiment("neutral");
        supportTicketRepository.save(ticket4);
        ticketCount++;

        // Ticket 5: CLOSED — fully resolved and closed
        ChatSession tSession5 = chatSessionRepository.save(new ChatSession(customer.getId()));
        SupportTicket ticket5 = new SupportTicket(customer.getId(), tSession5.getId(),
                "Shipping delay complaint",
                "My order was supposed to arrive 5 days ago but I still haven't received it. The tracking shows it's stuck in customs.");
        ticket5.setStatus(TicketStatus.CLOSED);
        ticket5.setPriority(TicketPriority.URGENT);
        ticket5.setCategory("BILLING");
        ticket5.setAssignedAgent("agent@codafriqa.local");
        ticket5.setAssignedAgentId(agent.getId());
        ticket5.setSentiment("positive");
        ticket5.setClosedAt(java.time.LocalDateTime.now().minusDays(1));
        ticket5.setInternalNotes(List.of(
                "Customs clearance completed on Jan 16",
                "Package delivered to customer on Jan 17",
                "Customer confirmed receipt - no further action needed"
        ));
        supportTicketRepository.save(ticket5);
        ticketCount++;

        // Ticket 6: PENDING_INTERNAL — waiting on internal team
        ChatSession tSession6 = chatSessionRepository.save(new ChatSession(customer.getId()));
        SupportTicket ticket6 = new SupportTicket(customer.getId(), tSession6.getId(),
                "Refund not processed after 10 days",
                "I was promised a refund 10 days ago but it still hasn't appeared in my account. Order #ORD-2024-0312.");
        ticket6.setStatus(TicketStatus.PENDING_INTERNAL);
        ticket6.setPriority(TicketPriority.HIGH);
        ticket6.setCategory("BILLING");
        ticket6.setAssignedAgent("agent@codafriqa.local");
        ticket6.setAssignedAgentId(agent.getId());
        ticket6.setSentiment("negative");
        ticket6.setAiSummary("- Customer waiting 10 days for refund\n- Order #ORD-2024-0312\n- Finance team needs to verify refund status");
        supportTicketRepository.save(ticket6);
        ticketCount++;

        log.info("StagingDataSeeder: ticket seeding complete — {} tickets inserted across {} statuses",
                ticketCount, 6);
        return ticketCount;
    }

    private static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
