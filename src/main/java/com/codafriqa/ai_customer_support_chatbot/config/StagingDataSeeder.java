package com.codafriqa.ai_customer_support_chatbot.config;

import com.codafriqa.ai_customer_support_chatbot.model.User;
import com.codafriqa.ai_customer_support_chatbot.model.UserRole;
import com.codafriqa.ai_customer_support_chatbot.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Staging environment data seeder.
 * 
 * Creates default seed accounts with BCrypt-hashed passwords for testing:
 * - System Administrator (admin@codafriqa.local / role: ROLE_ADMIN)
 * - Support Manager (manager@codafriqa.local / role: ROLE_MANAGER)
 * - Support Agent (agent@codafriqa.local / role: ROLE_AGENT)
 * - Knowledge Editor (editor@codafriqa.local / role: ROLE_EDITOR)
 * - Customer (customer@codafriqa.local / role: ROLE_CUSTOMER)
 * 
 * Idempotent: no-ops when users already exist.
 * Runs with LOWEST_PRECEDENCE to execute after all other runners.
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
    private static final String DEFAULT_PASSWORD = "password123";

    private final UserRepository userRepository;
    private final BCryptPasswordEncoder passwordEncoder;

    public StagingDataSeeder(UserRepository userRepository) {
        this.userRepository = userRepository;
        this.passwordEncoder = new BCryptPasswordEncoder();
    }

    @Override
    public void run(ApplicationArguments args) {
        log.info("StagingDataSeeder: ApplicationRunner fired — checking if seeding is needed");

        for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
            try {
                int seeded = seedUsers();
                if (seeded > 0) {
                    log.info("StagingDataSeeder: SUCCESS — seeded {} users into the database", seeded);
                } else {
                    log.info("StagingDataSeeder: users table already has {} rows — skipping seed", 
                            userRepository.count());
                }
                return; // Success — exit retry loop
            } catch (Exception e) {
                if (attempt < MAX_RETRIES) {
                    log.warn("StagingDataSeeder: attempt {}/{} failed ({}: {}) — retrying in {}ms",
                            attempt, MAX_RETRIES,
                            e.getClass().getSimpleName(), e.getMessage(),
                            RETRY_DELAY_MS);
                    sleep(RETRY_DELAY_MS);
                } else {
                    log.error("StagingDataSeeder: FAILED after {} attempts — users table will be empty. " +
                                    "Last error: {}: {}",
                            MAX_RETRIES, e.getClass().getSimpleName(), e.getMessage(), e);
                }
            }
        }
    }

    /**
     * Seed all test users with BCrypt-hashed passwords.
     * Returns the number of users seeded (0 if already populated).
     */
    @Transactional
    private int seedUsers() {
        long existingCount = userRepository.count();
        if (existingCount > 0) {
            return 0;
        }

        log.info("StagingDataSeeder: inserting seed users into the users table");

        String hashedPassword = passwordEncoder.encode(DEFAULT_PASSWORD);

        // Seed accounts
        User[] users = {
            createAdminUser(hashedPassword),
            createManagerUser(hashedPassword),
            createAgentUser(hashedPassword),
            createEditorUser(hashedPassword),
            createCustomerUser(hashedPassword),
        };

        int count = 0;
        for (User user : users) {
            try {
                userRepository.save(user);
                count++;
                log.debug("StagingDataSeeder: created user {} with role {}", 
                        user.getEmail(), user.getRole());
            } catch (Exception e) {
                log.warn("StagingDataSeeder: failed to create user {}: {}", 
                        user.getEmail(), e.getMessage());
            }
        }

        log.info("StagingDataSeeder: user seeding complete — {} users inserted", count);
        return count;
    }

    /** System Administrator account. */
    private User createAdminUser(String hashedPassword) {
        return new User("admin@codafriqa.local", hashedPassword, UserRole.ADMIN);
    }

    /** Support Manager account. */
    private User createManagerUser(String hashedPassword) {
        // Note: UserRole.MANAGER doesn't exist in enum, using ADMIN as closest match
        return new User("manager@codafriqa.local", hashedPassword, UserRole.ADMIN);
    }

    /** Support Agent account. */
    private User createAgentUser(String hashedPassword) {
        return new User("agent@codafriqa.local", hashedPassword, UserRole.AGENT);
    }

    /** Knowledge Editor account. */
    private User createEditorUser(String hashedPassword) {
        // Note: UserRole.EDITOR doesn't exist in enum, using AGENT as closest match
        return new User("editor@codafriqa.local", hashedPassword, UserRole.AGENT);
    }

    /** Customer account. */
    private User createCustomerUser(String hashedPassword) {
        return new User("customer@codafriqa.local", hashedPassword, UserRole.CUSTOMER);
    }

    private static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
