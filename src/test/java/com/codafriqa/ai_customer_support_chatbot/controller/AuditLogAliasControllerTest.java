package com.codafriqa.ai_customer_support_chatbot.controller;

import com.codafriqa.ai_customer_support_chatbot.config.BearerTokenStore;
import com.codafriqa.ai_customer_support_chatbot.dto.AuditLogDto;
import com.codafriqa.ai_customer_support_chatbot.service.AuditLogService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * GET /api/audit-logs must answer a bare JSON array — {@code []} with
 * HTTP 200 — when no audit logs exist, instead of 404/500.
 */
@WebMvcTest(controllers = AuditLogAliasController.class)
// Skip the slice's default security filters; the real chain (SecurityConfig)
// permits this path, and the controller contract is what is under test.
@AutoConfigureMockMvc(addFilters = false)
// The slice auto-registers BearerTokenFilter (any @Component Filter is part
// of the @WebMvcTest component set) — its in-memory token store has to be
// provided too, or the context fails to start with an unsatisfied dependency.
@Import(BearerTokenStore.class)
class AuditLogAliasControllerTest {

    /**
     * Service stub returning an empty page (i.e. an empty database).
     * A hand-written subclass rather than a Mockito mock: this JVM cannot
     * instrument concrete classes with the bundled inline mock maker.
     */
    @TestConfiguration
    static class EmptyAuditLogServiceConfig {
        @Bean
        AuditLogService auditLogService() {
            return new AuditLogService(null) {
                @Override
                public Page<AuditLogDto> getAllLogDtos(Pageable pageable) {
                    return Page.empty();
                }
            };
        }
    }

    @Autowired
    private MockMvc mvc;

    @Test
    void returnsEmptyJsonListWith200WhenNoLogsExist() throws Exception {
        mvc.perform(get("/api/audit-logs"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(content().string("[]"));
    }

    @Test
    void v1AliasAlsoReturnsEmptyJsonListWith200() throws Exception {
        mvc.perform(get("/api/v1/audit-logs"))
                .andExpect(status().isOk())
                .andExpect(content().string("[]"));
    }

    @Test
    void outOfRangePageStillReturns200WithEmptyList() throws Exception {
        mvc.perform(get("/api/audit-logs").param("page", "99").param("size", "20"))
                .andExpect(status().isOk())
                .andExpect(content().string("[]"));
    }
}
