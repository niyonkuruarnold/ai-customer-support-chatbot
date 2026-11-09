package com.codafriqa.ai_customer_support_chatbot.config;

import com.codafriqa.ai_customer_support_chatbot.model.User;
import com.codafriqa.ai_customer_support_chatbot.service.UserService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * Boot-time data seeding.
 *
 * The customer-facing chat has no registration, so every session is backed
 * by the anonymous customer account (see UserService.ensureAnonymousUser).
 * This runner makes sure that account exists from startup — otherwise the
 * agent workspace would have no contact details to show for escalated
 * tickets. Idempotent: no-op when the account already exists.
 */
@Component
public class DataInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DataInitializer.class);

    private final UserService userService;

    public DataInitializer(UserService userService) {
        this.userService = userService;
    }

    @Override
    public void run(ApplicationArguments args) {
        try {
            User customer = userService.ensureAnonymousUser();
            log.info("Anonymous customer account ready (id={}, email={}) for chat sessions",
                    customer.getId(), customer.getEmail());
        } catch (Exception e) {
            // Seeding must never prevent the app from starting
            log.warn("Could not seed the anonymous customer account: {}: {}",
                    e.getClass().getSimpleName(), e.getMessage());
        }
    }
}
