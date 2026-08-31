package com.codafriqa.ai_customer_support_chatbot;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Main Spring Boot application entry point
 */
@SpringBootApplication
@EnableScheduling
public class AiCustomerSupportChatbotApplication {

    private static final Logger log = LoggerFactory.getLogger(AiCustomerSupportChatbotApplication.class);

    public static void main(String[] args) {
        SpringApplication.run(AiCustomerSupportChatbotApplication.class, args);
    }

    /**
     * Startup check for the Gemini configuration. Logs only whether the key is
     * present (never the key itself) so a missing GEMINI_API_KEY shows up in
     * the logs instead of surfacing later as a chat fallback message.
     */
    @Bean
    public CommandLineRunner logGeminiConfiguration(
            @Value("${GEMINI_API_KEY:}") String apiKey) {
        return args -> {
            if (apiKey == null || apiKey.isBlank()) {
                log.warn("Gemini API key is NOT configured: GEMINI_API_KEY is missing or empty "
                        + "(set it in your terminal or in a local .env file, see .env.example). "
                        + "AI chat responses and knowledge base embeddings will use degraded fallbacks.");
            } else if (apiKey.startsWith("your-") || apiKey.contains("placeholder")) {
                log.warn("GEMINI_API_KEY appears to be a placeholder value ({}). "
                        + "Replace it with a real key from https://aistudio.google.com/apikey",
                        apiKey.substring(0, Math.min(10, apiKey.length())) + "…");
            } else {
                log.info("Gemini API key is configured ({} characters, starts with {}).",
                        apiKey.length(), apiKey.substring(0, Math.min(7, apiKey.length())));
            }
        };
    }
}
