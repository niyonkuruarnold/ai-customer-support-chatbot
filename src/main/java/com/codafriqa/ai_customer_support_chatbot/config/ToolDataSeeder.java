package com.codafriqa.ai_customer_support_chatbot.config;

import com.codafriqa.ai_customer_support_chatbot.model.Tool;
import com.codafriqa.ai_customer_support_chatbot.model.ToolStatus;
import com.codafriqa.ai_customer_support_chatbot.model.User;
import com.codafriqa.ai_customer_support_chatbot.model.UserRole;
import com.codafriqa.ai_customer_support_chatbot.repository.ToolRepository;
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
 * Boot-time seeder for the System Indexer (Owner Dashboard).
 *
 * <p>Runs {@link ApplicationRunner} with {@link Ordered#LOWEST_PRECEDENCE}
 * so it executes <strong>after</strong> all other runners — by which time
 * Hibernate {@code ddl-auto=update} has created or migrated every table.
 *
 * <p>If the {@code tools} table is empty, inserts 9 sample tools with
 * realistic statuses so the metric cards on the System Indexer dashboard
 * show non-zero values:
 * <ul>
 *   <li>4 {@link ToolStatus#AVAILABLE} tools</li>
 *   <li>3 {@link ToolStatus#BORROWED} tools</li>
 *   <li>2 {@link ToolStatus#IN_MAINTENANCE} tools</li>
 * </ul>
 *
 * <p>Includes a retry loop (3 attempts, 2-second delay) to handle the
 * case where Hibernate DDL hasn't finished when the runner fires.
 *
 * <p>Idempotent: no-ops when tools already exist. Seeding failures are
 * logged at ERROR level and never prevent the app from starting.
 */
@Component
@Order(Ordered.LOWEST_PRECEDENCE)
public class ToolDataSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(ToolDataSeeder.class);

    /** Email that matches the in-memory admin in {@code SecurityConfig}. */
    private static final String ADMIN_EMAIL = "admin";

    /** Maximum number of attempts to seed if the DB isn't ready yet. */
    private static final int MAX_RETRIES = 3;

    /** Milliseconds to wait between retry attempts. */
    private static final long RETRY_DELAY_MS = 2000;

    private final UserRepository userRepository;
    private final ToolRepository toolRepository;
    private final BCryptPasswordEncoder passwordEncoder;

    public ToolDataSeeder(UserRepository userRepository,
                          ToolRepository toolRepository) {
        this.userRepository = userRepository;
        this.toolRepository = toolRepository;
        this.passwordEncoder = new BCryptPasswordEncoder();
    }

    @Override
    public void run(ApplicationArguments args) {
        log.info("ToolDataSeeder: ApplicationRunner fired — checking if seeding is needed");

        for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
            try {
                Long adminId = ensureAdminUser();
                int seeded = seedSampleTools(adminId);
                if (seeded > 0) {
                    log.info("ToolDataSeeder: SUCCESS — seeded {} tools into the database (AVAILABLE={}, BORROWED={}, IN_MAINTENANCE={})",
                            seeded,
                            countByStatus(seeded, ToolStatus.AVAILABLE),
                            countByStatus(seeded, ToolStatus.BORROWED),
                            countByStatus(seeded, ToolStatus.IN_MAINTENANCE));
                } else {
                    log.info("ToolDataSeeder: tools table already has {} rows — skipping seed", toolRepository.count());
                }
                return; // Success — exit retry loop
            } catch (Exception e) {
                if (attempt < MAX_RETRIES) {
                    log.warn("ToolDataSeeder: attempt {}/{} failed ({}: {}) — retrying in {}ms",
                            attempt, MAX_RETRIES,
                            e.getClass().getSimpleName(), e.getMessage(),
                            RETRY_DELAY_MS);
                    sleep(RETRY_DELAY_MS);
                } else {
                    log.error("ToolDataSeeder: FAILED after {} attempts — tools table will be empty on the dashboard. " +
                                    "Last error: {}: {}",
                            MAX_RETRIES, e.getClass().getSimpleName(), e.getMessage(), e);
                }
            }
        }
    }

    // ------------------------------------------------------------------
    // Admin user
    // ------------------------------------------------------------------

    /**
     * Make sure an ADMIN user exists in the database. This gives the
     * in-memory Spring Security admin account a real DB row with an ID
     * that can be used as {@code ownerId} for seeded tools.
     *
     * @return the admin user's database ID
     */
    private Long ensureAdminUser() {
        return userRepository.findByEmail(ADMIN_EMAIL)
                .orElseGet(() -> {
                    log.info("ToolDataSeeder: creating database admin user (email={})", ADMIN_EMAIL);
                    User admin = new User(
                            ADMIN_EMAIL,
                            passwordEncoder.encode("admin123"),
                            UserRole.ADMIN);
                    return userRepository.save(admin);
                }).getId();
    }

    // ------------------------------------------------------------------
    // Sample tools
    // ------------------------------------------------------------------

    /**
     * Seed sample tools when the table is empty. Each tool is assigned to
     * the admin user so the System Indexer dashboard (which filters by
     * {@code ownerId}) can display them.
     *
     * @return the number of tools seeded (0 if the table was already populated)
     */
    @Transactional
    private int seedSampleTools(Long adminId) {
        long existingCount = toolRepository.count();
        if (existingCount > 0) {
            return 0;
        }

        log.info("ToolDataSeeder: inserting 9 sample tools into the tools table");

        var seeds = new Seed[]{
                // ── AVAILABLE (4 tools) ──
                new Seed("Multimeter Unit A",
                        "Fluke 87V industrial multimeter for electrical diagnostics",
                        "Electrical Testing", ToolStatus.AVAILABLE),
                new Seed("Diagnostic Scanner B",
                        "OBD-II automotive diagnostic scanner with live data",
                        "Automotive", ToolStatus.AVAILABLE),
                new Seed("Oscilloscope Pro",
                        "Rigol DS1054Z 4-channel 50 MHz digital oscilloscope",
                        "Electronics", ToolStatus.AVAILABLE),
                new Seed("Thermal Camera C",
                        "FLIR E8 thermal imaging camera for heat mapping",
                        "Inspection", ToolStatus.AVAILABLE),

                // ── BORROWED (3 tools) ──
                new Seed("Hydraulic Jack",
                        "3-ton heavy-duty hydraulic floor jack",
                        "Lifting Equipment", ToolStatus.BORROWED),
                new Seed("Torque Wrench Set",
                        "1/2-inch drive click-type torque wrench set (10-150 ft-lbs)",
                        "Hand Tools", ToolStatus.BORROWED),
                new Seed("Battery Tester",
                        "Foxwell BT705 12V/24V battery load tester and charger",
                        "Electrical Testing", ToolStatus.BORROWED),

                // ── IN_MAINTENANCE (2 tools) ──
                new Seed("Soldering Station",
                        "Hakko FX-888D digital soldering station — tip replacement",
                        "Electronics", ToolStatus.IN_MAINTENANCE),
                new Seed("Calibration Kit",
                        "Fluke 5520A multi-product calibrator — annual calibration",
                        "Calibration", ToolStatus.IN_MAINTENANCE),
        };

        int availableCount = 0;
        int borrowedCount = 0;
        int maintenanceCount = 0;

        for (Seed s : seeds) {
            Tool tool = new Tool(s.name(), s.desc(), s.category(), adminId);
            tool.setStatus(s.status());
            toolRepository.save(tool);

            switch (s.status()) {
                case AVAILABLE -> availableCount++;
                case BORROWED -> borrowedCount++;
                case IN_MAINTENANCE -> maintenanceCount++;
            }
        }

        log.info("ToolDataSeeder: tool seeding complete — {} tools inserted (AVAILABLE={}, BORROWED={}, IN_MAINTENANCE={})",
                seeds.length, availableCount, borrowedCount, maintenanceCount);

        return seeds.length;
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private static int countByStatus(int total, ToolStatus status) {
        // Count from the known seed data breakdown
        return switch (status) {
            case AVAILABLE -> 4;
            case BORROWED -> 3;
            case IN_MAINTENANCE -> 2;
        };
    }

    private static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /** Local seed record used only during boot-time seeding. */
    private record Seed(String name, String desc, String category, ToolStatus status) {}
}
