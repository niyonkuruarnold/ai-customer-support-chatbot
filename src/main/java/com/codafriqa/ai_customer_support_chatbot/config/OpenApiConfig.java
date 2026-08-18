package com.codafriqa.ai_customer_support_chatbot.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Springdoc OpenAPI configuration.
 *
 * <p>Defines API metadata displayed in Swagger UI ({@code /swagger-ui.html})
 * and the machine-readable spec at {@code /v3/api-docs}. Also registers
 * HTTP Basic authentication so the protected agent/admin endpoints can be
 * tested directly from Swagger UI.
 */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI customOpenAPI() {
        final String securitySchemeName = "basicAuth";

        return new OpenAPI()
                .info(new Info()
                        .title("AI Customer Support Chatbot API")
                        .version("1.0")
                        .description("REST APIs for AI RAG Chatbot, Ticket Management, and Agent Handoff")
                        .contact(new Contact()
                                .name("CODAFRIQA Development Team")
                                .email("dev@codafriqa.local")))
                .addSecurityItem(new SecurityRequirement().addList(securitySchemeName))
                .components(new Components()
                        .addSecuritySchemes(securitySchemeName,
                                new SecurityScheme()
                                        .name(securitySchemeName)
                                        .type(SecurityScheme.Type.HTTP)
                                        .scheme("basic")
                                        .description("HTTP Basic authentication for agent and admin endpoints (default: admin / admin123)")));
    }
}
