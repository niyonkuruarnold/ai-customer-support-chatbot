package com.codafriqa.ai_customer_support_chatbot.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Guards the security contract the frontend depends on:
 *
 * <ul>
 *   <li>HTTP Basic is explicitly disabled — credentials in an
 *       {@code Authorization: Basic ...} header must NOT authenticate anyone,
 *       and must never produce a {@code WWW-Authenticate} challenge (that
 *       header is what makes browsers pop up the native "Sign in to access
 *       this site" dialog during role switches).</li>
 *   <li>Unauthorized requests answer with the standard JSON payload
 *       {@code {"status":401,"error":"Unauthorized"}}.</li>
 *   <li>Bearer tokens issued by {@link BearerTokenStore} still authenticate,
 *       so the Vue app's Authorization header keeps working.</li>
 * </ul>
 *
 * The slice auto-registers {@link BearerTokenFilter} (any {@code @Component}
 * {@code Filter} is part of the {@code @WebMvcTest} component set), so its
 * token store is imported as well; the real {@link SecurityConfig} chain is
 * imported because that chain is exactly what is under test.
 */
@WebMvcTest(controllers = SecurityConfigTest.ProtectedEndpointController.class)
@AutoConfigureMockMvc // filters stay ON: the security chain is the subject under test
@Import({ SecurityConfig.class, BearerTokenStore.class, SecurityConfigTest.ProtectedEndpointController.class })
class SecurityConfigTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private BearerTokenStore tokenStore;

    @Test
    void anonymousRequestGetsJson401WithoutWwwAuthenticateHeader() throws Exception {
        mockMvc.perform(get("/api/users/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().doesNotExist("WWW-Authenticate"))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.error").value("Unauthorized"));
    }

    @Test
    void basicCredentialsNeverAuthenticateAndNeverTriggerABrowserChallenge() throws Exception {
        // With HTTP Basic re-enabled, valid default credentials (admin/admin123)
        // would answer 200 here — so 401 proves .httpBasic(...disable()) holds.
        String credentials = Base64.getEncoder()
                .encodeToString("admin:admin123".getBytes(StandardCharsets.UTF_8));

        mockMvc.perform(get("/api/users/me")
                        .header(HttpHeaders.AUTHORIZATION, "Basic " + credentials))
                .andExpect(status().isUnauthorized())
                .andExpect(header().doesNotExist("WWW-Authenticate"))
                .andExpect(jsonPath("$.error").value("Unauthorized"));
    }

    @Test
    void bearerTokenStillAuthenticatesTheRequest() throws Exception {
        var issued = tokenStore.issue(new UsernamePasswordAuthenticationToken(
                "admin@codafriqa.local",
                "n/a",
                List.of(new SimpleGrantedAuthority("ROLE_USER"))));

        mockMvc.perform(get("/api/users/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + issued.accessToken()))
                .andExpect(status().isOk())
                .andExpect(content().string("ok"));
    }

    @Test
    void permitAllPathsAreNotChallenged() throws Exception {
        // No controller is mapped here in this slice, so 404 (not 401) means
        // the security chain let the anonymous request through.
        mockMvc.perform(get("/api/auth/token"))
                .andExpect(status().isNotFound())
                .andExpect(header().doesNotExist("WWW-Authenticate"));
    }

    /** Minimal stand-in for an authenticated endpoint restricted to logged-in users. */
    @RestController
    static class ProtectedEndpointController {

        @GetMapping("/api/users/me")
        String profile() {
            return "ok";
        }
    }
}
