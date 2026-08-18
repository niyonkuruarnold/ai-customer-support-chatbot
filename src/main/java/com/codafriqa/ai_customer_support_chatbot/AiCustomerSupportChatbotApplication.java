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
     * Startup check for the OpenAI configuration. Logs only whether the key is
     * present (never the key itself) so a missing OPENAI_API_KEY shows up in
     * the logs instead of surfacing later as a chat fallback message.
     *
     * Resolves the env var directly with a default ("${OPENAI_API_KEY:}"). It
     * must NOT resolve "spring.ai.openai.api-key" through Environment#getProperty:
     * that value is the literal placeholder "${OPENAI_API_KEY}" when the env var
     * is unset, and Boot's ConfigurationPropertySourcesPropertyResolver resolves
     * nested placeholders and would throw "Could not resolve placeholder" at
     * boot instead of reporting the key is missing.
     */
    @Bean
    public CommandLineRunner logOpenAiConfiguration(
            @Value("${OPENAI_API_KEY:}") String apiKey) {
        return args -> {
            if (apiKey == null || apiKey.isBlank()) {
                log.warn("OpenAI API key is NOT configured: OPENAI_API_KEY is missing or empty "
                        + "(set it in your terminal or in a local .env file, see .env.example). "
                        + "AI chat responses and knowledge base embeddings will use degraded fallbacks.");
            } else {
                log.info("OpenAI API key is configured ({} characters).", apiKey.length());
            }
        };
    }
}
