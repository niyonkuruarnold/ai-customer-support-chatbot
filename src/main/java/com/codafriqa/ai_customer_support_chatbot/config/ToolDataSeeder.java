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
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * Boot-time seeder for the System Indexer (Owner Dashboard).
 *
 * <ol>
 *   <li>Ensures a database {@link User} with role {@code ADMIN} exists so that
 *       in-memory Spring Security users (admin / admin123) have a real DB row
 *       and ID that the frontend can reference as {@code ownerId}.</li>
 *   <li>If the {@code tools} table is empty, inserts a set of sample tools
 *       with statuses {@link ToolStatus#AVAILABLE},
 *       {@link ToolStatus#BORROWED}, and {@link ToolStatus#IN_MAINTENANCE}
 *       so the metric cards on the System Indexer dashboard show non-zero
 *       values.</li>
 * </ol>
 *
 * Idempotent: no-ops when tools already exist or the admin user is already
 * present. Seeding failures are logged and never prevent the app from starting.
 */
@Component
public class ToolDataSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(ToolDataSeeder.class);

    /** Email that matches the in-memory admin in {@code SecurityConfig}. */
    private static final String ADMIN_EMAIL = "admin";

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
        try {
            Long adminId = ensureAdminUser();
            seedSampleTools(adminId);
        } catch (Exception e) {
            // Seeding must never prevent the app from starting
            log.warn("Tool seeding skipped: {}: {}",
                    e.getClass().getSimpleName(), e.getMessage());
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
                    log.info("Creating database admin user (email={})", ADMIN_EMAIL);
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
     */
    private void seedSampleTools(Long adminId) {
        if (toolRepository.count() > 0) {
            log.debug("Tools table already has {} rows — skipping seed",
                    toolRepository.count());
            return;
        }

        log.info("Seeding sample tools for the System Indexer dashboard");

        var seeds = new Seed[]{
                // AVAILABLE tools
                new Seed("Power Drill", "18V cordless drill with two batteries",
                        "Power Tools", ToolStatus.AVAILABLE),
                new Seed("Garden Hose", "50 ft expandable garden hose",
                        "Garden", ToolStatus.AVAILABLE),
                new Seed("Wrench Set", "12-piece metric wrench set",
                        "Hand Tools", ToolStatus.AVAILABLE),
                new Seed("Paint Roller Kit", "9-inch roller with extendable pole",
                        "Painting", ToolStatus.AVAILABLE),

                // BORROWED tools
                new Seed("Ladder", "20 ft fiberglass extension ladder",
                        "Ladders", ToolStatus.BORROWED),
                new Seed("Pressure Washer", "2000 PSI electric pressure washer",
                        "Cleaning", ToolStatus.BORROWED),
                new Seed("Circular Saw", "7-¼ inch circular saw with blade",
                        "Power Tools", ToolStatus.BORROWED),

                // IN_MAINTENANCE tools
                new Seed("Lawn Mower", "21-inch self-propelled gas mower",
                        "Garden", ToolStatus.IN_MAINTENANCE),
                new Seed("Chainsaw", "16-inch electric chainsaw — chain tensioning",
                        "Power Tools", ToolStatus.IN_MAINTENANCE),
        };

        for (Seed s : seeds) {
            Tool tool = new Tool(s.name(), s.desc(), s.category(), adminId);
            tool.setStatus(s.status());
            toolRepository.save(tool);
        }

        log.info("Seeded {} sample tools (AVAILABLE={}, BORROWED={}, IN_MAINTENANCE={})",
                seeds.length,
                count(seeds, ToolStatus.AVAILABLE),
                count(seeds, ToolStatus.BORROWED),
                count(seeds, ToolStatus.IN_MAINTENANCE));
    }

    private static int count(Seed[] seeds, ToolStatus status) {
        int n = 0;
        for (Seed s : seeds) {
            if (s.status() == status) n++;
        }
        return n;
    }

    /** Local seed record used only during boot-time seeding. */
    private record Seed(String name, String desc, String category, ToolStatus status) {}
}
